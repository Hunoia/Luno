package hunoia.luno.ui.actionselect

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.action.model.ACTION_PANEL_TYPE_ID
import hunoia.luno.action.model.launchQualifiedName
import hunoia.luno.action.model.withLaunchMiniWindow
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.domain.action.ActionCatalog
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import hunoia.luno.core.AppContext
import hunoia.luno.core.JsonSerializer
import hunoia.luno.ui.actionselect.UiState
import hunoia.luno.ui.component.actionText
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.component.slideToRes

fun Context.selectedItemLabel(item: Any, subGestures: List<SubGesture>): String {
    return selectedItemLabel(item, subGestures, emptyList())
}

fun Context.selectedItemLabel(
    item: Any,
    subGestures: List<SubGesture>,
    actionLibraryEntries: List<NewActionLibraryEntry>,
    rules: List<AutomationRule> = emptyList(),
): String {
    return when (item) {
        is StoredAction -> actionTextWithSubGesture(item, subGestures, actionLibraryEntries, rules, emptyIfNone = false)
        is AppInfo -> item.label
        is LauncherInfo.ShortcutInfo -> item.label
        else -> ""
    }
}

fun Context.actionTextWithSubGesture(
    action: StoredAction,
    subGestures: List<SubGesture>,
    emptyIfNone: Boolean
): String = actionTextWithSubGesture(action, subGestures, emptyList(), emptyList(), emptyIfNone)

fun Context.actionTextWithSubGesture(
    action: StoredAction,
    subGestures: List<SubGesture>,
    actionLibraryEntries: List<NewActionLibraryEntry>,
    emptyIfNone: Boolean
): String = actionTextWithSubGesture(action, subGestures, actionLibraryEntries, emptyList(), emptyIfNone)

