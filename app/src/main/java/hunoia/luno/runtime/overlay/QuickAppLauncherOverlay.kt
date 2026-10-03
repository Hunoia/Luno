package hunoia.luno.runtime.overlay

import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.util.Log
import android.view.MotionEvent
import hunoia.luno.BuildConfig
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.bridge.window.applyOverlayViewTreeOwners
import hunoia.luno.bridge.window.windowManager
import hunoia.luno.config.ConfigProvider
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.runtime.GestureHost
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.quicklaunch.QuickAppLauncherRoot
import hunoia.luno.ui.quicklaunch.QuickAppLauncherState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.max
import hunoia.luno.config.model.miniWindowSettings

/**
 * 快速启动器面板的窗口宿主。
 *
 * **ComposeView 常驻**：窗口只创建一次，收起时缩到 1×1 而不是 `removeView`。这样
 * - 首开不必重查 PackageManager、不必重建搜索索引、不必重新解码图标；
 * - 而那块全屏 RGBA_8888 buffer（本机 1200×2670 ≈ 12.2MB）随窗口缩小而释放。
 *
 * 常驻的代价是 state 跨会话存活，所以每次唤起要 `resetForNewSession()` 清查询词。
 *
 * 顺序约束（批次 9 的教训）：**必须先放大窗口再让内容可见**，否则淡入首帧会按 1×1
 * 定位再跳到全尺寸。`MainOverlayController.setPanelVisible` 的注释记的是同一个坑。
 */
class QuickAppLauncherOverlay(private val host: GestureHost) {
    @Volatile private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    /** 面板内容当前是否可见。常驻之后 `overlayView != null` 不再等价于「可见」。 */
    @Volatile private var panelShown = false

    /** 首次 show 正在等 DataStore / 建 view。 */
    @Volatile private var isShowing = false
    private var isHiding = false
    private var triggerCloseAnimated: (() -> Unit)? = null
    private var lastCloseMs: Long = 0L
    @Volatile private var pendingJob: Job? = null

    /** 常驻组合持有的 state。窗口尺寸调整后由它把面板点亮。 */
    @Volatile private var residentState: QuickAppLauncherState? = null

    /** 首次创建时 state 还没建好，等 `onStateReady` 回调再点亮。 */
    private var awaitingFirstReveal = false

    var onAppLaunchRequested: ((AppInfo) -> Unit)? = null

