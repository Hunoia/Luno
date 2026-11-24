package hunoia.luno.runtime.floatball.alert

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import hunoia.luno.core.LunoLog as Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.withContext
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.runtime.floatball.FloatBallLifecycleOwner
import hunoia.luno.runtime.overlay.AccessibilityOverlayHost
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
 * 生命周期归本类：普通动效在末帧回调后先摘窗再触发 [show] 的 [onFinished]（有且仅有一次）；
 * 锁屏关闭动效（[AlertPlayPlan.closing]）优先借用无障碍服务的 `TYPE_ACCESSIBILITY_OVERLAY`
 * 挂整段动画（普通 app overlay 在状态栏 / 导航栏之下，最后一刻仍会露内容），到达压黑点后
 * 再把窗口根视图设成不透明纯黑、回调“可以锁屏”，窗口保持到真正息屏 / 兜底超时才摘。
 */
internal class AlertStageWindow(private val context: Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var fallback: Runnable? = null

    /** 当前窗口实际用的 context：关闭态可能是无障碍服务，普通动效是悬浮球服务。 */
    private var windowContext: Context? = null

    /** 正在播放（含兜底待摘的收尾期）：期间的到达合并进当前播放，不重启。 */
    val isShowing: Boolean get() = root != null

    /**
     * 挂窗并播放一次。普通动效播完先摘窗再回调 [onFinished]；关闭态在压黑后回调，
     * 窗口保持黑屏直到 [cancel] / 兜底（主线程，有且仅有一次）。
     * 已在播放 / 计划为空时返回 false（合并语义由 [isShowing] 判定）。
     */
    fun show(
        plan: AlertPlayPlan,
        anchor: AlertAnchor,
        colorSpec: ColorSpec,
        secondColor: ColorSpec = ColorSpec.Unset,
        onFinished: () -> Unit,
        /** 当前锚点提供者：默认恒为 [anchor]；触钮跟手时由调用方喂最新手指坐标。 */
        anchorProvider: () -> AlertAnchor = { anchor },
    ): Boolean {
        if (root != null || plan.stageLayers.isEmpty()) return false
        val closing = plan.closing
        // 锁屏关闭动效整段画在无障碍浮层里：普通 app overlay 在状态栏 / 导航栏之下，
        // 最后一刻仍会把系统栏或底部内容露出来（用户实测“不是全黑”）。
        val overlayContext = if (closing) AccessibilityOverlayHost.context else null
        val windowContext = overlayContext ?: context
        val accessibility = overlayContext != null
        val coverMillis = if (closing) closingCoverMillis(plan.durationMillis) else plan.durationMillis
        var notified = false
        fun notifyFinished() {
            if (notified) return
            notified = true
            onFinished()
        }
        fun detachNow() {
            fallback?.let(mainHandler::removeCallbacks)
            fallback = null
            detach()
        }
        Log.i(
            TAG,
            "stage show closing=$closing accessibility=$accessibility " +
                "duration=${plan.durationMillis} cover=$coverMillis",
        )
        val (_, frame) = FloatBallLifecycleOwner.createWindowRoot(windowContext) {
            AlertStageContent(
                plan = plan,
                anchor = anchor,
                colorSpec = colorSpec,
                secondColor = secondColor,
                onFinished = {
                    mainHandler.post {
                        // 普通动效先摘窗再回调：回调可能立刻排队下一条，窗口必须先空出来。
                        // 关闭态保持黑屏，等真正息屏（或兜底）再摘。
                        if (!closing) {
                            detachNow()
                            notifyFinished()
                        } else {
                            // Compose 黑层之外再加一道根视图纯黑兜底：不依赖 surface alpha
                            // 或图层合成，压黑点到就是 #FF000000。
                            Log.i(TAG, "stage closing covered: hold black until screen off")
                            root?.setBackgroundColor(AndroidColor.BLACK)
                            notifyFinished()
                        }
                    }
                },
                anchorProvider = anchorProvider,
            )
        }
        val lp = layoutParams(accessibility)
        val wm = windowContext.windowManager()
        this.windowContext = windowContext
        if (!wm.safeAddView(frame, lp)) {
            // 挂窗失败（权限 / 窗口被拒）：丢弃根视图，本次不播。
            this.windowContext?.removeWindow(frame)
            this.windowContext = null
            return false
        }
        root = frame
        // 熔断兜底：末帧回调因任何原因（重组丢失 / 挂起被打断）没来，也保证回调 / 摘窗。
        val fallbackRun = Runnable {
            fallback = null
            if (closing) {
                notifyFinished()
                detachNow()
            } else {
                detachNow()
                notifyFinished()
            }
        }
        fallback = fallbackRun
        val holdExtra = if (closing) ClosingHoldMs else FallbackSlackMs
        mainHandler.postDelayed(fallbackRun, coverMillis + holdExtra)
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
        val host = windowContext ?: context
        windowContext = null
        host.removeWindow(frame)
    }

    private fun layoutParams(accessibility: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = if (accessibility) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            gravity = Gravity.START or Gravity.TOP
            // 不用显式 DensityProvider 尺寸：无障碍浮层 MATCH_PARENT 才是真正的全显示，
            // 显式高度在某些 ROM 上会把底部导航栏/圆角区域排除在外。
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
        }

    private companion object {
        const val TAG = "AlertStage"

        /** 末帧回调兜底余量（ms）：动画全部播完 + 一帧余量。 */
        const val FallbackSlackMs = 600L

        /** 关闭态黑屏保持上限：息屏广播丢失时也必须摘窗，绝不让黑幕盖住锁屏。 */
        const val ClosingHoldMs = 1_500L
    }
}

