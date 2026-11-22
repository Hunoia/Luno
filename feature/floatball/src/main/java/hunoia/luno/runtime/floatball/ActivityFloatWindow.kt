package hunoia.luno.runtime.floatball

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.config.model.ActivityFloatHeightRange
import hunoia.luno.config.model.ActivityFloatPeekLeft
import hunoia.luno.config.model.ActivityFloatPeekNone
import hunoia.luno.config.model.ActivityFloatPeekRight
import hunoia.luno.config.model.ActivityFloatPeekWidth
import hunoia.luno.config.model.ActivityFloatPlacement
import hunoia.luno.config.model.ActivityFloatSettings
import hunoia.luno.config.model.ActivityFloatWidthRange
import hunoia.luno.core.domain.ForegroundActivity
import hunoia.luno.runtime.overlay.changeSnapshot
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.runtime.surface.SurfaceHandle
import hunoia.luno.runtime.surface.SurfaceKind
import hunoia.luno.ui.theme.MotionFamily
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.motionMillis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 当前界面悬浮窗：部件「钉悬浮」拉起的实时小卡。
 *
 * 与 [ClipFloatWindow] 同一套窗口口径（同 flags、同拖动 / 四角缩放 / 捏合 / 边缘停靠，
 * 手势仲裁直接复用 [ClipFloatGesture]），差异只在交互与内容：
 * - 点一下 = 底部动作条（复制 / 关闭），再点收起；停靠态点一下恢复；
 * - 无长按提起 / 拖出投递（没有粘贴目标语义，[Handlers.liftable] 恒 false）；
 * - 内容不是窗口参数：卡片实时读 `ForegroundActivityHub`，前台切换即刷新。
 *
 * 生命周期同剪贴板悬浮窗（临时 Surface）：息屏 / 锁屏由宿主关闭，解锁不自动恢复；
 * 位置 / 尺寸 / 停靠侧只影响下一次打开（[ActivityFloatSettings]，横竖屏各一份）。
 */
