package hunoia.luno.appcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.appcontrol.R
import hunoia.luno.domain.appcontrol.AppControlCenter
import hunoia.luno.core.AppContext
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.launch.PackageControl
import hunoia.luno.quickaccess.query.AppAvailability
import hunoia.luno.quickaccess.query.AppCatalog
import hunoia.luno.quickaccess.query.DisabledAppQuery
import hunoia.luno.quickaccess.query.LauncherEnvironment
import hunoia.luno.quickaccess.query.SystemAppsVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 冻结管理页的一行（按包名去重；分身与主应用共享冻结状态）。 */
data class ManagedApp(
    val packageName: String,
    val label: String,
    val availability: AppAvailability,
    val inOneKey: Boolean,
    val protected: Boolean,
)

/** 页面唯一状态（architecture §8.4）。 */
data class AppControlUiState(
    val apps: List<ManagedApp> = emptyList(),
    val oneKeyCount: Int = 0,
    val frozenCount: Int = 0,
    val loading: Boolean = true,
    val query: String = "",
    val searchExpanded: Boolean = false,
    /** 系统应用两类开关（无界面组件 / 有界面无入口），页内内存态。 */
    val systemVisibility: SystemAppsVisibility = SystemAppsVisibility(),
    val selectionMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    val pendingFreezeCount: Int = 0,
    val failures: List<Pair<String, String>> = emptyList(),
    val busy: Boolean = false,
)

class AppControlVM : ViewModel() {

    private data class Loaded(
        val apps: List<ManagedApp> = emptyList(),
        val loading: Boolean = true,
    )

    private data class Local(
        val query: String = "",
        val searchExpanded: Boolean = false,
        val systemVisibility: SystemAppsVisibility = SystemAppsVisibility(),
        val selectionMode: Boolean = false,
        val selected: Set<String> = emptySet(),
        val pendingFreeze: List<String> = emptyList(),
        val failures: List<Pair<String, String>> = emptyList(),
        val busy: Boolean = false,
    )

