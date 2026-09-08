package hunoia.luno.ui.actionselect

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.api.appInfo
import hunoia.luno.action.api.shortcutInfo
import hunoia.luno.action.payload.SubGestureActionData
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.actionLibraryRefId
import hunoia.luno.core.AppContext
import hunoia.luno.core.JsonSerializer
import hunoia.luno.ui.actionselect.UiState
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
import hunoia.luno.ui.component.actionText
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName

fun Context.selectedItemLabel(item: Any, subGestures: List<SubGesture>): String {
    return selectedItemLabel(item, subGestures, emptyList())
}

fun Context.selectedItemLabel(
    item: Any,
    subGestures: List<SubGesture>,
    actionLibraryEntries: List<ActionLibraryEntry>
): String {
    return when (item) {
        is Action -> actionTextWithSubGesture(item, subGestures, actionLibraryEntries, emptyIfNone = false)
        is AppInfo -> item.label
        is LauncherInfo.ShortcutInfo -> item.label
        else -> ""
    }
}

fun Context.actionTextWithSubGesture(
    action: Action,
    subGestures: List<SubGesture>,
    emptyIfNone: Boolean
): String = actionTextWithSubGesture(action, subGestures, emptyList(), emptyIfNone)

fun Context.actionTextWithSubGesture(
    action: Action,
    subGestures: List<SubGesture>,
    actionLibraryEntries: List<ActionLibraryEntry>,
    emptyIfNone: Boolean
): String {
    action.actionLibraryRefId()?.let { id ->
        return actionLibraryEntries.firstOrNull { it.id == id }?.name ?: getString(R.string.action_library_missing)
    }
    if (action.value != ActionFacade.SUB_GESTURE) {
        return actionText(action, emptyIfNone)
    }
    val data = runCatching {
        JsonSerializer.decodeFromString<SubGestureActionData>(action.data)
    }.getOrNull() ?: return getString(R.string.action_sub_gesture)
    return subGestures.firstOrNull { it.id == data.id }?.name ?: getString(R.string.action_sub_gesture)
}

internal const val MAX_SELECT_COUNT = 5
internal const val LONG_SLIDE_SOFT_MAX_SELECT_COUNT = 50

internal fun canActionEnabled(
    selectedRecord: SelectedRecord,
    item: Action,
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
    when (actionSelect.triggerType) {
        GestureTriggerType.Tap -> return context.getString(R.string.tap_action)
        GestureTriggerType.DoubleTap -> return context.getString(R.string.double_tap_action)
        GestureTriggerType.LongPress -> return context.getString(R.string.long_press)
        else -> Unit
    }
    val str1 = when (actionSelect.direction) {
        GestureDirection.Left -> context.getString(R.string.slide_to_left)
        GestureDirection.UpLeft -> context.getString(R.string.slide_to_top_left)
        GestureDirection.Up -> context.getString(R.string.slide_to_top)
        GestureDirection.UpRight -> context.getString(R.string.slide_to_top_right)
        GestureDirection.Right -> context.getString(R.string.slide_to_right)
        GestureDirection.DownRight -> context.getString(R.string.slide_to_bottom_right)
        GestureDirection.Down -> context.getString(R.string.slide_to_bottom)
        GestureDirection.DownLeft -> context.getString(R.string.slide_to_bottom_left)
    }
    val str2 = when (actionSelect.triggerType) {
        GestureTriggerType.Slide -> context.getString(R.string.short1)
        GestureTriggerType.SlideHold -> context.getString(R.string.slide_hold_action)
        GestureTriggerType.LongSlide -> context.getString(R.string.long1)
        GestureTriggerType.LongSlideHold -> context.getString(R.string.long_slide_hold_action)
        else -> ""
    }
    return "$str1($str2)"
}

internal fun Any.toAction(): Action {
    return when (this) {
        is Action -> this.copy(extra = null)
        is ActionLibraryEntry -> this.toReferenceAction()
        is AppInfo -> Action(
            value = ActionFacade.EXTRA_LAUNCH_APP,
            data = JsonSerializer.encodeToString(this)
        )
        is LauncherInfo.ShortcutInfo -> Action(
            value = ActionFacade.EXTRA_LAUNCH_SHORTCUT,
            data = JsonSerializer.encodeToString(this)
        )
        else -> error("Unsupported selected action type: ${this::class.java.name}")
    }
}

