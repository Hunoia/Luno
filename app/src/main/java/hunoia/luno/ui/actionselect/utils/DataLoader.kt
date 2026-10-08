package hunoia.luno.ui.actionselect

import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.launchAppRef
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.core.AppContext
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import hunoia.luno.ui.automation.AutomationDraftHolder
import hunoia.luno.ui.navigation.ActionSelect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun StoredAction.shortcutInfo(): LauncherInfo.ShortcutInfo? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching { JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(data) }.getOrNull()
}

internal fun List<StoredAction>.shortcutIconPaths(): List<String> {
    return flatMap { action ->
        listOfNotNull(
            action.shortcutInfo()?.iconPath,
            action.longPressVariant()?.shortcutInfo()?.iconPath
        )
    }.filter { it.isNotEmpty() }
}

internal fun tryDeleteShortcutIcons(old: List<StoredAction>, new: List<StoredAction>) {
    val newPaths = new.shortcutIconPaths().toSet()
    old.forEach { action ->
        listOfNotNull(action.shortcutInfo(), action.longPressVariant()?.shortcutInfo()).forEach { shortcutInfo ->
            if (shortcutInfo.iconPath.isNullOrEmpty()) return@forEach
            if (shortcutInfo.iconPath in newPaths) return@forEach
            File(shortcutInfo.iconPath).delete()
        }
    }
}

internal suspend fun saveSettingsAction(
    actionSelect: ActionSelect,
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
) {
    val selectedRecord = getUiState().selectedRecord
    val picked = selectedRecord.list.filterIsInstance<StoredAction>()
    // 选中含有效动作时清掉空占位（旧 [none] + 新选不再多出一个空）；全空则保持空落盘。
    val newActions =
        if (picked.any { it.typeId != "internal.none" }) picked.filter { it.typeId != "internal.none" }
        else picked
    if (actionSelect.automationRuleId.isNotBlank()) {
        saveAutomationEffect(actionSelect, newActions)
        return
    }
    if (actionSelect.subGestureId.isNotBlank()) {
        GestureConfig.updateSubGestures { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { gesture ->
                    if (gesture.id != actionSelect.subGestureId) return@map gesture
                    val oldActions = gesture.actionsFor(actionSelect.triggerType, actionSelect.direction)
                    tryDeleteShortcutIcons(oldActions, newActions)
                    gesture.withActions(actionSelect.triggerType, actionSelect.direction, newActions)
                }
            )
        }
        return
    }
    val buttonsUpdater = GestureConfig::updateButtons
    buttonsUpdater { list ->
        val mutableList = list.toMutableList()
        var button: GestureButton? = null
        var index = -1
        for (i in mutableList.indices) {
            index = i
            val b = mutableList[i]
            if (b.id == actionSelect.gestureButtonId) {
                button = b
                break
            }
        }
        if (button == null) {
            return@buttonsUpdater mutableList
        }
        val oldActions = button.actionsFor(actionSelect.triggerType, actionSelect.direction)
        tryDeleteShortcutIcons(oldActions, newActions)
        button = button.withActions(actionSelect.triggerType, actionSelect.direction, newActions)
        mutableList.apply {
            set(index, button)
        }
    }
}