@Composable
private fun AlertStageContent(
    plan: AlertPlayPlan,
    anchor: AlertAnchor,
    colorSpec: ColorSpec,
    secondColor: ColorSpec,
    onFinished: () -> Unit,
    anchorProvider: () -> AlertAnchor,
) {
    SideGestureTheme {
        // 到达提示是产品决策：系统「移除动画」也照播（动画协程上下文显式固定 1f，
        // 覆盖 Recomposer 注入的 MotionDurationScale），否则 scale=0 时 tween 会瞬间结束。
        // 一个共享时钟：所有层只在绘制阶段读它（只重绘不重组）。
        val closing = plan.closing
        val totalMillis = if (closing) closingCoverMillis(plan.durationMillis) else plan.durationMillis
        val clock = remember(plan) { Animatable(0f) }
        LaunchedEffect(plan) {
            withContext(FixedMotionDurationScale) {
                clock.snapTo(0f)
                if (totalMillis > 0) {
                    clock.animateTo(
                        targetValue = totalMillis.toFloat(),
                        animationSpec = tween(
                            durationMillis = totalMillis,
                            easing = LinearEasing,
                        ),
                    )
                }
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
                anchorProvider = anchorProvider,
            )
            if (closing) {
                // 关闭态尾部压黑：最后 ClosingFadeMs 渐到全黑，之后保持纯黑等息屏。
                // 黑幕类形态自己已经全黑时这一层不可见，只兜底其它预设。
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val now = clock.value
                    val fadeStart = (totalMillis - ClosingFadeMs).coerceAtLeast(0).toFloat()
                    val span = (totalMillis - fadeStart).coerceAtLeast(1f)
                    val alpha = if (now >= totalMillis) {
                        1f
                    } else {
                        ((now - fadeStart) / span).coerceIn(0f, 1f)
                    }
                    if (alpha > 0.001f) {
                        drawRect(
                            color = Color.Black.copy(alpha = alpha),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                        )
                    }
                }
            }
        }
    }
}

/** 关闭态最多播多久就压黑（ms）：长/短预设都由它给出“可以锁屏”的时刻。 */
internal fun closingCoverMillis(durationMillis: Int): Int =
    durationMillis.coerceIn(0, ClosingCoverMaxMs)

/** 关闭态压黑开始前的渐隐时长（ms）。 */
internal const val ClosingFadeMs = 220

/** 关闭态压黑上限（ms）。 */
internal const val ClosingCoverMaxMs = 2_000

/** 到达提示固定 1f 的系统动画缩放（舞台窗与胶囊窗共用；见 [AlertStageContent] 的注释）。 */
internal object FixedMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}