internal fun ActionLibraryEntry.toReferenceAction(): Action {
    val value = when (type) {
        ActionLibraryType.Shell -> ActionFacade.EXECUTE_SHELL_COMMAND
        ActionLibraryType.Url -> ActionFacade.OPEN_URL
        ActionLibraryType.Activity -> ActionFacade.OPEN_APP_ACTIVITY
    }
    return Action(value = value, data = JsonSerializer.encodeToString(ActionLibraryRefData(id)))
}

internal fun Action.sameAction(other: Action): Boolean {
    return value == other.value && data == other.data
}

internal fun assembleDataTransform(state: UiState): UiState {
    val allActions = ActionFacade.definitions
        .filter { def -> def.isDisplayed }
        .filterNot { def ->
            def.actionId == ActionFacade.OPEN_APP_ACTIVITY ||
                def.actionId == ActionFacade.OPEN_URL ||
                def.actionId == ActionFacade.EXECUTE_SHELL_COMMAND
        }
        .map { def -> def.toAction() }
        .toMutableList()
        .apply {
            state.subGestures
                .filter { gesture -> gesture.enabled && gesture.id != state.excludedSubGestureId }
                .forEach { gesture ->
                    add(
                        Action(
                            value = ActionFacade.SUB_GESTURE,
                            data = JsonSerializer.encodeToString(SubGestureActionData(id = gesture.id))
                        )
                    )
                }
        }
    val allWithoutNone = allActions.apply { removeAt(0) }
    val list1 = mutableListOf<Action>()
    val list2 = mutableListOf<Action>()
    allWithoutNone.forEach { action ->
        if (state.selectedRecord.isSelected(action) || action == Action.NONE) {
            list1.add(action)
        } else {
            list2.add(action)
        }
    }
    val finalList = list1 + list2
    return state.copy(actions = finalList)
}

internal fun selectActionTransform(state: UiState, action: Action, selected: Boolean): UiState {
    val record = state.selectedRecord.selectAction(action, selected)
    return state.copy(selectedRecord = record)
}

internal fun selectAppInfoTransform(state: UiState, appInfo: AppInfo, selected: Boolean): UiState {
    val record = state.selectedRecord.selectAppInfo(appInfo, selected)
    return state.copy(selectedRecord = record)
}

internal fun selectShortcutInfoTransform(state: UiState, shortcut: LauncherInfo.ShortcutInfo, selected: Boolean): UiState {
    val record = state.selectedRecord.selectShortcutInfo(shortcut, selected)
    return state.copy(selectedRecord = record)
}

internal fun updateSelectedActionTransform(state: UiState, index: Int, transform: (Action) -> Action): UiState {
    val list = state.selectedRecord.list.toMutableList()
    val current = list.getOrNull(index) as? Action ?: return state
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
            list = state.selectedRecord.list.filterIsInstance<Action>().toMutableList().let { list ->
                val index = list.indexOfFirst { action ->
                    action.appInfo?.qualifiedName == appInfo.qualifiedName
                }
                if (index != -1) {
                    val current = list[index]
                    val currentAppInfo = current.appInfo ?: appInfo
                    val newAppInfo = currentAppInfo.copy(miniWindow = switchToMiniWindow)
                    list[index] = current.copy(data = JsonSerializer.encodeToString(newAppInfo))
                }
                list
            }
        )
    )
}

internal fun updateActionDataTransform(state: UiState, action: Action, data: String): UiState {
    val newList = state.selectedRecord.list.toMutableList()
    val index = newList.indexOfFirst { obj -> obj is Action && obj.sameAction(action) }
    if (index == -1) return state
    val current = newList[index] as Action
    if (current.data == data) return state
    newList[index] = current.copy(data = data)
    return state.copy(selectedRecord = state.selectedRecord.copy(list = newList))
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