private suspend fun saveAutomationEffect(actionSelect: ActionSelect, newActions: List<StoredAction>) {
    val entries = ActionLibrary.getSettings().entries
    val current = loadAutomationActions(actionSelect, entries) ?: emptyList()
    tryDeleteShortcutIcons(current, newActions)
    val selected = newActions.firstOrNull()
    val action = selected?.takeIf { it.typeId != "internal.libraryRef" }?.withoutVariantKey()
    val entryId = selected
        ?.takeIf { it.typeId == "internal.libraryRef" }
        ?.let { (it.params["entryId"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
        .orEmpty()
    val path = actionSelect.automationStepPath
    fun applyTo(rule: AutomationRule): AutomationRule {
        if (path.isBlank()) {
            return rule.copy(effect = rule.effect.copy(action = action, entryId = entryId))
        }
        if (rule.effect.stepAt(path) == null) {
            android.util.Log.w(
                "ActionSelect",
                "automation step path missing, rule=${rule.id} path=$path",
            )
        }
        return rule.copy(effect = rule.effect.withStepAt(path) { step ->
            step.copy(kind = hunoia.luno.config.model.RuleStepKind.ACTION, action = action, entryId = entryId)
        })
    }
    // 草稿优先：编辑页跳转时只暂存内存，未保存前不写库。
    if (AutomationDraftHolder.transform(actionSelect.automationRuleId, ::applyTo)) return
    AutomationCenter.updateRules { rules ->
        rules.map { rule ->
            if (rule.id != actionSelect.automationRuleId) rule else applyTo(rule)
        }
    }
}

private suspend fun loadAutomationActions(
    actionSelect: ActionSelect,
    actionLibraryEntries: List<NewActionLibraryEntry>
): List<StoredAction>? {
    val rules = AutomationCenter.getRules()
    val rule = AutomationDraftHolder.take(actionSelect.automationRuleId)
        ?: rules.find { it.id == actionSelect.automationRuleId } ?: return null
    if (rule.effect.type != RuleEffectType.RUN_ACTION) return null
    val path = actionSelect.automationStepPath
    if (path.isNotBlank()) {
        val step = rule.effect.stepAt(path) ?: return null
        step.action?.let { return listOf(it) }
        if (step.entryId.isBlank()) return null
        val entry = actionLibraryEntries.find { it.id == step.entryId } ?: return null
        return listOf(StoredAction.of("internal.libraryRef", "entryId" to entry.id))
    }
    rule.effect.action?.let { return listOf(it) }
    if (rule.effect.entryId.isBlank()) return null
    val entry = actionLibraryEntries.find { it.id == rule.effect.entryId } ?: return null
    return listOf(StoredAction.of("internal.libraryRef", "entryId" to entry.id))
}

internal suspend fun updateShortcutInfosBody(
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit,
    addNewShortcut: (LauncherInfo, LauncherInfo.ShortcutInfo) -> Unit
) {
    val createLauncherInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryShortcutActivities(AppContext.get())
    }
    val launchLauncherInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryShortcuts(AppContext.get())
    }
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { selectedRecord ->
            val uninstalledList = mutableListOf<LauncherInfo.ShortcutInfo>()
            selectedRecord
                .list
                .mapNotNull { (it as? StoredAction)?.shortcutInfo() }
                .forEach { selected ->
                    val uninstalled = !createLauncherInfos.any { launcher ->
                        launcher.qualifiedName == selected.qualifiedName
                    } && !launchLauncherInfos.any { launcher ->
                        launcher.shortcuts.any { shortcut ->
                            shortcut.qualifiedNameWithIntents == selected.qualifiedNameWithIntents
                        }
                    }
                    if (uninstalled) {
                        uninstalledList.add(selected)
                    }
                }
            selectedRecord.removeAllShortcutInfos(uninstalledList)
        }
    }
    val finalCreateList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfos = selectedRecord.list.mapNotNull { (it as? StoredAction)?.shortcutInfo() }
        createLauncherInfos.forEach { launcherInfo ->
            val cache = selectedShortcutInfos.find { info ->
                info.packageName == launcherInfo.packageName
            }
            if (cache != null) {
                list1.add(launcherInfo)
            } else {
                list2.add(launcherInfo)
            }
        }
        list1 + list2
    }
    val finalLaunchList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfos = selectedRecord.list.mapNotNull { (it as? StoredAction)?.shortcutInfo() }
        launchLauncherInfos.forEach { launcherInfo ->
            val cache = selectedShortcutInfos.find { info ->
                info.packageName == launcherInfo.packageName
            }
            if (cache != null) {
                list1.add(launcherInfo)
            } else {
                list2.add(launcherInfo)
            }
        }
        list1 + list2
    }
    updateUiState {
        it.copy(
            createShortcuts = finalCreateList,
            launchShortcuts = finalLaunchList,
            selectedRecord = selectedRecord
        )
    }
    getUiState()
        .selectedRecord
        .list
        .mapNotNull { (it as? StoredAction)?.shortcutInfo() }
        .forEach { shortcut ->
            val launcherInfo = createLauncherInfos.find {
                it.qualifiedName == shortcut.qualifiedName
            }
            if (launcherInfo != null) {
                addNewShortcut(launcherInfo, shortcut)
            }
        }
}

