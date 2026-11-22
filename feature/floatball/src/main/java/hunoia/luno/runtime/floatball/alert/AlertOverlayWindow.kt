package hunoia.luno.runtime.floatball.alert

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.runtime.floatball.FloatBallLifecycleOwner
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.theme.SideGestureTheme

/**
 * 到达提示动效的按需 overlay 窗宿主：各效果一种窗口规格，播完即摘，绝不驻留。
 *
 * - [AlertWindowKind.EdgeGlow] / [AlertWindowKind.EdgeFlow] / [AlertWindowKind.FullRipple]：
 *   全屏穿透窗（纯绘制，不挡触摸）。
 * - [AlertWindowKind.TopParticles]：顶部条窗（高度 = 下落行程旋钮）。
 * - [AlertWindowKind.BallRing]：锚定球中心的小方窗（球径 + 光环行程余量）。
 *
 * LP 走 PointerOverlay 同款（`FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` + cutout
 * ALWAYS，`START|TOP`）：坐标即屏幕绝对坐标，不卷入「LP 原点 = 状态栏下沿」的 ROM 差异。
 * 窗口尺寸 > 内容绘制边界时才有光效溢出的空间（ui-guidelines §4.6），各规格天然满足。
 *
 * 生命周期归本类：效果末帧回调后先摘窗再触发 [show] 的 [onFinished]（有且仅有一次）；
 * 末帧回调因任何原因没来时由熔断兜底摘窗。
 */
internal class AlertOverlayWindow(private val context: Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var compose: ComposeView? = null
    private var fallback: Runnable? = null

    /** 正在播放（含兜底待摘的收尾期）：期间的到达合并进当前播放，不重启。 */
    val isShowing: Boolean get() = root != null

    /**
     * 挂窗并播放一次，播完自行摘窗。[onFinished] 在窗口拆除后调用（主线程，有且仅有一次）。
     * 已在播放时返回 false（合并语义由 [isShowing] 判定）。[secondColor] 只被
     * [AlertWindowKind.EdgeFlow] 的双色模式消费。
     */
    fun show(
        kind: AlertWindowKind,
        plan: AlertPlayPlan,
        colorSpec: ColorSpec,
        ring: AlertRingGeometry?,
        onFinished: () -> Unit,
        secondColor: ColorSpec = ColorSpec.Unset,
    ): Boolean {
        if (root != null) return false
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            fallback?.let(mainHandler::removeCallbacks)
            fallback = null
            detach()
            onFinished()
        }
        val (composeView, frame) = FloatBallLifecycleOwner.createWindowRoot(context) {
            AlertContent(
                kind = kind,
                plan = plan,
                colorSpec = colorSpec,
                secondColor = secondColor,
                ring = ring,
                onFinished = { mainHandler.post { finish() } },
            )
        }
        if (!wm.safeAddView(frame, layoutParamsFor(kind, plan, ring))) {
            // 挂窗失败（权限 / 窗口被拒）：丢弃根视图，本次不播。
            context.removeWindow(frame)
            return false
        }
        root = frame
        compose = composeView
        // 熔断兜底：末帧回调因任何原因（重组丢失 / 挂起被打断）没来，也保证摘窗。
        val fallbackRun = Runnable {
            fallback = null
            finish()
        }
        fallback = fallbackRun
        mainHandler.postDelayed(fallbackRun, plan.totalMillis + FallbackSlackMs)
        return true
    }

    /** 立即摘窗（息屏 / 服务销毁 / 播放中断），不再触发 [show] 的回调。幂等。 */
    fun cancel() {
        fallback?.let(mainHandler::removeCallbacks)
        fallback = null
        detach()
    }

    private fun detach() {
        val frame = root ?: return
        root = null
        compose = null
        context.removeWindow(frame)
    }

    private fun layoutParamsFor(
        kind: AlertWindowKind,
        plan: AlertPlayPlan,
        ring: AlertRingGeometry?,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        gravity = Gravity.START or Gravity.TOP
        when (kind) {
            AlertWindowKind.EdgeGlow, AlertWindowKind.EdgeFlow, AlertWindowKind.FullRipple -> {
                width = DensityProvider.screenWidthPx
                height = DensityProvider.screenHeightPx
                x = 0
                y = 0
            }

            AlertWindowKind.TopParticles -> {
                val travelDp = (plan.spec as? ParticlesSpec)?.travelDp ?: TopStripHeightDp
                width = DensityProvider.screenWidthPx
                height = DensityProvider.dp2px(travelDp.toFloat())
                x = 0
                y = 0
            }

            AlertWindowKind.BallRing -> {
                val ballPx = ring?.ballPx ?: DensityProvider.dp2px(RingFallbackBallDp)
                val travel = ringTravelPx(ballPx, plan.amount)
                val size = ballPx + 2 * (travel + DensityProvider.dp2px(RingMarginDp))
                width = size
                height = size
                x = (ring?.centerX ?: 0) - size / 2
                y = (ring?.centerY ?: 0) - size / 2
            }
        }
    }

    private companion object {
        /** 顶部条窗高度的回退值（dp）：spec 缺失时兜底，正常路径走行程旋钮。 */
        const val TopStripHeightDp = 200f

        /** 光环窗口在行程之外的余量（dp）：描边宽度 + 抗锯齿边。 */
        const val RingMarginDp = 8f

        /** ring 缺失时的球径回退值（dp）：与球默认尺寸同源，仅兜底。 */
        const val RingFallbackBallDp = 48f

        /** 末帧回调兜底余量（ms）：动画全部播完 + 一帧余量。 */
        const val FallbackSlackMs = 600L
    }
}

/** 各独立 overlay 窗的规格。 */
internal enum class AlertWindowKind {
    EdgeGlow,
    EdgeFlow,
    TopParticles,
    BallRing,
    FullRipple,
}

/** BallRing / FullRipple 的锚定几何：屏幕绝对坐标的球心与球径（全屏涟漪屏幕中心时 ballPx = 0）。 */
internal data class AlertRingGeometry(
    val centerX: Int,
    val centerY: Int,
    val ballPx: Int,
)

@Composable
private fun AlertContent(
    kind: AlertWindowKind,
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    secondColor: ColorSpec,
    ring: AlertRingGeometry?,
    onFinished: () -> Unit,
) {
    SideGestureTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (kind) {
                AlertWindowKind.EdgeGlow -> EdgeGlowEffect(plan, colorSpec, onFinished)
                AlertWindowKind.EdgeFlow -> EdgeFlowEffect(
                    plan = plan,
                    colorSpec = colorSpec,
                    secondColorSpec = secondColor,
                    onFinished = onFinished,
                )
                AlertWindowKind.TopParticles -> TopParticlesEffect(plan, colorSpec, onFinished)
                AlertWindowKind.BallRing -> BallRingEffect(
                    plan = plan,
                    ballRadiusPx = (ring?.ballPx ?: 0) / 2f,
                    travelPx = ringTravelPx(ring?.ballPx ?: 0, plan.amount).toFloat(),
                    colorSpec = colorSpec,
                    onFinished = onFinished,
                )
                AlertWindowKind.FullRipple -> FullRippleEffect(
                    plan = plan,
                    ring = ring,
                    colorSpec = colorSpec,
                    onFinished = onFinished,
                )
            }
        }
    }
}