fun Context.actionTextWithSubGesture(
    action: StoredAction,
    subGestures: List<SubGesture>,
    actionLibraryEntries: List<NewActionLibraryEntry>,
    rules: List<AutomationRule>,
    emptyIfNone: Boolean
): String {
    if (action.typeId == "internal.libraryRef") {
        val id = (action.params["entryId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return actionLibraryEntries.firstOrNull { it.id == id }?.name
            ?: getString(R.string.action_library_missing)
    }
    ActionCatalog.runRuleIdOf(action)?.let { ruleId ->
        rules.firstOrNull { it.id == ruleId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (rules.isNotEmpty()) {
            return getString(R.string.action_rule_missing)
        }
    }
    if (action.typeId != "internal.subGesture") {
        return actionText(action, emptyIfNone = emptyIfNone)
    }
    val id = (action.params["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return subGestures.firstOrNull { it.id == id }?.name
        ?: getString(R.string.action_sub_gesture)
}

/** 解析 StoredAction 的展示分类：走定义表，未知落 SYSTEM。 */
internal fun actionCategory(action: StoredAction): ActionCategory {
    return ActionDefinitions.byTypeId(action.typeId)?.category ?: ActionCategory.SYSTEM
}

internal fun createTitle(actionSelect: ActionSelect): String {
    val context = AppContext.get()
    if (actionSelect.actionPanelId.isNotBlank()) {
        return if (actionSelect.actionPanelItemIndex >= 0) {
            context.getString(R.string.action_panel_item_long_press)
        } else {
            context.getString(R.string.action_panel_add_item)
        }
    }
    if (actionSelect.automationRuleId.isNotBlank()) {
        return context.getString(R.string.automation_pick_action)
    }
    when (actionSelect.triggerType) {
        GestureTriggerType.Tap -> return context.getString(R.string.tap_action)
        GestureTriggerType.DoubleTap -> return context.getString(R.string.double_tap_action)
        GestureTriggerType.LongPress -> return context.getString(R.string.long_press)
        else -> Unit
    }
    val str1 = context.getString(actionSelect.direction.slideToRes)
    val str2 = when (actionSelect.triggerType) {
        GestureTriggerType.Slide -> context.getString(R.string.short1)
        GestureTriggerType.SlideHold -> context.getString(R.string.slide_hold_action)
        GestureTriggerType.LongSlide -> context.getString(R.string.long1)
        GestureTriggerType.LongSlideHold -> context.getString(R.string.long_slide_hold_action)
    }
    return "$str1($str2)"
}

internal fun Any.toStoredAction(): StoredAction {
    return when (this) {
        is StoredAction -> this
        is NewActionLibraryEntry -> StoredAction.of("internal.libraryRef", "entryId" to id)
        is AppInfo -> StoredAction.of(
            "app.launch",
            "packageName" to packageName,
            "className" to className,
            "miniWindow" to miniWindow.toString(),
        )
        is LauncherInfo.ShortcutInfo -> StoredAction.of(
            "app.launchShortcut",
            "data" to JsonSerializer.encodeToString(this),
        )
        else -> error("Unsupported selected action type: ${this::class.java.name}")
    }
}

/** 定义表 -> 可勾选的 StoredAction（内联新动作，不再经 legacy id）。 */
internal fun ActionDefinition.toStoredAction(): StoredAction =
    StoredAction(typeId)

internal fun assembleDataTransform(state: UiState): UiState {
    val allActions = ActionCatalog.pickerDefinitions(state.excludedTypeIds)
        .map { def -> def.toStoredAction() }
        .toMutableList()
        .apply {
            // 面板项模式：挑的是「面板里放什么」，不放置空入口，也不列面板本身
            // （面板不能嵌套面板，且在这里列自己毫无意义）。
            if (state.isPanelItemMode) return@apply
            // 「无动作」置空入口。internal.none 在 libraryExcluded 内被 pickerDefinitions
            // 滤掉了（动作库里不该能建「无」），这里**额外合成**一行而不是改定义表 ——
            // 碰 pickerExcluded 会踩「传空集合让整张定义表变可选行」那条既有约定。
            add(StoredAction.of("internal.none"))
            // 动作面板：一行一个具名面板。用户点哪行就把哪个 panelId 存进槽里。
            state.actionPanels.forEach { panel ->
                add(StoredAction.of(ACTION_PANEL_TYPE_ID, ACTION_PANEL_PARAM_PANEL_ID to panel.id))
            }
            // 自动化规则里绑子手势是无效的:`SubGestureState.tryEnterSubGesture` 需要一个
            // 真实的手势完成脉冲,自动化上下文里没有,命中后静默 return false。
            if (state.isAutomation) return@apply
            state.subGestures
                .filter { gesture -> gesture.enabled && gesture.id != state.excludedSubGestureId }
                .forEach { gesture ->
                    add(StoredAction.of("internal.subGesture", "id" to gesture.id))
                }
        }
    val list1 = mutableListOf<StoredAction>()
    val list2 = mutableListOf<StoredAction>()
    allActions.forEach { action ->
        if (state.selectedRecord.isSelected(action)) list1.add(action) else list2.add(action)
    }
    val finalList = list1 + list2
    return state.copy(actions = finalList)
}

internal fun selectActionTransform(state: UiState, action: StoredAction): UiState =
    state.copy(selectedRecord = state.selectedRecord.selectAction(action))

internal fun selectAppInfoTransform(state: UiState, appInfo: AppInfo): UiState =
    state.copy(selectedRecord = state.selectedRecord.selectAppInfo(appInfo))

internal fun selectShortcutInfoTransform(state: UiState, shortcut: LauncherInfo.ShortcutInfo): UiState =
    state.copy(selectedRecord = state.selectedRecord.selectShortcutInfo(shortcut))

internal fun toggleMiniWindowTransform(state: UiState, appInfo: AppInfo, switchToMiniWindow: Boolean): UiState {
    val apps = state.apps.map { app ->
        if (app.qualifiedName == appInfo.qualifiedName) {
            app.copy(miniWindow = switchToMiniWindow)
        } else app
    }
    val current = state.selectedRecord.action
    val record = if (current?.launchQualifiedName() == appInfo.qualifiedName) {
        state.selectedRecord.selectOne(current.withLaunchMiniWindow(switchToMiniWindow))
    } else {
        state.selectedRecord
    }
    return state.copy(apps = apps, selectedRecord = record)
}
