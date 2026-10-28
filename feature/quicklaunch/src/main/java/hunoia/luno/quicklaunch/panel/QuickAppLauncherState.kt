package hunoia.luno.quicklaunch.panel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.quicklaunch.query.AppListRepository
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.sortApps
import hunoia.luno.quicklaunch.query.QuickAppLauncherAppList
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.ui.theme.AnimPostHideDelay
import hunoia.luno.core.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import hunoia.luno.core.LOG_TAG


class QuickAppLauncherState(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    initialSettings: QuickAppLauncherSettings,
    private val requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit,
    private val onCloseAnimatedRaw: () -> Unit,
    private val onLaunch: (AppInfo, Boolean) -> Boolean,
    private val onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
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
    var previewSettings by mutableStateOf<hunoia.luno.config.model.QuickAppLauncherSettings?>(null)
        private set

    fun setPreviewLayout(settings: hunoia.luno.config.model.QuickAppLauncherSettings) {
        previewSettings = settings
    }
    var keyboardExpanded by mutableStateOf(true)
    var panelVisible by mutableStateOf(false)
    var closing by mutableStateOf(false)

    val closeAnimated: () -> Unit = {
        if (!closing) {
            closing = true
            panelVisible = false
            coroutineScope.launch {
                delay(AnimPostHideDelay)
                onCloseAnimatedRaw()
            }
        }
    }

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
        onRegisterCloseAnimated?.invoke(closeAnimated)
    }

    /**
     * 组合销毁时调用。state 现在跨会话常驻，旋转 / 服务销毁都会丢弃它；
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

    val visibleApps: List<AppInfo>
        get() = appListState.apps

    val filteredApps: List<AppInfo> by derivedStateOf {
        sortApps(visibleApps, usageStats, tokens)
    }

    fun addToken(token: String) {
        tokens = tokens + token
    }

    fun deleteToken() {
        tokens = tokens.dropLast(1)
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
     * 可见性由 overlay 驱动，而不是 content 里的 `LaunchedEffect`：窗口必须先**从 1×1 放大到
     * 面板尺寸，再置 true；反过来淡入首帧会按 1×1 定位再跳到全尺寸。
     */
    fun setPanelShown(visible: Boolean) {
        if (panelVisible == visible) return
        panelVisible = visible
        // 必须在这里放开 closing 闩锁。closeAnimated 是 `if (!closing)` 守卫的，
        // 而 closing 置 true 之后没有任何地方复位 —— state 常驻前每次开面板都重建，
        // 所以看不出来；常驻之后第一次关闭就把闩锁焊死了，之后永远关不掉。
        if (visible) closing = false
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
                                QuickLaunchFacade.appIconRequest(context, app.packageName, iconSizePx)
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
            log = { message -> android.util.Log.i(LOG_TAG, message) },
            onLaunch = onLaunch,
            onLaunched = closeAnimated
        )
    }
}

internal enum class Page { App, Settings }

