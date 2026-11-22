package hunoia.luno.runtime.floatball

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.config.model.WebCardHeightRange
import hunoia.luno.config.model.WebCardSettings
import hunoia.luno.config.model.WebCardWidthRange
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.runtime.surface.SurfaceHandle
import hunoia.luno.runtime.surface.SurfaceKind
import hunoia.luno.ui.theme.MotionFamily
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.motionMillis
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 搜索小窗：引擎勾了「小窗打开」后点按拉起的 WebView 悬浮窗。
 *
 * 真悬浮窗（TYPE_APPLICATION_OVERLAY）而不是 Activity：底层应用保持 resumed，
 * 卡外触摸原生穿透（[WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL]，点得到也打得动）。
 * Activity 方案做不到这一点——resumed 的 Activity 会把底下的应用压成 paused，
 * 穿透过去的触摸打不出任何反应。窗口可聚焦（WebView 输入框需要 IME）；
 * 卡外点击会把焦点还给点中的应用，卡内键盘随点按显隐。
 *
 * 生命周期同剪贴板悬浮窗（临时 Surface）：面板打开软隐藏、息屏 / 锁屏由宿主关闭、
 * 解锁不自动恢复。尺寸随 [WebCardSettings] 横竖屏各存一份（缩放松手落库）；
 * 位置会话内有效：默认居中，顶栏拖柄移位，重开回中。
 */
