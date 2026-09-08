package hunoia.luno.ui.actionselect

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.navigation.ActionSelect
import kotlinx.coroutines.launch

class ActionSelectVM(
    private val actionSelect: ActionSelect
) : BaseComposeVM<UiState, UiEvent>() {

    override val initialState: UiState = UiState(
        title = createTitle(actionSelect),
        selectSingle = false
    )

    fun showDialog(show: Boolean, action: Action = Action.NONE) {
        updateUiState { it.copy(actionSettingsDialog = it.actionSettingsDialog.copy(show = show, action = action)) }
    }

    init {
        loadData()
    }

    fun reloadData() {
        viewModelScope.launch {
            loadDataBody(
                actionSelect = actionSelect,
                onUpdateState = { transform -> updateUiState(transform) }
            )
            assembleData()
        }
    }

    fun addNewShortcut(launcherInfo: LauncherInfo, shortcutInfo: LauncherInfo.ShortcutInfo) {
        updateUiState {
            val index = it.createShortcuts.indexOfFirst { info ->
                info.qualifiedName == launcherInfo.qualifiedName
            }
            if (index < 0) {
                return@updateUiState it
            }
            val newList = it.createShortcuts.toMutableList().apply {
                val cache = it.createShortcuts[index]
                set(index, cache.copy(shortcuts = cache.shortcuts + shortcutInfo))
            }
            it.copy(createShortcuts = newList)
        }
    }

    fun select(obj: Any, selected: Boolean) {
        if (uiState.longPressTargetIndex != null && selected) {
            selectLongPressAction(obj)
            return
        }
        if (obj is AppInfo) {
            updateUiState { selectAppInfoTransform(it, obj, selected) }
        } else if (obj is LauncherInfo.ShortcutInfo) {
            updateUiState { selectShortcutInfoTransform(it, obj, selected) }
        } else if (obj is Action) {
            updateUiState { selectActionTransform(it, obj, selected) }
        } else if (obj is ActionLibraryEntry) {
            val action = obj.toReferenceAction()
            updateUiState { selectActionTransform(it, action, selected) }
        }
    }

    fun startSetLongPressAction(index: Int) {
        if (index !in uiState.selectedRecord.list.indices) return
        updateUiState { it.copy(longPressTargetIndex = index) }
    }

    fun cancelSetLongPressAction() {
        updateUiState { it.copy(longPressTargetIndex = null) }
    }

    fun clearLongPressAction(index: Int) {
        updateUiState { updateSelectedActionTransform(it, index) { it.copy(longPressAction = null) } }
    }

    fun selectLongPressAction(obj: Any) {
        val index = uiState.longPressTargetIndex ?: return
        val longPressAction = obj.toAction()
        updateUiState { updateSelectedActionTransform(it, index) { it.copy(longPressAction = longPressAction) } }
        updateUiState { it.copy(longPressTargetIndex = null) }
    }

    fun updateActionData(action: Action, data: String) {
        val index = uiState.selectedRecord.list.indexOfFirst { obj -> obj is Action && obj.sameAction(action) }
        if (index == -1) return
        val current = uiState.selectedRecord.list[index] as Action
        if (current.data == data) return
        updateUiState { updateActionDataTransform(it, action, data) }
    }

    fun moveSelectedAction(fromIndex: Int, toIndex: Int) {
        updateUiState { moveSelectedActionTransform(it, fromIndex, toIndex) }
    }

    fun toggleMiniWindow(appInfo: AppInfo) {
        val switchToMiniWindow = !appInfo.miniWindow
        updateUiState { toggleMiniWindowTransform(it, appInfo, switchToMiniWindow) }
        if (switchToMiniWindow) {
            toast(R.string.enable_mini_window)
        } else {
            toast(R.string.disable_mini_window)
        }
    }

    fun done() {
        saveSettings()
    }

    fun updateShortcutInfos() {
        viewModelScope.launchWithLoading {
            updateShortcutInfosBody(
                getUiState = { uiState },
                updateUiState = { transform -> updateUiState(transform) },
                addNewShortcut = { launcherInfo, shortcutInfo -> addNewShortcut(launcherInfo, shortcutInfo) }
            )
        }
    }

    fun updateAppInfos() {
        viewModelScope.launchWithLoading {
            updateAppInfosBody(
                getUiState = { uiState },
                updateUiState = { transform -> updateUiState(transform) }
            )
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            loadDataBody(
                actionSelect = actionSelect,
                onUpdateState = { transform -> updateUiState(transform) }
            )
            assembleData()
        }
    }

    private fun assembleData() {
        updateUiState { assembleDataTransform(it) }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            saveSettingsAction(
                actionSelect = actionSelect,
                getUiState = { uiState },
                updateUiState = { transform -> updateUiState(transform) }
            )
        }.invokeOnCompletion {
            if (it == null) {
                toast(R.string.save_success)
                finish()
            } else {
                toast(R.string.save_failure)
            }
        }
    }
}
