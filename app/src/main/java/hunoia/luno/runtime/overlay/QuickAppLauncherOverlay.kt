package hunoia.luno.runtime.overlay

import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.util.Log
import hunoia.luno.BuildConfig
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.bridge.window.applyOverlayViewTreeOwners
import hunoia.luno.bridge.window.windowManager
import hunoia.luno.config.ConfigProvider
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
    private var layoutGateListener: View.OnLayoutChangeListener? = null
    private var layoutGateFallback: Runnable? = null

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
        // 门控等待中被关闭：摘掉 listener/兜底，否则之后触发会把面板重新点亮。
        clearLayoutGate()
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
        clearLayoutGate()
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
        clearLayoutGate()
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
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        // 全屏 scrim：面板外的点击落在内容层 scrim 上被消费（只关面板不传透），
        // 面板几何已搬进 Compose（Root 内按 fractions 定位），窗口只管全屏/1×1 两种尺寸。
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.MATCH_PARENT
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

    /**
     * 等 expand 的全屏 relayout 落定再点亮，避免淡入首帧画在 1×1 窗口里。
     * 窗口若本来就已是全屏（listener 永不触发）靠 300ms 兜底，revealPanel 幂等。
     */
    private fun awaitLayoutThenReveal() {
        val view = overlayView ?: return
        clearLayoutGate()
        if (view.width > WindowLayoutFactory.COMPACT_SIZE && view.height > WindowLayoutFactory.COMPACT_SIZE) {
            revealPanel()
            return
        }
        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width > WindowLayoutFactory.COMPACT_SIZE && v.height > WindowLayoutFactory.COMPACT_SIZE) {
                clearLayoutGate()
                revealPanel()
            }
        }
        layoutGateListener = listener
        view.addOnLayoutChangeListener(listener)
        val fallback = Runnable {
            clearLayoutGate()
            revealPanel()
        }
        layoutGateFallback = fallback
        view.postDelayed(fallback, 300L)
    }

    private fun clearLayoutGate() {
        layoutGateListener?.let { overlayView?.removeOnLayoutChangeListener(it) }
        layoutGateListener = null
        layoutGateFallback?.let { overlayView?.removeCallbacks(it) }
        layoutGateFallback = null
    }

    fun show() {
        val now = System.currentTimeMillis()
        val interval = if (lastCloseMs > 0) now - lastCloseMs else -1L
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "show: panelShown=$panelShown isShowing=$isShowing isHiding=$isHiding overlayView=${overlayView != null} interval=${interval}ms")
        if (panelShown || isShowing || isHiding) return
        isShowing = true

        if (overlayView != null) {
            // 常驻路径：先放大，再等窗口真正落到全屏才点亮。expand 是跨进程异步的，
            // 直接 reveal 会让淡入前几帧画在 1×1 窗口里（面板按 fractions 定位在
            // 窗口之外，全被裁掉），等 WMS 切完 traversal 整块面板在淡入中途突然
            // 出现 = 跳动。门控 + 超时兜底（revealPanel 幂等，重复调无害）。
            isShowing = false
            expandWindow()
            awaitLayoutThenReveal()
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
        val lp = createLayoutParams()
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)

            setContent {
                val advancedSettings = host.advancedSettings ?: AdvancedSettings()
                SideGestureTheme {
                    QuickAppLauncherRoot(
                        initialSettings = initialSettings,
                        requestEnableDisabledPackage = host::requestEnableDisabledPackage,
                        onCloseAnimated = { onPanelHidden() },
                        onClose = { close() },
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

    private fun createLayoutParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.RGBA_8888
        // 全屏：面板外的点击由内容层 scrim 消费（关面板、不传透下层），替代 NOT_TOUCH_MODAL + ACTION_OUTSIDE。
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        @Suppress("DEPRECATION")
        gravity = Gravity.START or Gravity.TOP
    }
}