package hunoia.luno.ui.actionselect

import hunoia.luno.action.api.appInfo
import hunoia.luno.action.api.shortcutInfo
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.VALUE_LIBRARY_REF
import hunoia.luno.config.model.actionLibraryRefId
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import hunoia.luno.ui.navigation.ActionSelect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun List<Action>.shortcutIconPaths(): List<String> {
    return flatMap { action ->
        listOfNotNull(
            action.shortcutInfo?.iconPath,
            action.longPressAction?.shortcutInfo?.iconPath
        )
    }.filter { it.isNotEmpty() }
}

internal fun tryDeleteShortcutIcons(old: List<Action>, new: List<Action>) {
    val newPaths = new.shortcutIconPaths().toSet()
    old.forEach { action ->
        listOfNotNull(action.shortcutInfo, action.longPressAction?.shortcutInfo).forEach { shortcutInfo ->
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
    val newActions = selectedRecord.list.filterIsInstance<Action>()
    if (actionSelect.automationRuleId.isNotBlank()) {
        saveAutomationEffect(actionSelect, newActions)
        return
    }
    if (actionSelect.subGestureId.isNotBlank()) {
        ConfigProvider.updateSubGestureSettings { settings ->
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
    val buttonsUpdater = ConfigProvider::updateGestureButtons
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

private suspend fun saveAutomationEffect(actionSelect: ActionSelect, newActions: List<Action>) {
    val entries = ConfigProvider.getNewActionLibrarySettings().entries
    val current = loadAutomationActions(actionSelect, entries) ?: emptyList()
    tryDeleteShortcutIcons(current, newActions)
    val selected = newActions.firstOrNull()
    ConfigProvider.updateAutomationRules { rules ->
        rules.map { rule ->
            if (rule.id != actionSelect.automationRuleId) return@map rule
            rule.copy(effect = rule.effect.copy(
                action = selected?.takeIf { it.value != VALUE_LIBRARY_REF },
                entryId = selected?.actionLibraryRefId() ?: "",
            ))
        }
    }
}

private suspend fun loadAutomationActions(
    actionSelect: ActionSelect,
    actionLibraryEntries: List<NewActionLibraryEntry>
): List<Action>? {
    val rules = ConfigProvider.getAutomationRules()
    val rule = rules.find { it.id == actionSelect.automationRuleId } ?: return null
    if (rule.effect.type != RuleEffectType.RUN_ACTION) return null
    rule.effect.action?.let { return listOf(it.copy(extra = null)) }
    if (rule.effect.entryId.isBlank()) return null
    val entry = actionLibraryEntries.find { it.id == rule.effect.entryId } ?: return null
    return listOf(entry.toReferenceAction())
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
                .mapNotNull { (it as? Action)?.shortcutInfo }
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
        val selectedShortcutInfos = selectedRecord.list.mapNotNull { (it as? Action)?.shortcutInfo }
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
        val selectedShortcutInfos = selectedRecord.list.mapNotNull { (it as? Action)?.shortcutInfo }
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
        .mapNotNull { (it as? Action)?.shortcutInfo }
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
                .mapNotNull { (it as? Action)?.appInfo }
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
        val selectedAppInfos = selectedRecord.list.mapNotNull { (it as? Action)?.appInfo }
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
    val buttons = ConfigProvider.getGestureButtons()
    val gestureSettings = ConfigProvider.getGestureSettings()
    val subGestures = ConfigProvider.getSubGestureSettings().subGestures
    val actionLibraryEntries = ConfigProvider.getNewActionLibrarySettings().entries
    val isAutomation = actionSelect.automationRuleId.isNotBlank()

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

private fun GestureButton.actionsFor(triggerType: GestureTriggerType, direction: hunoia.luno.config.model.GestureDirection): List<Action> {
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
    newActions: List<Action>
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
): List<Action> {
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
    newActions: List<Action>
): hunoia.luno.config.model.SubGesture {
    return when (triggerType) {
        GestureTriggerType.Slide -> withSlideActions(direction, newActions)
        GestureTriggerType.SlideHold -> withSlideHoldActions(direction, newActions)
        GestureTriggerType.LongSlide -> withLongSlideActions(direction, newActions)
        GestureTriggerType.LongSlideHold -> withLongSlideHoldActions(direction, newActions)
        else -> this
    }
}