    private val loaded = MutableStateFlow(Loaded())
    private val local = MutableStateFlow(Local())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val uiState = combine(
        loaded,
        local,
        AppControlCenter.settings,
    ) { load, local, settings ->
        val oneKey = settings.oneKeyFreezePackageNames
        val apps = load.apps.map { it.copy(inOneKey = it.packageName in oneKey) }
        AppControlUiState(
            apps = apps,
            oneKeyCount = apps.count { it.inOneKey },
            // 只数名单内的已冻结：标题「冻结 N/M」的 N 必须 ≤ 名单总数 M。
            frozenCount = apps.count { it.inOneKey && it.availability == AppAvailability.DISABLED },
            loading = load.loading,
            query = local.query,
            searchExpanded = local.searchExpanded,
            systemVisibility = local.systemVisibility,
            selectionMode = local.selectionMode,
            selected = local.selected,
            pendingFreezeCount = local.pendingFreeze.size,
            failures = local.failures,
            busy = local.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppControlUiState())

    init {
        viewModelScope.launch {
            // 进页清理已卸载包：只动配置，不依赖 Shizuku；失败不阻塞页面。
            runCatching { PackageControl.pruneUninstalledFromOneKey() }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // 就地更新，不闪 loading（对齐 ClipboardVM.refresh 语义）：runBatch / setSystemVisibility
            // 触发刷新时列表不切 LoadingState，LazyColumn 滚动位置才不会被钳回顶部（冻结后跳顶）。
            // 首屏仍显示 LoadingState：AppControlUiState 默认 loading = true，本次完成后才置 false。
            val result = withContext(Dispatchers.IO) { loadApps() }
            loaded.update {
                it.copy(
                    apps = result,
                    loading = false,
                )
            }
        }
    }

    private suspend fun loadApps(): List<ManagedApp> {
        val context = AppContext.get()
        // 系统应用两类开关是页内内存态（默认关、不记忆），不落盘：用户显式打开则查询带上。
        val visibility = local.value.systemVisibility
        val list = runCatching {
            AppCatalog.query(context, includeDisabled = true, systemVisibility = visibility)
        }.getOrNull()
        val disabledEntries = runCatching {
            DisabledAppQuery.queryDisabledEntries(context)
        }.getOrNull()
        val disabled = disabledEntries?.associateBy { it.app.packageName }

        // 禁用包可能没有桌面入口且系统包开关关闭，AppCatalog 不会给出它们；补进来，
        // 否则用户看不到「谁会被一键解冻」。
        val all = list?.apps.orEmpty() + disabledEntries.orEmpty().map { it.app }
        val apps = all.distinctBy { it.packageName }
            .map { app ->
                val known = disabled?.get(app.packageName)?.availability
                // 批量表已经给出「禁用/冻结」全集：不在表里的就是 enabled，不再每包
                // 两次 PackageManager Binder（系统包全开时是数百次 IPC）。批量查询失败
                // 时传 null 退回逐包判定，保证状态不会误报。
                app.toManaged(
                    context,
                    known ?: if (disabled != null) AppAvailability.ENABLED else null,
                )
            }
            .sortedBy { it.label.lowercase() }
        return apps
    }

    private fun AppInfo.toManaged(
        context: android.content.Context,
        availability: AppAvailability?,
    ): ManagedApp = ManagedApp(
        packageName = packageName,
        label = label.ifBlank { packageName },
        availability = availability ?: when {
            DisabledAppQuery.isSuspended(context, packageName) -> AppAvailability.SUSPENDED
            DisabledAppQuery.isDisabled(context, packageName) -> AppAvailability.DISABLED
            else -> AppAvailability.ENABLED
        },
        inOneKey = false,
        protected = packageName == context.packageName ||
            LauncherEnvironment.isLauncherPackage(context, packageName),
    )

    fun setQuery(query: String) = local.update { it.copy(query = query) }

    /** 搜索与选择互斥：展开搜索时收选择；收搜索时清 query（与剪贴板一致）。 */
    fun toggleSearch() = local.update {
        if (it.searchExpanded) {
            it.copy(searchExpanded = false, query = "")
        } else {
            it.copy(searchExpanded = true, selectionMode = false, selected = emptySet())
        }
    }

    fun toggleSelectionMode() = local.update {
        // 进选择时收搜索并清 query（搜索/选择互斥）。
        if (it.selectionMode) {
            it.copy(selectionMode = false, selected = emptySet())
        } else {
            it.copy(selectionMode = true, searchExpanded = false, query = "", selected = emptySet())
        }
    }

    fun toggleSelected(packageName: String) = local.update {
        val next = if (packageName in it.selected) {
            it.selected - packageName
        } else {
            it.selected + packageName
        }
        it.copy(selected = next)
    }

    fun selectAll(packageNames: Collection<String>) = local.update {
        it.copy(selected = packageNames.toSet())
    }

    fun setSystemVisibility(visibility: SystemAppsVisibility) {
        // 页内内存开关：只翻转本地态并重查，不落盘（默认关、不记忆）。
        local.update { it.copy(systemVisibility = visibility) }
        refresh()
    }

    /** 单行开关：已冻结则解冻；正常则冻结（suspend / 受保护直接提示）。 */
    fun toggleFreeze(app: ManagedApp) {
        when (app.availability) {
            AppAvailability.SUSPENDED -> toast(R.string.app_control_suspended_toast)
            AppAvailability.DISABLED -> runBatch(listOf(app.packageName), enable = true)
            AppAvailability.ENABLED -> {
                if (app.protected) {
                    toast(R.string.app_control_protected_toast)
                } else {
                    runBatch(listOf(app.packageName), enable = false)
                }
            }
        }
    }

    fun requestFreezeSelected() {
        val state = uiState.value
        val targets = state.apps.filter {
            it.packageName in state.selected &&
                it.availability == AppAvailability.ENABLED &&
                !it.protected
        }.map { it.packageName }
        if (targets.isEmpty()) {
            toast(R.string.app_control_batch_nothing)
            return
        }
        local.update { it.copy(pendingFreeze = targets) }
    }

    fun cancelPendingFreeze() = local.update { it.copy(pendingFreeze = emptyList()) }

    fun confirmFreezeSelected() {
        val targets = local.value.pendingFreeze
        local.update { it.copy(pendingFreeze = emptyList()) }
        if (targets.isNotEmpty()) runBatch(targets, enable = false)
    }

    fun unfreezeSelected() {
        val state = uiState.value
        val targets = state.apps.filter {
            it.packageName in state.selected && it.availability == AppAvailability.DISABLED
        }.map { it.packageName }
        if (targets.isEmpty()) {
            toast(R.string.app_control_batch_nothing)
            return
        }
        runBatch(targets, enable = true)
    }

    /** 选择器确认：整体替换名单（多选一次落盘）。 */
    fun setOneKeyList(packageNames: List<String>) {
        viewModelScope.launch {
            AppControlCenter.setOneKeyList(packageNames)
            toast(R.string.app_control_one_key_updated)
        }
    }

    fun toggleOneKey(app: ManagedApp) {
        viewModelScope.launch {
            val included = !app.inOneKey
            runCatching { AppControlCenter.setOneKey(app.packageName, included) }
                .onSuccess {
                    toast(if (included) R.string.app_control_one_key_added else R.string.app_control_one_key_removed)
                }
                .onFailure { toast(R.string.app_control_freeze_failed) }
        }
    }

    /** 选择态批量移出名单（一次落盘，保留其余成员）。 */
    fun removeSelectedFromOneKey() {
        val targets = uiState.value.selected
        if (targets.isEmpty()) return
        viewModelScope.launch {
            runCatching { AppControlCenter.removeOneKey(targets) }
                .onSuccess { toast(R.string.app_control_one_key_removed) }
                .onFailure { toast(R.string.app_control_freeze_failed) }
        }
    }

    fun clearFailures() = local.update { it.copy(failures = emptyList()) }

    private fun runBatch(packageNames: List<String>, enable: Boolean) {
        if (packageNames.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) {
                if (enable) {
                    PackageControl.unfreezeBatch(packageNames)
                } else {
                    PackageControl.freezeBatch(packageNames)
                }
            }
            local.update {
                it.copy(
                    busy = false,
                    failures = result.failures.map { failure ->
                        failure.packageName to failureText(failure.reason)
                    },
                )
            }
            toast(
                R.string.app_control_batch_result,
                result.successes.size,
                result.failures.size,
            )
            refresh()
        }
    }

    private fun failureText(reason: String): String {
        val resId = when {
            reason == PackageControl.SUSPENDED_REASON -> R.string.app_control_reason_suspended
            reason == PackageControl.PROTECTED_SELF -> R.string.app_control_reason_protected_self
            reason == PackageControl.PROTECTED_LAUNCHER -> R.string.app_control_reason_protected_launcher
            reason.contains("binder unavailable") -> R.string.app_control_reason_shizuku_unavailable
            reason.contains("permission denied") -> R.string.app_control_reason_shizuku_permission
            else -> R.string.app_control_reason_failed
        }
        return AppContext.get().getString(resId)
    }

    private fun toast(resId: Int, vararg args: Any) {
        _messages.tryEmit(AppContext.get().getString(resId, *args))
    }
}
