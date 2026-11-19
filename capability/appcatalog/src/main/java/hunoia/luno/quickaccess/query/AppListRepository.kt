package hunoia.luno.quickaccess.query

import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.core.AppContext
import hunoia.luno.domain.search.SearchCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 应用列表的进程级共享数据源：快速启动器与网格面板的应用抽屉部件共用一份扫描结果。
 *
 * 引用计数：首个持有者 acquire 时才注册包变更广播并开始扫描，最后一个持有者 release 时
 * 退订并取消进行中的扫描。面板里的抽屉部件可能同时有多个（也允许一个面板放多个），
 * 每个都自己扫一遍 = N 次 getInstalledApplications + N 份图标请求，这里收成一份。
 *
 * 首帧先回磁盘快照（免等全量扫描），再被真实扫描覆盖；语言变了由 UI 层在配置变化时
 * 调 [reloadIfLocaleChanged] 重扫 —— `loadLabel` 的结果跟着系统语言走。
 */
object AppListRepository {

    private val _state = MutableStateFlow(QuickAppLauncherAppList(emptyList(), emptySet()))
    val state: StateFlow<QuickAppLauncherAppList> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 活动持有者数量：0 时广播已退订、扫描已取消。 */
    private var refCount = 0

    private var reloadJob: Job? = null
    private var systemAppsJob: Job? = null

    /** 当前列表是在哪个系统语言下扫出来的；空 = 还没扫过。写入在 IO、读取在主线程。 */
    @Volatile
    private var loadedLocaleTag: String = ""

    /**
     * 清单是否需要在下次打开时重扫。
     *
     * 常驻监听只置脏（包变更 / 语言变更），真正查 PackageManager 放到面板打开时；
     * 面板关闭期间装卸应用不该在后台白扫（architecture §13「无事件不做功」）。
     */
    @Volatile
    private var stale = true

    /** 常驻包变更标记：只标脏，不触发扫描。 */
    private var staleObserverRegistered = false
    private val staleMarker: () -> Unit = { stale = true }

    /** 上一次看到的系统包开关；首次 emission 只同步缓存，不重复触发整表重扫。 */
    private var lastShowSystemApps: Boolean? = null

    private val onPackageChanged: () -> Unit = { reload() }

    private fun ensureStaleObserver() {
        if (staleObserverRegistered) return
        staleObserverRegistered = true
        runCatching { PackageChangeReceiver.register(AppContext.get(), staleMarker) }
    }

    /** 需要应用列表的一方在进入组合时调用（与 [release] 成对）。 */
    fun acquire() {
        synchronized(this) {
            refCount++
            ensureStaleObserver()
            if (refCount == 1) {
                PackageChangeReceiver.register(AppContext.get(), onPackageChanged)
                // 系统应用开关是清单的一部分：真正变化时才重扫；首次 emission 只同步缓存，
                // 否则每次打开面板都会因为「重新订阅」白扫一遍系统包。
                systemAppsJob = scope.launch {
                    SearchCenter.showSystemApps.collect { enabled ->
                        val changed = lastShowSystemApps != null && lastShowSystemApps != enabled
                        lastShowSystemApps = enabled
                        AppCatalog.setShowSystemApps(enabled)
                        if (changed) {
                            stale = true
                            reload()
                        }
                    }
                }
                if (stale || _state.value.apps.isEmpty() ||
                    loadedLocaleTag != currentLocaleTag()
                ) {
                    reload()
                }
            }
        }
    }

    fun release() {
        synchronized(this) {
            if (refCount > 0) refCount--
            if (refCount > 0) return
            runCatching { PackageChangeReceiver.unregister(AppContext.get(), onPackageChanged) }
            reloadJob?.cancel()
            reloadJob = null
            systemAppsJob?.cancel()
            systemAppsJob = null
            // 不清 _state：下次 acquire 的首帧可以直接复用上次结果，扫描会随后覆盖。
        }
    }

    /** 外部写入（冻结/解冻）后的显式重扫：不等包广播，避免部分 ROM 不发 PACKAGE_CHANGED。 */
    fun refresh() {
        stale = true
        reload()
    }

    /** 系统语言变了才重扫；返回 true 表示确实触发了重扫。 */
    fun reloadIfLocaleChanged(): Boolean {
        if (currentLocaleTag() == loadedLocaleTag) return false
        // AppQuery / DisabledAppQuery 里按旧语言 loadLabel 的结果必须一起作废。
        AppQuery.invalidateLauncherCache()
        DisabledAppQuery.invalidate()
        stale = true
        reload()
        return true
    }

    private fun reload() {
        synchronized(this) {
            if (refCount == 0) return
            reloadJob?.cancel()
            reloadJob = scope.launch {
                val locale = currentLocaleTag()
                // 先记下语言：扫描期间 UI 再问一次不该判成「变了」而重扫第二遍。
                loadedLocaleTag = locale
                stale = false
                val snapshot = runCatching { hunoia.luno.domain.search.SearchCenter.getSnapshot() }.getOrNull()
                if (snapshot != null && snapshot.apps.isNotEmpty() && snapshot.labelLocale == locale) {
                    _state.value = QuickAppLauncherAppList(snapshot.apps, snapshot.disabledPkgs)
                }
                val fresh = runCatching {
                    // 先异步刷新开关再显式传值，避免 query 的默认参数走 runBlocking 读配置。
                    AppCatalog.refreshSystemSetting()
                    AppCatalog.query(
                        AppContext.get(),
                        includeDisabled = true,
                        includeSystem = AppCatalog.showSystemApps(),
                    )
                }.getOrNull()
                if (fresh != null) {
                    _state.value = fresh
                } else {
                    // 查询失败：保持旧列表，并让下次打开重试。
                    stale = true
                }
            }
        }
    }

    private fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag()
}
