package hunoia.luno.runtime.floatball.alert

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withContext
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.runtime.floatball.FloatBallLifecycleOwner
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.theme.SideGestureTheme

/**
 * 舞台层的锚点几何：胶囊收起圆的屏幕绝对坐标。
 *
 * [capsulePx] 是胶囊收起态直径（通知胶囊自己的 `rowHeightDp`，不是悬浮球尺寸）；
 * 涟漪从屏幕中心起播时传 0（没有起始半径）。
 */
internal data class AlertAnchor(
    val centerX: Float,
    val centerY: Float,
    val capsulePx: Int,
)

/**
 * 到达提示动效的**单舞台窗**：一个全屏穿透窗承载所有屏幕空间效果（边缘 / 粒子 / 球心·全屏），
 * 播完即摘，绝不驻留。
 *
 * 为什么只有一个窗：四类可同时播放，多窗会争 z-order、各自管生命周期、还要对齐总时长；
 * 单舞台把所有层画在同一张 Canvas 上，**一个共享时钟**跑完 [AlertPlayPlan.durationMillis]，
 * 组合动效天然成立，也不会再出现「第二个窗被占用而静默丢播」。
 *
 * LP 走 PointerOverlay 同款（`FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` + cutout
 * ALWAYS，`START|TOP`）：窗口原点 = 屏幕左上角，各层的屏幕绝对坐标可直接当窗内坐标用。
 * 全透明且不挡触摸——压在胶囊窗之上也不会挡胶囊，胶囊层效果仍画在胶囊窗自己那层。
 *
 * 生命周期归本类：效果末帧回调后先摘窗再触发 [show] 的 [onFinished]（有且仅有一次）；
 * 末帧回调因任何原因没来时由熔断兜底摘窗。
 */
internal class AlertStageWindow(private val context: Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var fallback: Runnable? = null

    /** 正在播放（含兜底待摘的收尾期）：期间的到达合并进当前播放，不重启。 */
    val isShowing: Boolean get() = root != null

    /**
     * 挂窗并播放一次，播完自行摘窗。[onFinished] 在窗口拆除后调用（主线程，有且仅有一次）。
     * 已在播放 / 计划为空时返回 false（合并语义由 [isShowing] 判定）。
     */
    fun show(
        plan: AlertPlayPlan,
        anchor: AlertAnchor,
        colorSpec: ColorSpec,
        secondColor: ColorSpec = ColorSpec.Unset,
        onFinished: () -> Unit,
    ): Boolean {
        if (root != null || plan.stageLayers.isEmpty()) return false
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            fallback?.let(mainHandler::removeCallbacks)
            fallback = null
            detach()
            onFinished()
        }
        val (_, frame) = FloatBallLifecycleOwner.createWindowRoot(context) {
            AlertStageContent(
                plan = plan,
                anchor = anchor,
                colorSpec = colorSpec,
                secondColor = secondColor,
                onFinished = { mainHandler.post { finish() } },
            )
        }
        if (!wm.safeAddView(frame, layoutParams())) {
            // 挂窗失败（权限 / 窗口被拒）：丢弃根视图，本次不播。
            context.removeWindow(frame)
            return false
        }
        root = frame
        // 熔断兜底：末帧回调因任何原因（重组丢失 / 挂起被打断）没来，也保证摘窗。
        val fallbackRun = Runnable {
            fallback = null
            finish()
        }
        fallback = fallbackRun
        mainHandler.postDelayed(fallbackRun, plan.durationMillis + FallbackSlackMs)
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
        context.removeWindow(frame)
    }

    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
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
        width = DensityProvider.screenWidthPx
        height = DensityProvider.screenHeightPx
        x = 0
        y = 0
    }

    private companion object {
        /** 末帧回调兜底余量（ms）：动画全部播完 + 一帧余量。 */
        const val FallbackSlackMs = 600L
    }
}

@Composable
private fun AlertStageContent(
    plan: AlertPlayPlan,
    anchor: AlertAnchor,
    colorSpec: ColorSpec,
    secondColor: ColorSpec,
    onFinished: () -> Unit,
) {
    SideGestureTheme {
        // 到达提示是产品决策：系统「移除动画」也照播（动画协程上下文显式固定 1f，
        // 覆盖 Recomposer 注入的 MotionDurationScale），否则 scale=0 时 tween 会瞬间结束。
        // 一个共享时钟：所有层只在绘制阶段读它（只重绘不重组）。
        val clock = remember(plan) { Animatable(0f) }
        LaunchedEffect(plan) {
            withContext(FixedMotionDurationScale) {
                clock.snapTo(0f)
                clock.animateTo(
                    targetValue = plan.durationMillis.toFloat(),
                    animationSpec = tween(
                        durationMillis = plan.durationMillis,
                        easing = LinearEasing,
                    ),
                )
            }
            withFrameNanos { }
            onFinished()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AlertStageLayers(
                plan = plan,
                anchor = anchor,
                colorSpec = colorSpec,
                secondColorSpec = secondColor,
                clock = { clock.value },
            )
        }
    }
}

/** 到达提示固定 1f 的系统动画缩放（舞台窗与胶囊窗共用；见 [AlertStageContent] 的注释）。 */
internal object FixedMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}