    fun toggle() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "toggle: panelShown=$panelShown overlayView=${overlayView != null}")
        if (panelShown) close() else show()
    }

    private fun close() {
        if (!panelShown) {
            cancelPendingShow()
            return
        }
        if (isHiding) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "close: skipped (already hiding)")
            return
        }
        isHiding = true
        lastCloseMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "close: reason=explicit hasAnimation=${triggerCloseAnimated != null}")
        val animated = triggerCloseAnimated
        if (animated != null) {
            // state.closeAnimated：先把 panelVisible 置 false 跑淡出，延迟 AnimPostHideDelay
            // 后回调 onPanelHidden()，由那里收窗口。
            animated()
        } else {
            collapseWindow()
        }
    }

    /**
     * 息屏等需要立刻让位的路径。
     *
     * 只收起、不销毁 —— 计划里写的是「立即释放」，但那样息屏 30s 后再唤起就走回了
     * 重建路径，而 §7.2 的首开测试恰好就是「息屏 30s → 解锁 → 唤起」。收起已经把
     * 那 12MB 的 buffer 还回去了，组合留着不花什么。
     */
    fun closeImmediately() {
        cancelPendingShow()
        if (overlayView == null) return
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "closeImmediately: collapsing")
        residentState?.setPanelShown(false)
        collapseWindow()
    }

    /**
     * 真正销毁窗口与常驻 state。服务销毁、旋转时调用。
     *
     * `quickAppLauncherOverlay` 在 SideGestureService 里是 `by lazy` 的，
     * `onSetOverlay` 重建 GestureCoordinator 时不会重建它，所以必须显式释放，
     * 否则 ComposeView 会跟着 service 一起泄漏。
     */
    fun release() {
        cancelPendingShow()
        val view = overlayView
        if (view != null) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "release: removing overlay")
            host.context.windowManager().safeRemoveViewImmediate(view)
        }
        overlayView = null
        overlayParams = null
        panelShown = false
        isShowing = false
        isHiding = false
        awaitingFirstReveal = false
        residentState = null
        triggerCloseAnimated = null
    }

    private fun cancelPendingShow(): Boolean {
        val job = pendingJob ?: return false
        pendingJob = null
        job.cancel()
        if (overlayView == null) isShowing = false
        return true
    }

    /** 淡出结束后由 state 回调：把窗口收回 1×1。组合与 state 都留着。 */
    private fun onPanelHidden() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "panelHidden: collapsing to 1x1")
        collapseWindow()
    }

    private fun collapseWindow() {
        panelShown = false
        isHiding = false
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        lp.width = WindowLayoutFactory.COMPACT_SIZE
        lp.height = WindowLayoutFactory.COMPACT_SIZE
        // 1×1 的窗口仍然会吃落在它那一个像素上的触摸，必须显式不可触摸。
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        host.context.windowManager().safeUpdateViewLayout(view, lp)
    }

    private fun expandWindow() {
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        val settings = residentState?.launcherSettings ?: return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        lp.applyPanelLayout(settings)
        host.context.windowManager().safeUpdateViewLayout(view, lp)
    }

    /** 点亮面板。幂等：state 还没就绪时什么都不做，等 `onStateReady` 或 post 兜底再来。 */
    private fun revealPanel() {
        if (panelShown) return
        val state = residentState ?: return
        panelShown = true
        state.resetForNewSession()
        state.setPanelShown(true)
    }

    fun show() {
        val now = System.currentTimeMillis()
        val interval = if (lastCloseMs > 0) now - lastCloseMs else -1L
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "show: panelShown=$panelShown isShowing=$isShowing isHiding=$isHiding overlayView=${overlayView != null} interval=${interval}ms")
        if (panelShown || isShowing || isHiding) return
        isShowing = true

        if (overlayView != null) {
            // 常驻路径：窗口与组合都在，只放大 + 点亮。顺序不能反。
            isShowing = false
            expandWindow()
            revealPanel()
            return
        }

        pendingJob = host.coroutineScope.launch {
            try {
                val initialSettings = loadSettingsAsync()
                withContext(Dispatchers.Main.immediate) {
                    if (!isActive) return@withContext
                    showOverlayView(initialSettings)
                }
            } finally {
                if (pendingJob === coroutineContext[Job]) pendingJob = null
                if (overlayView == null) isShowing = false
            }
        }
    }

    private suspend fun loadSettingsAsync() = withContext(Dispatchers.IO) {
        ConfigProvider.getQuickAppLauncherSettings()
    }

    private fun showOverlayView(initialSettings: QuickAppLauncherSettings) {
        val wm = host.context.windowManager()
        val lp = createLayoutParams(initialSettings)
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)

            setOnTouchListener(createDismissOnOutsideTouch(onOutsideTouch = {
                close()
            }, logTag = "touch"))

            setContent {
                val advancedSettings = host.advancedSettings ?: AdvancedSettings()
                SideGestureTheme {
                    QuickAppLauncherRoot(
                        initialSettings = initialSettings,
                        requestEnableDisabledPackage = host::requestEnableDisabledPackage,
                        onCloseAnimated = { onPanelHidden() },
                        onUpdateLayout = { settings -> updateLayout(settings) },
                        onLaunch = { appInfo, miniWindow ->
                            val nowTs = System.currentTimeMillis()
                            val gap = if (lastCloseMs > 0) nowTs - lastCloseMs else -1L
                            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "appClick: ${appInfo.label} pkg=${appInfo.packageName} miniWindow=$miniWindow intervalSinceClose=${gap}ms")
                            val success = Launcher.launchAppInfo(
                                context = host.context,
                                appInfo = appInfo,
                                miniWindow = miniWindow,
                                miniWindowSettings = advancedSettings.miniWindowSettings(),
                            )
                            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "appClick: ${appInfo.label} launchResult=$success")
                            if (success) onAppLaunchRequested?.invoke(appInfo)
                            success
                        },
                        onRegisterCloseAnimated = { callback -> triggerCloseAnimated = callback },
                        onStateReady = { state ->
                            residentState = state
                            if (awaitingFirstReveal) {
                                awaitingFirstReveal = false
                                isShowing = false
                                revealPanel()
                            }
                        },
                    )
                }
            }
        }
        if (!wm.safeAddView(composeView, lp)) {
            isShowing = false
            return
        }
        overlayView = composeView
        overlayParams = lp
        awaitingFirstReveal = true

        // 组合是 addView 之后异步建立的。onStateReady 是主路径；这个 post 只兜底
        // 「组合没跑起来」的情况，revealPanel 幂等所以重复调用无害。
        composeView.post {
            if (awaitingFirstReveal) {
                awaitingFirstReveal = false
                isShowing = false
                revealPanel()
            }
        }
    }

    private fun createLayoutParams(settings: QuickAppLauncherSettings) = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.RGBA_8888
        height = WindowManager.LayoutParams.WRAP_CONTENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        @Suppress("DEPRECATION")
        gravity = Gravity.START or Gravity.TOP
        applyPanelLayout(settings)
    }

    private fun WindowManager.LayoutParams.applyPanelLayout(settings: QuickAppLauncherSettings) {
        val screenWidth = DensityProvider.screenWidthPx
        val screenHeight = DensityProvider.screenHeightPx
        val panelWidth = (screenWidth * settings.panelWidthFraction.coerceIn(0.65f, 1f)).roundToInt().coerceIn(1, screenWidth)
        val estimatedHeight = estimatePanelHeightPx(settings).coerceAtMost(screenHeight)
        width = panelWidth
        x = ((screenWidth - panelWidth) * settings.panelHorizontalBias.coerceIn(0f, 1f)).roundToInt().coerceIn(0, screenWidth - panelWidth)
        val maxY = max(0, screenHeight - estimatedHeight)
        y = (screenHeight * (1f - settings.panelHeightFraction.coerceIn(0.05f, 0.95f))).roundToInt()
            .coerceIn(0, maxY)
        height = estimatedHeight
    }

    private fun estimatePanelHeightPx(settings: QuickAppLauncherSettings): Int {
        return (DensityProvider.screenHeightPx * settings.contentHeightFraction.coerceIn(0.35f, 0.85f)).roundToInt()
    }

    /** 调整面板拖动时的实时改窗口尺寸。只在面板可见时才会被调到。 */
    private fun updateLayout(settings: QuickAppLauncherSettings) {
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        lp.applyPanelLayout(settings)
        host.context.windowManager().safeUpdateViewLayout(view, lp)
    }
}

private fun createDismissOnOutsideTouch(
    onOutsideTouch: () -> Unit,
    logTag: String,
): View.OnTouchListener = View.OnTouchListener { v, event ->
    if (event.action == MotionEvent.ACTION_OUTSIDE) {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "$logTag: ACTION_OUTSIDE at (${event.rawX.toInt()}, ${event.rawY.toInt()}) → close")
        onOutsideTouch()
        v.performClick()
        true
    } else {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "$logTag: action=${event.action} at (${event.rawX.toInt()}, ${event.rawY.toInt()})")
        false
    }
}