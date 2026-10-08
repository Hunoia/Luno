package hunoia.luno.ui.actionselect

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.withLongPressVariant
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.navigation.ActionSelect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ActionSelectVM(
    private val actionSelect: ActionSelect
) : BaseComposeVM<UiState, UiEvent>() {

    override val initialState: UiState = UiState(
        title = createTitle(actionSelect)
    )

    init {
        loadData()
        observeSubGestures()
    }

    fun loadData() {
        viewModelScope.launch {
            loadDataBody(
                actionSelect = actionSelect,
                onUpdateState = { transform -> updateUiState(transform) }
            )
            assembleData()
        }
    }

    /**
     * `loadDataBody` 里的子手势是一次性快照(`getSubGestureSettings()` 走 `.data.first()`),
     * 所以选择页开着的时候在主页改子手势的启用状态或名字,这页不会更新。这里补一条响应式的。
     *
     * 只关心选择页真正用到的部分(启用状态 + id + 名字),并且只在这部分真的变了时才重算,
     * 避免子手势其它字段的改动导致整份动作列表重建。
     */
    private fun observeSubGestures() {
        if (actionSelect.automationRuleId.isNotBlank()) return
        viewModelScope.launch {
            GestureConfig.subGestures.collectLatest { settings ->
                val excludedId = uiState.excludedSubGestureId
                val visible = settings.subGestures
                    .filter { it.enabled && it.id != excludedId }
                    .map { it.id to it.name }
                val current = uiState.subGestures
                    .filter { it.enabled && it.id != excludedId }
                    .map { it.id to it.name }
                if (visible == current) return@collectLatest
                updateUiState { state -> state.copy(subGestures = settings.subGestures) }
                assembleData()
            }
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
        updateUiState { it.copy(selectionDirty = true) }
        if (uiState.longPressTargetIndex != null && selected) {
            selectLongPressAction(obj)
            return
        }
        if (obj is AppInfo) {
            updateUiState { selectAppInfoTransform(it, obj, selected) }
        } else if (obj is LauncherInfo.ShortcutInfo) {
            updateUiState { selectShortcutInfoTransform(it, obj, selected) }
        } else if (obj is StoredAction) {
            updateUiState { selectActionTransform(it, obj, selected) }
        } else if (obj is NewActionLibraryEntry) {
            val action = obj.toStoredAction()
            updateUiState { selectActionTransform(it, action, selected) }
        }
        // 自动化每步只用首个动作（见 saveAutomationEffect），点选即暂存返回，
        // 省掉 bottom bar 的完成一步；取消选中不触发。手势路径保持多选+完成不变。
        if (actionSelect.automationRuleId.isNotBlank() && selected) {
            saveSettings()
        }
    }

    fun startSetLongPressAction(index: Int) {
        if (index !in uiState.selectedRecord.list.indices) return
        updateUiState { it.copy(longPressTargetIndex = index) }
    }

    fun cancelSetLongPressAction() {
        updateUiState { it.copy(longPressTargetIndex = null) }
    }

    fun selectLongPressAction(obj: Any) {
        val index = uiState.longPressTargetIndex ?: return
        val longPressAction = obj.toStoredAction()
        updateUiState { updateSelectedActionTransform(it, index) { it.withLongPressVariant(longPressAction) } }
        updateUiState { it.copy(longPressTargetIndex = null) }
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

    private fun assembleData() {
        updateUiState { assembleDataTransform(it) }
    }

    private fun saveSettings() {
        // 自动化只暂存（编辑页保存键统一落盘），这里不提示已保存，避免误导；
        // 手势/子手势路径保持直写，提示不变。
        val isAutomation = actionSelect.automationRuleId.isNotBlank()
        viewModelScope.launch {
            saveSettingsAction(
                actionSelect = actionSelect,
                getUiState = { uiState },
                updateUiState = { transform -> updateUiState(transform) }
            )
        }.invokeOnCompletion {
            if (it == null) {
                if (!isAutomation) toast(R.string.save_success)
                finish()
            } else {
                toast(R.string.save_failure)
            }
        }
    }
}
