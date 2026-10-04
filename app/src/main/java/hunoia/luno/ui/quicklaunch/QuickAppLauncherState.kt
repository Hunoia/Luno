package hunoia.luno.ui.quicklaunch

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import hunoia.luno.bridge.PackageChangeReceiver
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.buildIndex
import hunoia.luno.quicklaunch.query.AppSearch.sortApps
import hunoia.luno.quicklaunch.query.QuickAppLauncherAppList
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.ui.theme.AnimPostHideDelay
import hunoia.luno.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import hunoia.luno.quicklaunch.query.matchesTokens


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
    /**
     * 调整面板拖拽中的实时预览：窗口全屏化后不再有窗口级缩放，拖拽预览走重组。
     * 为 null 时用已落盘的 [launcherSettings]；落盘值到达（commit 后 flow 下发）即清掉，
     * 避免覆盖已持久化的值。拖拽中 flow 无下发，所以预览不会被冲掉。
     */
    var previewSettings by mutableStateOf<hunoia.luno.config.model.QuickAppLauncherSettings?>(null)
        private set

    fun setPreviewLayout(settings: hunoia.luno.config.model.QuickAppLauncherSettings) {
        previewSettings = settings
    }
    var keyboardExpanded by mutableStateOf(true)
    var panelVisible by mutableStateOf(false)
    var closing by mutableStateOf(false)

    /** 常驻之后应用列表不再每次打开都重扫，所以装/卸应用得靠广播通知。 */
    private lateinit var onPackageChanged: () -> Unit

    /** 当前列表是在哪个系统语言下扫出来的，换语言要重扫。 */
    private var loadedLocaleTag: String = ""

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
            ConfigProvider.quickAppLauncherSettings.collectLatest {
                launcherSettings = it
                previewSettings = null
            }
        }
        onPackageChanged = {
            coroutineScope.launch {
                val state = withContext(Dispatchers.IO) {
                    DisabledAppQuery.queryQuickAppLauncherApps(context)
                }
                appListState = state
            }
        }
        PackageChangeReceiver.register(context, onPackageChanged)
        loadApps()
        onRegisterCloseAnimated?.invoke(closeAnimated)
    }

    /**
     * 组合销毁时调用。state 现在跨会话常驻，旋转 / 服务销毁都会丢弃它，
     * 不解注册的话回调会留在 [PackageChangeReceiver] 的静态列表里跟着泄漏。
     */
    fun dispose() {
        PackageChangeReceiver.unregister(context, onPackageChanged)
    }

    /**
     * 先渲染磁盘快照，再等真实扫描替换。
     *
     * 快照让面板首开不必等 PackageManager 全量扫描；扫描是首开延迟的主要来源。
     * 快照带的 label 是上次进程的运行结果，所以按 [labelLocale] 判新鲜度，
     * 语言对不上就当没有快照。
     */
    private fun loadApps() {
        coroutineScope.launch {
            val locale = currentLocaleTag()
            val snapshot = withContext(Dispatchers.IO) { ConfigProvider.getQuickAppSnapshot() }
            if (snapshot.apps.isNotEmpty() && snapshot.labelLocale == locale) {
                appListState = QuickAppLauncherAppList(snapshot.apps, snapshot.disabledPkgs)
            }
            val state = withContext(Dispatchers.IO) {
                DisabledAppQuery.queryQuickAppLauncherApps(context)
            }
            appListState = state
            loadedLocaleTag = locale
        }
    }

    private fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag()

    /**
     * 系统语言变了要重扫 —— `loadLabel` 的结果跟着语言走，而常驻列表不会自己更新。
     * 返回 true 表示确实需要重载，供 UI 层在配置变化时调用。
     */
    fun reloadAppsIfLocaleChanged(): Boolean {
        val tag = currentLocaleTag()
        if (tag == loadedLocaleTag) return false
        loadApps()
        return true
    }

    val visibleApps: List<AppInfo>
        get() = appListState.apps

    val filteredApps: List<AppInfo> by derivedStateOf {
        sortApps(visibleApps, launcherSettings, tokens)
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
     * 可见性由 overlay 驱动，而不是 content 里的 `LaunchedEffect`。
     *
     * 顺序要求：窗口必须**先**从 1×1 放大到面板尺寸，再置 true。反过来的话淡入首帧会
     * 按 1×1 定位再跳到全尺寸 —— `MainOverlayController.setPanelVisible` 的注释记的就是
     * 同一个坑（批次 9 的教训）。
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
     * 每次唤起新会话前清查询词。
     *
     * state 跨会话常驻，不清的话上一次的 tokens 会带进新会话 —— 这是常驻换来的行为变化。
     * `currentPage` 不在这里清：它活在 content 的 `remember` 里，收起时已随组合一起销毁。
     */
    fun resetForNewSession() {
        tokens = emptyList()
    }

    /**
     * 只预热当前查询命中列表的前 [PREWARM_COUNT] 个。
     *
     * 原来固定取 `appListState.apps` 的前 25 个 —— 那份列表没排序，和用户眼前看到的
     * 几乎无关，等于白预热。现在由调用方传入 `filteredApps`（已按档位/频次排好序），
     * 预热的就是马上要显示的那几个。
     *
     * 挂在 `App.applicationScope` 而不是面板 scope：面板收起即销毁，预热会被中途取消。
     * 并发用 [Semaphore] 限流而不用 `ImageLoader.Builder.fetcherDispatcher` —— 后者是
     * 全局的，会连带限掉用户滚动时正在加载的可见图标。
     */
    fun prewarmIcons(apps: List<AppInfo>, iconSizePx: Int) {
        if (iconSizePx <= 0) return
        val targets = apps.take(PREWARM_COUNT)
        if (targets.isEmpty()) return
        val loader = context.imageLoader
        App.applicationScope.launch {
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
            log = { message -> android.util.Log.i("LunoLauncher", message) },
            onLaunch = onLaunch,
            onLaunched = closeAnimated
        )
    }
}

internal enum class Page { App, Settings, Password }

internal fun pageMatchesTokens(pageName: String, tokens: List<String>): Boolean {
    val index = buildIndex(pageName)
    return matchesTokens(index.raw, tokens, false) || matchesTokens(index.pinyin, tokens, false) || matchesTokens(index.initials, tokens, false)
}

