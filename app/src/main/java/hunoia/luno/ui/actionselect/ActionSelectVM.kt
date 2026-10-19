package hunoia.luno.ui.actionselect

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.StoredAction
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

    /**
     * 点一行 = 选中 + 立即落盘 + 返回。
     *
     * 单选后没有「再点一下取消」这个中间态，也没有底栏的完成键可等，
     * 所以选择即提交。自动化路径原本就是这么做的，这里提为唯一路径。
     */
    fun select(obj: Any) {
        when (obj) {
            is AppInfo -> updateUiState { selectAppInfoTransform(it, obj) }
            is LauncherInfo.ShortcutInfo -> updateUiState { selectShortcutInfoTransform(it, obj) }
            is StoredAction -> updateUiState { selectActionTransform(it, obj) }

            is NewActionLibraryEntry -> updateUiState { selectActionTransform(it, obj.toStoredAction()) }
        }
        done()
    }

    /**
     * 小部件选定 → 落盘 → 收页。
     *
     * 绑定与配置在组合侧发起（要 Activity 回结果），落盘才回到这里。落盘层会
     * 自动降格找空位；页满时返回 false —— 若仍报「保存成功」就是真机上那个
     * 「添加没反应」的元凶（静默失败 + 假成功提示），现在分别提示并回收 id。
     */
    fun commitWidget(provider: String, appWidgetId: Int, spanX: Int, spanY: Int) {
        var placed = false
        viewModelScope.launch {
            placed = saveGridPanelWidget(
                actionSelect = actionSelect,
                provider = provider,
                appWidgetId = appWidgetId,
                spanX = spanX,
                spanY = spanY,
            )
        }.invokeOnCompletion { error ->
            when {
                error == null && placed -> toast(R.string.save_success)
                error == null -> toast(R.string.grid_panel_page_full)
                else -> toast(R.string.save_failure)
            }
            finish()
        }
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
        var saved = true
        viewModelScope.launch {
            saved = saveSettingsAction(
                actionSelect = actionSelect,
                getUiState = { uiState },
                updateUiState = { transform -> updateUiState(transform) },
            )
        }.invokeOnCompletion {
            when {
                it != null -> toast(R.string.save_failure)
                // 软失败（目前只有网格面板页满）：提示放不下，而不是假报保存成功。
                !saved -> toast(R.string.grid_panel_page_full)
                !isAutomation -> toast(R.string.save_success)
            }
            if (it == null) finish()
        }
    }
}