internal suspend fun updateAppInfosBody(
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
) {
    val appInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryApps(AppContext.get())
    }
    val disabledApps = DisabledAppQuery.queryDisabledApplicationsOnIo(AppContext.get())
    val normalPackageNames = appInfos.map { it.packageName }.toSet()
    val filteredDisabledApps = disabledApps.filter { it.packageName !in normalPackageNames }
    val mergedApps = mutableListOf<AppInfo>()
    mergedApps.addAll(appInfos)
    mergedApps.addAll(filteredDisabledApps)
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { selectedRecord ->
            val uninstalledList = mutableListOf<AppInfo>()
            selectedRecord
                .list
                .mapNotNull { (it as? StoredAction)?.launchAppRef() }
                .forEach { selectedApp ->
                    val uninstalled = !mergedApps.any { app ->
                        selectedApp.qualifiedName == app.qualifiedName
                    }
                    if (uninstalled) {
                        uninstalledList.add(selectedApp)
                    }
                }
            selectedRecord.removeAllAppInfos(uninstalledList)
        }
    }
    val finalList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<AppInfo>()
        val list2 = mutableListOf<AppInfo>()
        val selectedAppInfos = selectedRecord.list.mapNotNull { (it as? StoredAction)?.launchAppRef() }
        mergedApps.forEach { appInfo ->
            val cache = selectedAppInfos.find { app ->
                app.qualifiedName == appInfo.qualifiedName
            }
            if (cache != null) {
                val appInfo2 = appInfo.copy(
                    miniWindow = cache.miniWindow
                )
                list1.add(appInfo2)
            } else {
                list2.add(appInfo)
            }
        }
        val result = mutableListOf<AppInfo>()
        result.addAll(list1)
        result.addAll(list2)
        result
    }
    updateUiState {
        it.copy(
            apps = finalList,
            selectedRecord = selectedRecord
        )
    }
}

