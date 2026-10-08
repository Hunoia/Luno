package hunoia.luno.ui.actionselect

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.model.StoredAction
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
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
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
    // 规则即动作：显示目标规则名；规则列表未加载时落定义名，目标没了落缺失。
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
internal const val MAX_SELECT_COUNT = 50

internal fun canActionEnabled(
    selectedRecord: SelectedRecord,
    item: StoredAction,
    maxSelectCount: Int
): Boolean {
    if (maxSelectCount <= 1) return true
    return !(selectedRecord.size >= maxSelectCount && !selectedRecord.isSelected(item))
}

internal fun canAppInfoEnabled(
    selectedRecord: SelectedRecord,
    item: AppInfo,
    maxSelectCount: Int
): Boolean {
    if (maxSelectCount <= 1) return true
    return !(selectedRecord.size >= maxSelectCount && !selectedRecord.isSelected(item))
}

internal fun canLauncherInfoEnabled(
    selectedRecord: SelectedRecord,
    item: LauncherInfo,
    maxSelectCount: Int
): Boolean {
    if (maxSelectCount <= 1) return true
    return !(selectedRecord.size >= maxSelectCount && !item.shortcuts.any { selectedRecord.isSelected(it) })
}

internal fun canShortcutInfoEnabled(
    selectedRecord: SelectedRecord,
    item: LauncherInfo.ShortcutInfo,
    maxSelectCount: Int
): Boolean {
    if (maxSelectCount <= 1) return true
    return !(selectedRecord.size >= maxSelectCount && !selectedRecord.isSelected(item))
}

internal fun createTitle(actionSelect: ActionSelect): String {
    val context = AppContext.get()
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

internal fun selectActionTransform(state: UiState, action: StoredAction, selected: Boolean): UiState {
    val record = state.selectedRecord.selectAction(action, selected, state.maxSelectCount)
    return state.copy(selectedRecord = record)
}

internal fun selectAppInfoTransform(state: UiState, appInfo: AppInfo, selected: Boolean): UiState {
    val record = state.selectedRecord.selectAppInfo(appInfo, selected, state.maxSelectCount)
    return state.copy(selectedRecord = record)
}

internal fun selectShortcutInfoTransform(state: UiState, shortcut: LauncherInfo.ShortcutInfo, selected: Boolean): UiState {
    val record = state.selectedRecord.selectShortcutInfo(shortcut, selected, state.maxSelectCount)
    return state.copy(selectedRecord = record)
}

internal fun updateSelectedActionTransform(state: UiState, index: Int, transform: (StoredAction) -> StoredAction): UiState {
    val list = state.selectedRecord.list.toMutableList()
    val current = list.getOrNull(index) as? StoredAction ?: return state
    list[index] = transform(current)
    return state.copy(selectedRecord = state.selectedRecord.copy(list = list))
}

internal fun toggleMiniWindowTransform(state: UiState, appInfo: AppInfo, switchToMiniWindow: Boolean): UiState {
    return state.copy(
        apps = state.apps.map { app ->
            if (app.qualifiedName == appInfo.qualifiedName) {
                app.copy(miniWindow = switchToMiniWindow)
            } else app
        },
        selectedRecord = state.selectedRecord.copy(
            list = state.selectedRecord.list.filterIsInstance<StoredAction>().toMutableList().let { list ->
                val index = list.indexOfFirst { action ->
                    action.launchQualifiedName() == appInfo.qualifiedName
                }
                if (index != -1) {
                    list[index] = list[index].withLaunchMiniWindow(switchToMiniWindow)
                }
                list
            }
        )
    )
}

internal fun moveSelectedActionTransform(state: UiState, fromIndex: Int, toIndex: Int): UiState {
    val list = state.selectedRecord.list.toMutableList()
    if (fromIndex !in list.indices || toIndex !in list.indices) return state
    if (fromIndex == toIndex || list[fromIndex] == list[toIndex]) return state
    val item = list.removeAt(fromIndex)
    val targetIndex = toIndex.coerceIn(0, list.size)
    list.add(targetIndex, item)
    return state.copy(selectedRecord = state.selectedRecord.copy(list = list))
}
