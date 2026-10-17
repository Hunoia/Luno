package hunoia.luno.ui.actionselect

import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.hasPointParams
import hunoia.luno.action.model.launchAppRef
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.action.model.withLongPressVariant
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
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

internal fun StoredAction.shortcutIconPaths(): List<String> =
    listOfNotNull(
        shortcutInfo()?.iconPath,
        longPressVariant()?.shortcutInfo()?.iconPath
    ).filter { it.isNotEmpty() }

internal fun tryDeleteShortcutIcons(old: StoredAction?, new: StoredAction?) {
    val newPaths = listOfNotNull(new).shortcutIconPaths().toSet()
    listOfNotNull(old).shortcutIconPaths().forEach { path ->
        if (path in newPaths) return@forEach
        File(path).delete()
    }
}

internal suspend fun saveSettingsAction(
    actionSelect: ActionSelect,
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
) {
    // 单值语义：选择页给的就是最终结果，没有「占位 vs 有效」之分 ——
    // 选「无动作」传 null（= 删键/存 null），选别的传那一个。
    val picked = getUiState().selectedRecord.action
    if (actionSelect.actionPanelId.isNotBlank()) {
        saveActionPanelItem(actionSelect, picked)
        return
    }
    if (actionSelect.automationRuleId.isNotBlank()) {
        saveAutomationEffect(actionSelect, picked)
        return
    }
    if (actionSelect.subGestureId.isNotBlank()) {
        GestureConfig.updateSubGestures { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { gesture ->
                    if (gesture.id != actionSelect.subGestureId) return@map gesture
                    val oldAction = gesture.actionFor(actionSelect.triggerType, actionSelect.direction)
                    tryDeleteShortcutIcons(oldAction, picked)
                    gesture.withAction(actionSelect.triggerType, actionSelect.direction, picked)
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
        val oldAction = button.actionFor(actionSelect.triggerType, actionSelect.direction)
        tryDeleteShortcutIcons(oldAction, picked)
        button = button.withAction(actionSelect.triggerType, actionSelect.direction, picked)
        mutableList.apply {
            set(index, button)
        }
    }
}

/**
 * 面板项落盘：`actionPanelItemIndex < 0` 是新增一项（追加），>=0 是替换该项的长按变体。
 *
 * 走的是选择页而不是独立的参数编辑器 —— 面板项本来就是一整个动作，
 * 复用 `ActionSelect` 的分类/搜索/应用/快捷方式全部分组最省事。
 */
private suspend fun saveActionPanelItem(actionSelect: ActionSelect, picked: StoredAction?) {
    val panelId = actionSelect.actionPanelId
    val index = actionSelect.actionPanelItemIndex
    TuneConfig.updateAction { settings ->
        val panel = settings.actionPanels.panelById(panelId) ?: return@updateAction settings
        val items = when {
            // 新增：选了「无动作」等于放弃本次添加，不改面板。
            picked == null || picked.typeId == "internal.none" -> return@updateAction settings
            // 改长按变体：变体本身不带变体（防止套娃）。
            index >= 0 -> panel.items.mapIndexed { i, item ->
                if (i == index) item.withLongPressVariant(picked.withoutVariantKey()) else item
            }
            else -> panel.items + picked
        }
        settings.copy(
            actionPanels = settings.actionPanels.withPanel(panel.copy(items = items))
        )
    }
}

private suspend fun saveAutomationEffect(actionSelect: ActionSelect, picked: StoredAction?) {
    val current = loadAutomationAction(actionSelect)
    tryDeleteShortcutIcons(current, picked)
    // 动作库条目存的就是引用本身（internal.libraryRef），运行时由分发器就地解析成条目动作。
    // 以前引用另存 RuleStep.entryId、action 只放"真动作"，字段删掉后引用即步骤的 action。
    val action = picked?.withoutVariantKey()
    val path = actionSelect.automationStepPath
    fun applyTo(rule: AutomationRule): AutomationRule {
        if (rule.effect.stepAt(path) == null) {
            android.util.Log.w(
                "ActionSelect",
                "automation step path missing, rule=${rule.id} path=$path",
            )
        }
        return rule.copy(effect = rule.effect.withStepAt(path) { step ->
            step.copy(kind = hunoia.luno.config.model.RuleStepKind.ACTION, action = action)
        })
    }
    // 草稿优先：编辑页跳转时只暂存内存，未保存前不写库。
    // 无暂存时以库中规则为底新建暂存——选择页永不直写库，落盘统一由编辑页保存键负责。
    val base = AutomationDraftHolder.take(actionSelect.automationRuleId)
        ?: AutomationCenter.getRules().find { it.id == actionSelect.automationRuleId }
        ?: return
    AutomationDraftHolder.stage(applyTo(base))
}

private suspend fun loadAutomationAction(actionSelect: ActionSelect): StoredAction? {
    val rules = AutomationCenter.getRules()
    val rule = AutomationDraftHolder.take(actionSelect.automationRuleId)
        ?: rules.find { it.id == actionSelect.automationRuleId } ?: return null
    return rule.effect.stepAt(actionSelect.automationStepPath)?.action
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
        getUiState().selectedRecord.let { record ->
            // 单值：选中项若是已卸载的快捷方式，整个槽清空。
            val selected = record.action?.shortcutInfo()
            if (selected == null) {
                record
            } else {
                val stillThere = createLauncherInfos.any { it.qualifiedName == selected.qualifiedName } ||
                        launchLauncherInfos.any { launcher ->
                            launcher.shortcuts.any { it.qualifiedNameWithIntents == selected.qualifiedNameWithIntents }
                        }
                if (stillThere) record else UiState.SelectedRecord()
            }
        }
    }
    val finalCreateList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfo = selectedRecord.action?.shortcutInfo()
        createLauncherInfos.forEach { launcherInfo ->
            val isSelected = selectedShortcutInfo?.packageName == launcherInfo.packageName
            if (isSelected) list1.add(launcherInfo) else list2.add(launcherInfo)
        }
        list1 + list2
    }
    val finalLaunchList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfos = selectedRecord.action?.shortcutInfo()
        launchLauncherInfos.forEach { launcherInfo ->
            val isSelected = selectedShortcutInfos?.packageName == launcherInfo.packageName
            if (isSelected) list1.add(launcherInfo) else list2.add(launcherInfo)
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
        .action
        ?.shortcutInfo()
        ?.let { shortcut ->
            createLauncherInfos.find { it.qualifiedName == shortcut.qualifiedName }
                ?.let { addNewShortcut(it, shortcut) }
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
        getUiState().selectedRecord.let { record ->
            val selectedApp = record.action?.launchAppRef()
            if (selectedApp == null) {
                record
            } else if (mergedApps.any { it.qualifiedName == selectedApp.qualifiedName }) {
                record
            } else {
                UiState.SelectedRecord()   // 已卸载：槽清空
            }
        }
    }
    val finalList = withContext(Dispatchers.Default) {
        // 选中的应用置顶（沿用既有排序），单值下最多只有一个。
        val selectedRef = selectedRecord.action?.launchAppRef()
        val (selected, rest) = mergedApps.partition { appInfo ->
            selectedRef != null && selectedRef.qualifiedName == appInfo.qualifiedName
        }
        selected.map { it.copy(miniWindow = selectedRef?.miniWindow ?: it.miniWindow) } + rest
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
    // 全局动作面板列表：选择页一行一个具名面板。
    val actionPanels = TuneConfig.getAction().actionPanels.panels
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
            isAutomation = isAutomation,
            subGestures = subGestures,
            excludedSubGestureId = actionSelect.subGestureId,
            actionLibraryEntries = actionLibraryEntries.filter {
                // 自动化放行带固定坐标的 tap/longPress 条目（参数优先于触点，见执行器）；
                // 无坐标、或勾了「使用触发位置坐标」的条目，在无触点的自动化路径必
                // InvalidParameter，继续藏（hasPointParams 对两种都返回 false）。
                it.typeId !in libraryExcluded || it.storedAction.hasPointParams()
            },
            excludedTypeIds = pickerExcluded,
            rules = rules,
            actionPanels = actionPanels,
            isPanelItemMode = actionSelect.actionPanelId.isNotBlank(),
        )
    }
    val action = when {
        // 面板项模式：读回当前面板项（改变体时预选原变体），不碰触钮槽。
        actionSelect.actionPanelId.isNotBlank() -> {
            val index = actionSelect.actionPanelItemIndex
            val panel = actionPanels.firstOrNull { it.id == actionSelect.actionPanelId }
            val item = if (index >= 0) panel?.items?.getOrNull(index) else null
            item?.longPressVariant() ?: item
        }
        isAutomation -> loadAutomationAction(actionSelect)
        subGesture != null -> subGesture.actionFor(actionSelect.triggerType, actionSelect.direction)
        button != null -> button.actionFor(actionSelect.triggerType, actionSelect.direction)
        else -> null
    }
    onUpdateState { state ->
        state.copy(selectedRecord = state.selectedRecord.selectOne(action))
    }
}

