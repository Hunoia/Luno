package hunoia.luno.runtime.floatball.alert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.ui.theme.toMarkColor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 三种独立 overlay 窗的到达提示效果：屏幕边缘呼吸光、顶部粒子掉落、球光环扩散。
 *
 * 都是事件驱动的一次性时间线：进窗后由 [LaunchedEffect] 跑完 [AlertPlayPlan.totalMillis]，
 * 末帧之后回调 [onFinished]（拆窗等末帧，ui-guidelines §5.1），窗口随即被宿主摘除——
 * 不留任何常驻动画。时长全部来自 `MotionFamily.NotificationAlert`（已含应用档位，
 * 系统动画缩放由 Compose 自动乘）。
 */

/** 球光环的扩散行程（px）：随强度拉远，宿主与效果共用同一份口径定窗口余量。 */
internal fun ringTravelPx(ballPx: Int, intensity: Float): Int =
    (ballPx * (0.7f + 0.5f * intensity)).roundToInt()

/**
 * 屏幕边缘呼吸光：四条边各一条向内衰减的渐变光带，alpha 按 sin 包络呼吸一圈。
 *
 * 强度映射光带宽度（64–160dp）与 alpha 峰值（0.25–0.8）；节奏 = 呼吸圈数。
 */
@Composable
internal fun EdgeGlowEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val band = (64f + 96f * plan.intensity).dp
    val peakAlpha = 0.25f + 0.55f * plan.intensity
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(plan) {
        repeat(plan.cycles) { index ->
            elapsed.snapTo((index * plan.cycleMillis).toFloat())
            elapsed.animateTo(
                ((index + 1) * plan.cycleMillis).toFloat(),
                tween(durationMillis = plan.cycleMillis, easing = LinearEasing),
            )
        }
        withFrameNanos { }
        onFinished()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = (elapsed.value / plan.cycleMillis) % 1f
        val alpha = peakAlpha * sin(Math.PI.toFloat() * t)
        if (alpha <= 0.005f) return@Canvas
        val bandPx = band.toPx()
        val w = size.width
        val h = size.height
        val transparent = accent.copy(alpha = 0f)
        drawRect(
            brush = Brush.verticalGradient(0f to accent, 1f to transparent),
            topLeft = Offset.Zero,
            size = Size(w, bandPx),
            alpha = alpha,
        )
        drawRect(
            brush = Brush.verticalGradient(0f to transparent, 1f to accent),
            topLeft = Offset(0f, h - bandPx),
            size = Size(w, bandPx),
            alpha = alpha,
        )
        drawRect(
            brush = Brush.horizontalGradient(0f to accent, 1f to transparent),
            topLeft = Offset.Zero,
            size = Size(bandPx, h),
            alpha = alpha,
        )
        drawRect(
            brush = Brush.horizontalGradient(0f to transparent, 1f to accent),
            topLeft = Offset(w - bandPx, 0f),
            size = Size(bandPx, h),
            alpha = alpha,
        )
    }
}

/** 一颗粒子的静态参数：横向位置、出发延迟、横向漂移、半径。 */
private data class Particle(
    val xNorm: Float,
    val delayMs: Float,
    val driftNorm: Float,
    val radiusDp: Float,
)

/**
 * 顶部粒子掉落：粒子从窗口顶缘错峰出发，重力加速下落并淡出。
 *
 * 强度映射粒子数量（20–80）与 alpha 峰值；节奏 = 波次（出发时刻摊开在
 * 波次区间内），每颗下落时长 = 单循环基准。
 */
@Composable
internal fun TopParticlesEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val peakAlpha = 0.35f + 0.45f * plan.intensity
    val count = (20f + 60f * plan.intensity).roundToInt()
    val particles = remember(plan) {
        val random = Random.Default
        List(count) {
            Particle(
                xNorm = 0.03f + random.nextFloat() * 0.94f,
                delayMs = random.nextFloat() * (plan.cycles - 1) * plan.cycleMillis,
                driftNorm = (random.nextFloat() - 0.5f) * 0.06f,
                radiusDp = 1.5f + random.nextFloat() * 1.7f,
            )
        }
    }
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(plan) {
        elapsed.snapTo(0f)
        elapsed.animateTo(
            plan.totalMillis.toFloat(),
            tween(durationMillis = plan.totalMillis, easing = LinearEasing),
        )
        withFrameNanos { }
        onFinished()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = elapsed.value
        if (now >= plan.totalMillis) return@Canvas
        val h = size.height
        for (particle in particles) {
            val progress = (now - particle.delayMs) / plan.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val fall = progress.pow(1.6f)
            drawCircle(
                color = accent,
                alpha = peakAlpha * (1f - progress),
                radius = particle.radiusDp.dp.toPx(),
                center = Offset(
                    (particle.xNorm + particle.driftNorm * progress) * size.width,
                    fall * h,
                ),
            )
        }
    }
}

/**
 * 球光环扩散（雷达 ping）：从球缘向外扩散并淡出的圆环，圈与圈错峰重叠。
 *
 * 强度映射扩散行程（`ringTravelPx`）与 alpha 峰值；节奏 = 圈数。
 * 窗口由宿主按「球径 + 2×(行程 + 余量)」撑出，这里只画圆。
 */
@Composable
internal fun BallRingEffect(
    plan: AlertPlayPlan,
    ballRadiusPx: Float,
    travelPx: Float,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val peakAlpha = 0.35f + 0.45f * plan.intensity
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(plan) {
        elapsed.snapTo(0f)
        elapsed.animateTo(
            plan.totalMillis.toFloat(),
            tween(durationMillis = plan.totalMillis, easing = LinearEasing),
        )
        withFrameNanos { }
        onFinished()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = elapsed.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val startRadius = ballRadiusPx * 0.9f
        for (index in 0 until plan.cycles) {
            val progress = (now - index * plan.cycleMillis * 0.5f) / plan.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            drawCircle(
                color = accent,
                alpha = peakAlpha * (1f - progress),
                radius = startRadius + travelPx * progress,
                center = center,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