internal class ActivityFloatWindow(
    private val context: Context,
    private val host: Host,
) : SurfaceHandle {

    interface Host {
        /** 复制点击时刻的最新前台组件名（null = 还没检测到界面，无事发生）。 */
        fun onCopy(activity: ForegroundActivity?)

        fun onClose()

        fun onSettingsChanged(settings: ActivityFloatSettings)
    }

    override val surfaceId: String = SURFACE_ID
    override val surfaceKind: SurfaceKind = SurfaceKind.TRANSIENT

    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private var root: FrameLayout? = null

    /** 退场动画期间仍挂在系统上的旧窗口；新窗口进城前先摘掉，避免两层叠着淡出。 */
    private var exitingRoot: FrameLayout? = null

    /** 停靠侧：0 正常 / -1 左缘 / 1 右缘。停靠时窗口被推出屏幕，只留一段可见。 */
    private val peekState = mutableIntStateOf(ActivityFloatPeekNone)

    /** 底部动作条（复制 / 关闭）是否展开：点卡片切换，拖动 / 缩放起手即收。 */
    private val barState = mutableStateOf(false)

    private var settings: ActivityFloatSettings = ActivityFloatSettings()
    private var attached = false
    private var positionAnimator: ValueAnimator? = null

    fun isShowing(): Boolean = attached

    @SuppressLint("RtlHardcoded")
    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            // 停靠要把窗口挪到屏幕外，没有这个 flag 负坐标会被系统夹回屏内。
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        width = widthPx()
        height = heightPx()
        gravity = Gravity.LEFT or Gravity.TOP
        alpha = 1f
        val placement = settings.placement(isLandscape())
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - width)
        val maxY = max(0, screenH - height)
        x = if (placement.peek != ActivityFloatPeekNone) {
            peekX(placement.peek, screenW, width)
        } else {
            (placement.x * maxX).roundToInt().coerceIn(0, maxX)
        }
        y = (placement.y * maxY).roundToInt().coerceIn(0, maxY)
    }

    /** 已挂窗则只按新设置重摆几何（重复「钉悬浮」不重播进场动画）；否则建窗。 */
    fun show(newSettings: ActivityFloatSettings) {
        settings = newSettings.coerced()
        barState.value = false
        if (attached) {
            applyGeometry()
            return
        }
        exitingRoot?.let { context.removeWindow(it) }
        exitingRoot = null
        peekState.intValue = settings.placement(isLandscape()).peek
        // 与悬浮球 / 胶囊同构：FrameLayout 做窗口根、owner 挂根一处（见 createWindowRoot）。
        // 服务进程没有 Activity：ComposeView 在 attach 时向上找这三个 owner，缺一即崩。
        val (_, created) = FloatBallLifecycleOwner.createWindowRoot(context) {
            SideGestureTheme {
                ActivityFloatCard(
                    peekSide = peekState.intValue,
                    barOpen = barState.value,
                    resizable = peekState.intValue == ActivityFloatPeekNone,
                    onTap = ::handleTap,
                    onCopy = { activity ->
                        barState.value = false
                        host.onCopy(activity)
                    },
                    onClose = { close() },
                    onMoveStart = ::beginMove,
                    onMoveTo = ::moveTo,
                    onMoveEnd = ::finishMove,
                    onResizeStart = ::beginResize,
                    onResizeTo = ::resizeTo,
                    onResizeEnd = ::finishResize,
                    onPinch = ::applyScale,
                    onPinchEnd = ::finishResize,
                )
            }
        }
        root = created
        if (!wm.safeAddView(created, layoutParams())) {
            root = null
            return
        }
        attached = true
        created.post {
            applyGeometry()
            // 停靠态本来就只露一条，缩放进出等于把它挤成一条缝，直接静态呈现。
            if (peekState.intValue == ActivityFloatPeekNone) playIntro(created)
        }
    }

    fun onConfigurationChanged() {
        if (attached) applyGeometry()
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
        peekState.intValue = ActivityFloatPeekNone
        barState.value = false
        positionAnimator?.cancel()
        positionAnimator = null
        if (v != null) {
            if (animated) {
                v.animate().cancel()
                v.animate().alpha(0f).scaleX(INTRO_SCALE).scaleY(INTRO_SCALE)
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

    private fun screenBounds(): Pair<Int, Int> {
        val bounds = wm.currentWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    private fun dp2px(dp: Float): Int = (dp * density).roundToInt()

    private fun peekPx(): Int = dp2px(ActivityFloatPeekWidth)

    private fun widthPx(): Int = dp2px(settings.widthDp)

    private fun heightPx(): Int = dp2px(settings.heightDp)

    private fun peekX(side: Int, screenW: Int, width: Int): Int = when (side) {
        ActivityFloatPeekLeft -> -(width - peekPx())
        ActivityFloatPeekRight -> screenW - peekPx()
        else -> 0
    }

    private fun normalizeX(x: Int, maxX: Int): Float =
        if (maxX <= 0) 0f else (x.toFloat() / maxX).coerceIn(0f, 1f)

    private fun normalizeY(y: Int, maxY: Int): Float =
        if (maxY <= 0) 0f else (y.toFloat() / maxY).coerceIn(0f, 1f)

    private fun persistPlacement(x: Int, y: Int, maxX: Int, maxY: Int, peek: Int) {
        settings = settings.withPlacement(
            landscape = isLandscape(),
            placement = ActivityFloatPlacement(
                x = normalizeX(x, maxX),
                y = normalizeY(y, maxY),
                peek = peek,
            ),
        )
        host.onSettingsChanged(settings)
    }

    /**
     * 按设置落位并（可选）动画到目标。停靠侧只在设置里表达，窗口坐标由它派生：
     * 停靠左 = 左边移出屏外只留 peek 宽度，停靠右对称。
     */
    private fun applyGeometry(animate: Boolean = false) {
        val v = root ?: return
        if (v.width <= 0 || v.height <= 0) {
            if (v.isAttachedToWindow) v.post { applyGeometry(animate) }
            return
        }
        val (screenW, screenH) = screenBounds()
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthPx()
        lp.height = heightPx()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        val placement = settings.placement(isLandscape())
        peekState.intValue = placement.peek
        val targetX = if (placement.peek != ActivityFloatPeekNone) {
            peekX(placement.peek, screenW, lp.width)
        } else {
            (placement.x * maxX).roundToInt().coerceIn(0, maxX)
        }
        val targetY = (placement.y * maxY).roundToInt().coerceIn(0, maxY)
        if (animate) animatePosition(v, lp, targetX, targetY) else {
            lp.x = targetX
            lp.y = targetY
            wm.safeUpdateViewLayout(v, lp)
        }
    }

    private fun animatePosition(
        v: View,
        lp: WindowManager.LayoutParams,
        toX: Int,
        toY: Int,
    ) {
        positionAnimator?.cancel()
        val fromX = lp.x
        val fromY = lp.y
        if (fromX == toX && fromY == toY) {
            wm.safeUpdateViewLayout(v, lp)
            return
        }
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = motionMillis(MotionFamily.FloatingSnap).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                lp.x = (fromX + (toX - fromX) * f).roundToInt()
                lp.y = (fromY + (toY - fromY) * f).roundToInt()
                wm.safeUpdateViewLayout(v, lp)
            }
            start()
        }
    }

    private fun playIntro(v: View) {
        v.alpha = 0f
        v.scaleX = INTRO_SCALE
        v.scaleY = INTRO_SCALE
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .start()
    }

    // ---- 点按 / 停靠 ----

    private fun handleTap() {
        if (peekState.intValue != ActivityFloatPeekNone) {
            restoreFromPeek()
            return
        }
        barState.value = !barState.value
    }

    private fun restoreFromPeek() {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        val x = if (peekState.intValue == ActivityFloatPeekRight) maxX else 0
        persistPlacement(x, lp.y.coerceIn(0, maxY), maxX, maxY, ActivityFloatPeekNone)
        applyGeometry(animate = true)
    }

    // ---- 拖动（绝对定位，避免窗口跟手后的局部坐标反馈抖动） ----

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartScreen: Offset? = null
    private var dragScreenW = 0
    private var dragScreenH = 0

    private fun beginMove(screen: Offset) {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        barState.value = false
        // 停靠态起手拖动：先把卡片拉回屏内再跟手，否则手指拖半天窗口还在屏外。
        if (peekState.intValue != ActivityFloatPeekNone) {
            val (screenW, screenH) = screenBounds()
            val maxX = max(0, screenW - lp.width)
            val maxY = max(0, screenH - lp.height)
            lp.x = if (peekState.intValue == ActivityFloatPeekRight) maxX else 0
            lp.y = lp.y.coerceIn(0, maxY)
            peekState.intValue = ActivityFloatPeekNone
            wm.safeUpdateViewLayout(v, lp)
        }
        positionAnimator?.cancel()
        positionAnimator = null
        dragStartX = lp.x
        dragStartY = lp.y
        dragStartScreen = screen
        // 每帧查 currentWindowMetrics 会拖慢跟手；起点缓存一次屏宽高即可。
        val (w, h) = screenBounds()
        dragScreenW = w
        dragScreenH = h
    }

    private fun moveTo(screen: Offset) {
        val start = dragStartScreen ?: return
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val dx = (screen.x - start.x).roundToInt()
        val dy = (screen.y - start.y).roundToInt()
        val before = lp.changeSnapshot()
        lp.x = (dragStartX + dx).coerceIn(0, max(0, dragScreenW - lp.width))
        lp.y = (dragStartY + dy).coerceIn(0, max(0, dragScreenH - lp.height))
        // 手指原地抖动时 x/y 不变：跳过这笔跨进程调用（拖动是每帧一次）。
        if (lp.changeSnapshot().contentEquals(before)) return
        wm.safeUpdateViewLayout(v, lp)
    }

    /** 松手结算：贴近左右边缘就停靠（移出屏外只留可见段），否则就地保存位置。 */
    private fun finishMove() {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        val snap = dp2px(SNAP_DP)
        val peek = when {
            lp.x <= snap -> ActivityFloatPeekLeft
            maxX - lp.x <= snap -> ActivityFloatPeekRight
            else -> ActivityFloatPeekNone
        }
        val visibleX = when (peek) {
            ActivityFloatPeekLeft -> 0
            ActivityFloatPeekRight -> maxX
            else -> lp.x.coerceIn(0, maxX)
        }
        persistPlacement(visibleX, lp.y.coerceIn(0, maxY), maxX, maxY, peek)
        applyGeometry(animate = true)
    }

    // ---- 四角缩放 ----

    private var resizeStartW = 0
    private var resizeStartH = 0
    private var resizeStartX = 0
    private var resizeStartY = 0
    private var resizeStartScreen: Offset? = null

    private fun beginResize(corner: Int, screen: Offset) {
        val lp = root?.layoutParams as? WindowManager.LayoutParams ?: return
        barState.value = false
        resizeStartW = lp.width
        resizeStartH = lp.height
        resizeStartX = lp.x
        resizeStartY = lp.y
        resizeStartScreen = screen
    }

    private fun resizeTo(corner: Int, screen: Offset) {
        val start = resizeStartScreen ?: return
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val dx = (screen.x - start.x).roundToInt()
        val dy = (screen.y - start.y).roundToInt()
        val (minW, minH) = minSizePx()
        val maxW = screenW.coerceAtLeast(minW)
        val maxH = screenH.coerceAtLeast(minH)

        var w = resizeStartW
        var h = resizeStartH
        var x = resizeStartX
        var y = resizeStartY
        when (corner) {
            CORNER_TL -> { w -= dx; h -= dy; x += dx; y += dy }
            CORNER_TR -> { w += dx; h -= dy; y += dy }
            CORNER_BL -> { w -= dx; h += dy; x += dx }
            else -> { w += dx; h += dy }
        }
        w = w.coerceIn(minW, maxW)
        h = h.coerceIn(minH, maxH)
        // 左上 / 左侧拖拽时保持对角固定：尺寸被夹取后，位置跟着修正。
        if (corner == CORNER_TL || corner == CORNER_BL) {
            x = resizeStartX + (resizeStartW - w)
        }
        if (corner == CORNER_TL || corner == CORNER_TR) {
            y = resizeStartY + (resizeStartH - h)
        }
        val before = lp.changeSnapshot()
        lp.x = x.coerceIn(0, max(0, screenW - w))
        lp.y = y.coerceIn(0, max(0, screenH - h))
        lp.width = w
        lp.height = h
        // 尺寸被夹到上下限后不再变化：跳过这笔跨进程调用（拖动是每帧一次）。
        if (lp.changeSnapshot().contentEquals(before)) return
        settings = settings.copy(widthDp = px2dp(w), heightDp = px2dp(h))
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun finishResize() {
        settings = settings.coerced()
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        persistPlacement(
            x = lp.x.coerceIn(0, maxX),
            y = lp.y.coerceIn(0, maxY),
            maxX = maxX,
            maxY = maxY,
            peek = ActivityFloatPeekNone,
        )
    }

    private fun applyScale(zoom: Float) {
        val nextW = settings.widthDp * zoom
        val nextH = settings.heightDp * zoom
        if (abs(nextW - settings.widthDp) < MIN_SCALE_STEP_DP &&
            abs(nextH - settings.heightDp) < MIN_SCALE_STEP_DP
        ) {
            return
        }
        settings = settings.copy(
            widthDp = nextW.coerceIn(ActivityFloatWidthRange.start, ActivityFloatWidthRange.endInclusive),
            heightDp = nextH.coerceIn(ActivityFloatHeightRange.start, ActivityFloatHeightRange.endInclusive),
        )
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthPx()
        lp.height = heightPx()
        val (screenW, screenH) = screenBounds()
        lp.x = lp.x.coerceIn(0, max(0, screenW - lp.width))
        lp.y = lp.y.coerceIn(0, max(0, screenH - lp.height))
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun minSizePx(): Pair<Int, Int> =
        dp2px(ActivityFloatWidthRange.start) to dp2px(ActivityFloatHeightRange.start)

    private fun px2dp(px: Int): Float = px / density

    private fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private companion object {
        const val SURFACE_ID = "activity-float"
        const val SNAP_DP = 24f
        const val INTRO_SCALE = 0.85f

        /** 捏合时小于这个量不改尺寸：事件比帧密，逐帧改窗口会抖。 */
        const val MIN_SCALE_STEP_DP = 0.35f
    }
}