internal class WebCardWindow(
    private val context: Context,
    private val host: Host,
) : SurfaceHandle {

    interface Host {
        fun onClose()

        fun onSettingsChanged(settings: WebCardSettings)
    }

    override val surfaceId: String = SURFACE_ID
    override val surfaceKind: SurfaceKind = SurfaceKind.TRANSIENT

    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private var root: FrameLayout? = null

    /** 退场动画期间仍挂在系统上的旧窗口；新窗口进城前先摘掉，避免两层叠着淡出。 */
    private var exitingRoot: FrameLayout? = null

    private var settings: WebCardSettings = WebCardSettings()
    private var attached = false

    /** 导航目标：重复 show 只改这里，内容侧 LaunchedEffect(url) 跟着加载，不重建窗口。 */
    private val urlState = mutableStateOf("")

    fun isShowing(): Boolean = attached

    @SuppressLint("RtlHardcoded")
    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        // 可聚焦（WebView 输入要 IME）+ 卡外穿透：NOT_TOUCH_MODAL 允许可聚焦窗口的
        // 窗口外触摸继续派发给底下的窗口。不设 NOT_FOCUSABLE。
        flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        width = widthPx()
        height = heightPx()
        gravity = Gravity.LEFT or Gravity.TOP
        alpha = 1f
        val (screenW, screenH) = screenBounds()
        x = ((screenW - width) / 2).coerceAtLeast(0)
        y = ((screenH - height) / 2).coerceAtLeast(0)
    }

    /** 已挂窗则只换导航目标；否则建窗。 */
    fun show(url: String, newSettings: WebCardSettings) {
        settings = newSettings.coerced()
        urlState.value = url
        if (attached) return
        exitingRoot?.let { context.removeWindow(it) }
        exitingRoot = null
        // 与悬浮球 / 胶囊同构：FrameLayout 做窗口根、owner 挂根一处（见 createWindowRoot）。
        // 服务进程没有 Activity：ComposeView 在 attach 时向上找这三个 owner，缺一即崩。
        val (_, created) = FloatBallLifecycleOwner.createWindowRoot(context) {
            SideGestureTheme {
                WebCardContent(
                    url = urlState.value,
                    onMoveBy = ::moveBy,
                    onResizeBy = ::resizeBy,
                    onResizeCommit = ::commitResize,
                    onClose = { close() },
                )
            }
        }
        root = created
        if (!wm.safeAddView(created, layoutParams())) {
            root = null
            return
        }
        attached = true
        created.post { playIntro(created) }
    }

    fun onConfigurationChanged() {
        if (!attached) return
        applySize()
        reClampPosition()
    }

    /**
     * 关窗。退场按 `Floating` 家族播完再摘窗（时长取自档位，不写死常量，
     * 慢档下窗口不会在动画中途被拆掉）。
     */
    fun close(animated: Boolean = true) {
        val v = root
        val wasAttached = attached
        root = null
        attached = false
        if (v != null) {
            if (animated) {
                v.animate().cancel()
                v.animate().alpha(0f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(motionMillis(MotionFamily.Floating).toLong())
                    .withEndAction {
                        context.removeWindow(v)
                        if (exitingRoot === v) exitingRoot = null
                    }
                    .start()
                exitingRoot = v
            } else {
                context.removeWindow(v)
            }
        }
        if (wasAttached) host.onClose()
    }

    // ---- SurfaceHandle ----

    override fun softHide() {
        if (!attached) return
        setTouchable(false)
        root?.alpha = 0f
    }

    override fun restore() {
        if (!attached) return
        root?.alpha = 1f
        setTouchable(true)
    }

    private fun setTouchable(enabled: Boolean) {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val notTouchable = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (enabled != notTouchable) return
        lp.flags = if (enabled) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.safeUpdateViewLayout(v, lp)
    }

    override fun detach() {
        if (attached) close(animated = false)
        exitingRoot?.let {
            context.removeWindow(it)
            exitingRoot = null
        }
    }

    // ---- 几何 ----

    private fun screenBounds(): Pair<Int, Int> =
        wm.currentWindowMetrics.bounds.let { it.width() to it.height() }

    private fun dp2px(dp: Float): Int = (dp * density).roundToInt()

    private fun widthPx(): Int =
        dp2px(settings.widthDp(landscape())).coerceIn(1, screenBounds().first)

    private fun heightPx(): Int =
        dp2px(settings.heightDp(landscape())).coerceIn(1, screenBounds().second)

    private fun applySize() {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthPx()
        lp.height = heightPx()
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun reClampPosition() {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val before = Pair(lp.x, lp.y)
        lp.x = lp.x.coerceIn(0, max(0, screenW - lp.width))
        lp.y = lp.y.coerceIn(0, max(0, screenH - lp.height))
        if (lp.x == before.first && lp.y == before.second) return
        wm.safeUpdateViewLayout(v, lp)
    }

    /** 顶栏拖柄移位（会话内，不持久化；重开回中）。 */
    private fun moveBy(dx: Float, dy: Float) {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val before = Pair(lp.x, lp.y)
        lp.x = (lp.x + dx.roundToInt()).coerceIn(0, max(0, screenW - lp.width))
        lp.y = (lp.y + dy.roundToInt()).coerceIn(0, max(0, screenH - lp.height))
        if (lp.x == before.first && lp.y == before.second) return
        wm.safeUpdateViewLayout(v, lp)
    }

    /** 右下角手柄缩放：增量改写窗口尺寸——没有「从快照重算」这一步，跳变无从发生。 */
    private fun resizeBy(dw: Float, dh: Float) {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val marginPx = dp2px(RESIZE_MARGIN_DP)
        val maxW = dp2px(WebCardWidthRange.endInclusive).coerceAtMost(screenW - marginPx)
        val maxH = dp2px(WebCardHeightRange.endInclusive).coerceAtMost(screenH - marginPx)
        val minW = dp2px(WebCardWidthRange.start).coerceAtMost(maxW)
        val minH = dp2px(WebCardHeightRange.start).coerceAtMost(maxH)
        val before = Pair(lp.width to lp.height, lp.x to lp.y)
        lp.width = (lp.width + dw.roundToInt()).coerceIn(minW, maxW)
        lp.height = (lp.height + dh.roundToInt()).coerceIn(minH, maxH)
        lp.x = lp.x.coerceIn(0, max(0, screenW - lp.width))
        lp.y = lp.y.coerceIn(0, max(0, screenH - lp.height))
        if (before == Pair(lp.width to lp.height, lp.x to lp.y)) return
        wm.safeUpdateViewLayout(v, lp)
    }

    /** 缩放松手落库：窗口像素 → dp 回写 settings（横竖屏各一份），宿主持久化。 */
    private fun commitResize() {
        val lp = root?.layoutParams as? WindowManager.LayoutParams ?: return
        settings = settings
            .withSize(
                landscape = landscape(),
                widthDp = lp.width / density,
                heightDp = lp.height / density,
            )
            .coerced()
        host.onSettingsChanged(settings)
    }

    private fun landscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun playIntro(v: View) {
        v.alpha = 0f
        v.animate().alpha(1f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .start()
    }

    private companion object {
        const val SURFACE_ID = "web-card"

        /** 缩放上限再让出的屏边距：卡片不许顶满整屏。 */
        const val RESIZE_MARGIN_DP = 16f
    }
}