private fun GestureButton.actionFor(triggerType: GestureTriggerType, direction: hunoia.luno.config.model.GestureDirection): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Tap -> tapActions
        GestureTriggerType.DoubleTap -> doubleTapActions
        GestureTriggerType.LongPress -> longPressActions
        GestureTriggerType.Slide -> slideActions.actionBy(direction)
        GestureTriggerType.SlideHold -> slideHoldActions.actionBy(direction)
        GestureTriggerType.LongSlide -> longSlideActions.actionBy(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActions.actionBy(direction)
    }
}

private fun GestureButton.withAction(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    action: StoredAction?
): GestureButton {
    return when (triggerType) {
        GestureTriggerType.Tap -> copy(tapActions = action)
        GestureTriggerType.DoubleTap -> copy(doubleTapActions = action)
        GestureTriggerType.LongPress -> copy(longPressActions = action)
        GestureTriggerType.Slide -> copy(slideActions = slideActions.withAction(direction, action))
        GestureTriggerType.SlideHold -> copy(slideHoldActions = slideHoldActions.withAction(direction, action))
        GestureTriggerType.LongSlide -> copy(longSlideActions = longSlideActions.withAction(direction, action))
        GestureTriggerType.LongSlideHold -> copy(longSlideHoldActions = longSlideHoldActions.withAction(direction, action))
    }
}

private fun hunoia.luno.config.model.SubGesture.actionFor(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection
): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Slide -> slideActionFor(direction)
        GestureTriggerType.SlideHold -> slideHoldActionFor(direction)
        GestureTriggerType.LongSlide -> longSlideActionFor(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActionFor(direction)
        else -> null
    }
}

private fun hunoia.luno.config.model.SubGesture.withAction(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    action: StoredAction?
): hunoia.luno.config.model.SubGesture {
    return when (triggerType) {
        GestureTriggerType.Slide -> withSlideAction(direction, action)
        GestureTriggerType.SlideHold -> withSlideHoldAction(direction, action)
        GestureTriggerType.LongSlide -> withLongSlideAction(direction, action)
        GestureTriggerType.LongSlideHold -> withLongSlideHoldAction(direction, action)
        else -> this
    }
}
