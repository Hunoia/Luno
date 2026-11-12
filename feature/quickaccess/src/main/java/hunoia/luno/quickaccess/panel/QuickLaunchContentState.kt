package hunoia.luno.quickaccess.panel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.imageLoader
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.quickaccess.query.AppListRepository
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.quickaccess.query.AppSearch.sortApps
import hunoia.luno.quickaccess.query.QuickAppLauncherAppList
import hunoia.luno.quickaccess.QuickLaunchFacade
import hunoia.luno.core.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import hunoia.luno.core.LOG_TAG

/**
 * 快速启动的内容态：应用列表 / 排序 / 查询词 / 键盘 / 拉起。
 *
 * 不再持有窗口、scrim、进出场与关闭编排 —— 这些归宿主：
 * - 独立快速启动面板由 `QuickAppLauncherState` 包一层窗口会话；
 * - 单功能面板由 `internal.quickLaunch` 部件直接复用本类，关闭走面板自己的 `LocalGridPanelClose`。
 *
 * 这样两处共享同一份搜索 / 键盘 / 候选 / 启动语义，不会各维护一套。
 */
class QuickLaunchContentState(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    initialSettings: QuickAppLauncherSettings,
    private val requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit,
    private val onLaunch: (AppInfo, Boolean) -> Boolean,
    private val onLaunched: () -> Unit,
) {
    private companion object {
        /** 预热数量：够铺满一屏候选行 + 一行网格即可，再多只是白占缓存。 */
        const val PREWARM_COUNT = 16

        /** 预热并发上限。解码是 IO + CPU 混合，4 条足够把单核压满，再高只会互相抢。 */
        const val PREWARM_CONCURRENCY = 4
    }

    var appListState by mutableStateOf(QuickAppLauncherAppList(emptyList(), emptySet()))
        private set
    var tokens by mutableStateOf(emptyList<String>())
        private set
    var launcherSettings by mutableStateOf(initialSettings)
        private set
    /** Luno 触发的应用启动统计（包名维度）：与布局配置分开订阅。 */
    var usageStats by mutableStateOf(AppUsageStats())
        private set
    /**
     * 调整面板拖拽中的实时预览：窗口全屏化后不再有窗口级缩放，拖拽预览走重组。
     * 为 null 时用已落盘的 [launcherSettings]；落盘值到达即清掉，避免覆盖已持久化的值。
     */
    var previewSettings by mutableStateOf<QuickAppLauncherSettings?>(null)
        private set

    var keyboardExpanded by mutableStateOf(true)

    init {
        coroutineScope.launch {
            hunoia.luno.domain.search.SearchCenter.launcher.collectLatest {
                launcherSettings = it
                previewSettings = null
            }
        }
        coroutineScope.launch {
            hunoia.luno.domain.search.SearchCenter.appUsage.collectLatest { usageStats = it }
        }
        // 应用列表走进程级共享仓（与网格面板的抽屉部件同一份，见 AppListRepository）：
        // 扫包与包变更广播只注册一次，面板/启动器各自只订阅结果。
        AppListRepository.acquire()
        coroutineScope.launch {
            AppListRepository.state.collectLatest { appListState = it }
        }
    }

    fun setPreviewLayout(settings: QuickAppLauncherSettings) {
        previewSettings = settings
    }

    /**
     * 组合销毁时调用。state 跨会话常驻，旋转 / 服务销毁都会丢弃它；
     * 不退订共享仓会让引用计数永远不退，包变更广播跟着泄漏。
     */
    fun dispose() {
        AppListRepository.release()
    }

    /**
     * 系统语言变了要重扫 —— `loadLabel` 的结果跟着语言走，而常驻列表不会自己更新。
     * 返回 true 表示确实触发了重扫（实际扫描在共享仓里跑）。
     */
    fun reloadAppsIfLocaleChanged(): Boolean = AppListRepository.reloadIfLocaleChanged()

    val filteredApps: List<AppInfo> by derivedStateOf {
        sortApps(appListState.apps, usageStats, tokens)
    }

    fun addToken(token: String) {
        tokens = tokens + token
    }

    fun clearTokens() {
        tokens = emptyList()
    }

    fun toggleKeyboard() {
        keyboardExpanded = !keyboardExpanded
    }

    fun expandKeyboard() {
        keyboardExpanded = true
    }

    /**
     * 每次唤起新会话前清查询词：state 跨会话常驻，不清的话上一次的 tokens 会带进新会话。
     * `currentPage` 不在这里清：它活在 content 的 `remember` 里，收起时已随组合销毁。
     */
    fun resetForNewSession() {
        tokens = emptyList()
    }

    /**
     * 只预热当前查询命中列表的前 [PREWARM_COUNT] 个（由调用方传 `filteredApps`，已排序）：
     * 固定取 `appListState.apps` 前 N 个与用户眼前的列表几乎无关，等于白预热。
     * 挂在 `AppContext.applicationScope` 而非面板 scope：面板收起即销毁，预热会被中途取消。
     * 并发用 [Semaphore] 限流而不用 `ImageLoader.Builder.fetcherDispatcher` —— 后者是全局的，
     * 会连带限掉用户滚动时正在加载的可见图标。
     */
    fun prewarmIcons(apps: List<AppInfo>, iconSizePx: Int) {
        if (iconSizePx <= 0) return
        val targets = apps.take(PREWARM_COUNT)
        if (targets.isEmpty()) return
        val loader = context.imageLoader
        AppContext.applicationScope!!.launch {
            val gate = Semaphore(PREWARM_CONCURRENCY)
            targets.map { app ->
                async {
                    gate.withPermit {
                        runCatching {
                            loader.execute(
                                QuickLaunchFacade.appIconRequest(context, app.packageName, iconSizePx, app.userId)
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    fun launchApp(app: AppInfo, isDisabled: Boolean, miniWindow: Boolean) {
        QuickLaunchFacade.launchApp(
            context = context,
            coroutineScope = coroutineScope,
            app = app,
            isDisabled = isDisabled,
            miniWindow = miniWindow,
            requestEnableDisabledPackage = requestEnableDisabledPackage,
            log = { message -> hunoia.luno.core.LunoLog.i(LOG_TAG, message) },
            onLaunch = onLaunch,
            onLaunched = onLaunched
        )
    }
}

internal enum class Page { App, Settings }