internal suspend fun loadDataBody(
    actionSelect: ActionSelect,
    onUpdateState: (update: (UiState) -> UiState) -> Unit
) {
    val buttons = GestureConfig.getButtons()
    val gestureSettings = TuneConfig.getGesture()
    val subGestures = GestureConfig.getSubGestures().subGestures
    val actionLibraryEntries = ActionLibrary.getSettings().entries
    val isAutomation = actionSelect.automationRuleId.isNotBlank()
    // 规则即动作：选择页专属分组（只留启用；自动化模式剔除正在编辑的自身，禁自引用）。
    val rules = ActionCatalog.rulesForPicker(
        AutomationCenter.getRules(),
        excludeRuleId = if (isAutomation) actionSelect.automationRuleId else "",
    )

    // 两个排除集必须分开,它们管的不是同一件事:
    // - pickerExcluded 决定「有哪些动作类型可以被选」,必须恒非空。传空集合会让
    //   `typeId !in excluded` 恒真,整张定义表都变成可选行。
    // - libraryExcluded 只过滤动作库里已保存的条目。触钮路径保持不额外过滤,否则
    //   pickerExcluded 多排掉的 app.launch / accessibility.tap / accessibility.longPress
    //   是允许存进动作库的类型,会把用户已保存的条目从列表里藏掉。
    val pickerExcluded = if (isAutomation) {
        ActionDefinitions.automationExcludedTypeIds()
    } else {
        ActionDefinitions.pickerExcludedTypeIds()
    }
    val libraryExcluded = if (isAutomation) ActionDefinitions.automationExcludedTypeIds() else emptySet()
    val button = if (isAutomation) null else buttons.find { it.id == actionSelect.gestureButtonId }
    val subGesture = if (isAutomation) null else subGestures.find { it.id == actionSelect.subGestureId }
    onUpdateState { state ->
        state.copy(
            maxSelectCount = if (isAutomation) 1 else MAX_SELECT_COUNT,
            isAutomation = isAutomation,
            subGestures = subGestures,
            excludedSubGestureId = actionSelect.subGestureId,
            actionLibraryEntries = actionLibraryEntries.filter { it.typeId !in libraryExcluded },
            excludedTypeIds = pickerExcluded,
            rules = rules,
        )
    }
    val actions = when {
        isAutomation -> loadAutomationActions(actionSelect, actionLibraryEntries)
        subGesture != null -> subGesture.actionsFor(actionSelect.triggerType, actionSelect.direction)
        button != null -> button.actionsFor(actionSelect.triggerType, actionSelect.direction)
        else -> null
    }
    if (actions != null) {
        onUpdateState { state ->
            val newSelectedRecord = state.selectedRecord.selectAll(actions)
            state.copy(selectedRecord = newSelectedRecord)
        }
    }
}

private fun GestureButton.actionsFor(triggerType: GestureTriggerType, direction: hunoia.luno.config.model.GestureDirection): List<StoredAction> {
    return when (triggerType) {
        GestureTriggerType.Tap -> tapActions
        GestureTriggerType.DoubleTap -> doubleTapActions
        GestureTriggerType.LongPress -> longPressActions
        GestureTriggerType.Slide -> slideActions.actionsBy(direction)
        GestureTriggerType.SlideHold -> slideHoldActions.actionsBy(direction)
        GestureTriggerType.LongSlide -> longSlideActions.actionsBy(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActions.actionsBy(direction)
    }
}

private fun GestureButton.withActions(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    newActions: List<StoredAction>
): GestureButton {
    return when (triggerType) {
        GestureTriggerType.Tap -> copy(tapActions = newActions)
        GestureTriggerType.DoubleTap -> copy(doubleTapActions = newActions)
        GestureTriggerType.LongPress -> copy(longPressActions = newActions)
        GestureTriggerType.Slide -> copy(slideActions = slideActions.withActions(direction, newActions))
        GestureTriggerType.SlideHold -> copy(slideHoldActions = slideHoldActions.withActions(direction, newActions))
        GestureTriggerType.LongSlide -> copy(longSlideActions = longSlideActions.withActions(direction, newActions))
        GestureTriggerType.LongSlideHold -> copy(longSlideHoldActions = longSlideHoldActions.withActions(direction, newActions))
    }
}

private fun hunoia.luno.config.model.SubGesture.actionsFor(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection
): List<StoredAction> {
    return when (triggerType) {
        GestureTriggerType.Slide -> slideActionsFor(direction)
        GestureTriggerType.SlideHold -> slideHoldActionsFor(direction)
        GestureTriggerType.LongSlide -> longSlideActionsFor(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActionsFor(direction)
        else -> emptyList()
    }
}

private fun hunoia.luno.config.model.SubGesture.withActions(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    newActions: List<StoredAction>
): hunoia.luno.config.model.SubGesture {
    return when (triggerType) {
        GestureTriggerType.Slide -> withSlideActions(direction, newActions)
        GestureTriggerType.SlideHold -> withSlideHoldActions(direction, newActions)
        GestureTriggerType.LongSlide -> withLongSlideActions(direction, newActions)
        GestureTriggerType.LongSlideHold -> withLongSlideHoldActions(direction, newActions)
        else -> this
    }
}
