package hunoia.luno.runtime.floatball.alert

import android.graphics.Paint
import android.graphics.RuntimeShader
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.FloatBallDefaults
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
 * 不留任何常驻动画。量旋钮（[AlertPlayPlan.amount]）与亮度（[AlertPlayPlan.brightness]）
 * 已按效果归一化，各效果按自己的语义映射到 [EdgeGlowTuning] / [ParticlesTuning] /
 * [RingTuning] 的曲线常量；时长全部来自各自 `MotionFamily.Alert*` 家族（已含应用档位，
 * 系统动画缩放由 Compose 自动乘）。
 */

/**
 * 屏幕边缘呼吸光的 AGSL 距离场 shader：到圆角矩形屏缘的符号距离做指数衰减，
 * 辉光连续贴合圆角（替代旧的四条矩形渐变带——角部拼接生硬、与真实边缘光差距大）。
 *
 * - `falloff` = 光带衰减尺度（用户旋钮，px）
 * - 包络强度（呼吸）由宿主每帧写 `intensity` uniform
 * - 圆角外侧（屏幕物理圆角裁掉的区域）按 [EdgeGlowTuning.OuterFalloffDp] 快速收尾
 */
private const val EDGE_GLOW_SHADER = """
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float falloff;
    uniform float outerFalloff;
    uniform float intensity;
    layout(color) uniform half4 color;

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5;
        float2 p = fragCoord - halfSize;
        float2 q = abs(p) - (halfSize - float2(cornerRadius));
        float dist = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
        float inward = max(-dist, 0.0) / falloff;
        float outward = max(dist, 0.0) / outerFalloff;
        float glow = exp(-inward) * exp(-outward);
        half a = half(glow * intensity);
        return half4(color.rgb * a, a);
    }
"""

/**
 * 屏幕边缘呼吸光：alpha 按 sin 包络呼吸一圈，强度映射 alpha 峰值，光带宽度是用户旋钮。
 * 绘制走 [EDGE_GLOW_SHADER]，仅播放期每帧求值。
 */
@Composable
internal fun EdgeGlowEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val peakAlpha = plan.brightness.lerpInRange(EdgeGlowTuning.PeakAlphaMin, EdgeGlowTuning.PeakAlphaMax)
    val shader = remember { RuntimeShader(EDGE_GLOW_SHADER) }
    val paint = remember { Paint() }
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
        val intensity = peakAlpha * sin(Math.PI.toFloat() * t)
        if (intensity <= 0.005f) return@Canvas
        val falloffPx = (plan.amount
            .lerpInRange(
                FloatBallDefaults.AlertEdgeGlowBandWidthDpMin.toFloat(),
                FloatBallDefaults.AlertEdgeGlowBandWidthDpMax.toFloat(),
            ))
            .dp.toPx()
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("cornerRadius", EdgeGlowTuning.ScreenCornerRadiusDp.dp.toPx())
        shader.setFloatUniform("falloff", falloffPx)
        shader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
        shader.setFloatUniform("intensity", intensity)
        shader.setColorUniform("color", accent.copy(alpha = 1f).toArgb())
        paint.shader = shader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/** 一颗粒子的静态参数：横向位置、出发延迟、横向漂移、半径、摆动相位。 */
private data class Particle(
    val xNorm: Float,
    val delayMs: Float,
    val driftNorm: Float,
    val radiusDp: Float,
    val phase: Float,
)

/**
 * 顶部粒子掉落：粒子从窗口顶缘错峰出发，重力加速下落（[ParticlesTuning.GravityExp]），
 * 下落途中横向正弦摆动；双层圆点辉光（实心核 + 低 alpha 光晕）；alpha 先驻留后衰减。
 *
 * 亮度映射 alpha 峰值，量旋钮映射粒子数量；节奏 = 波次（出发时刻摊开在波次区间内），
 * 每颗下落时长 = 单循环基准。
 */
@Composable
internal fun TopParticlesEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val peakAlpha = plan.brightness.lerpInRange(ParticlesTuning.PeakAlphaMin, ParticlesTuning.PeakAlphaMax)
    val count = plan.amount
        .lerpInRange(
            FloatBallDefaults.AlertParticlesCountMin.toFloat(),
            FloatBallDefaults.AlertParticlesCountMax.toFloat(),
        )
        .roundToInt()
    val particles = remember(plan) {
        val random = Random.Default
        List(count) {
            val hero = random.nextFloat() < ParticlesTuning.HeroFraction
            Particle(
                xNorm = 0.03f + random.nextFloat() * 0.94f,
                delayMs = random.nextFloat() * (plan.cycles - 1) * plan.cycleMillis,
                driftNorm = (random.nextFloat() - 0.5f) * ParticlesTuning.DriftNorm,
                radiusDp = (ParticlesTuning.RadiusMinDp +
                    random.nextFloat() * (ParticlesTuning.RadiusMaxDp - ParticlesTuning.RadiusMinDp))
                    .let { if (hero) it * ParticlesTuning.HeroRadiusScale else it },
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
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
        val w = size.width
        val gravityExp = ParticlesTuning.GravityExp
        val fadeExp = ParticlesTuning.FadeExp
        val swayAmpPx = ParticlesTuning.SwayAmpNorm * w
        val swayCycles = ParticlesTuning.SwayCycles
        val haloScale = ParticlesTuning.HaloScale
        val haloAlphaFraction = ParticlesTuning.HaloAlphaFraction
        for (particle in particles) {
            val progress = (now - particle.delayMs) / plan.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(fadeExp)
            if (alpha <= 0.005f) continue
            val fall = progress.pow(gravityExp)
            val sway = sin(2f * Math.PI.toFloat() * progress * swayCycles + particle.phase) * swayAmpPx
            val radius = particle.radiusDp.dp.toPx()
            val center = Offset(
                (particle.xNorm + particle.driftNorm * progress) * w + sway,
                fall * h,
            )
            // 光晕层（大而淡）+ 实心核：双层圆点辉光，替代平面实心点。
            drawCircle(
                color = accent,
                alpha = alpha * haloAlphaFraction,
                radius = radius * haloScale,
                center = center,
            )
            drawCircle(color = accent, alpha = alpha, radius = radius, center = center)
        }
    }
}

/**
 * 球光环扩散（雷达 ping）：从球缘向外**减速扩张**并淡出的圆环；核环 + 宽光晕双描边；
 * 每循环发 [RingTuning.RingsPerCycle] 圈错峰重叠。
 *
 * 亮度映射描边宽度与 alpha 峰值；扩散行程由宿主按量旋钮经 [ringTravelPx] 算好传入，
 * 窗口按「球径 + 2×(行程 + 余量)」撑出，这里只画圆。
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
    val peakAlpha = plan.brightness.lerpInRange(RingTuning.PeakAlphaMin, RingTuning.PeakAlphaMax)
    val strokeDp = plan.brightness.lerpInRange(RingTuning.StrokeMinDp, RingTuning.StrokeMaxDp)
    val haloScale = RingTuning.HaloStrokeScale
    val haloAlphaFraction = RingTuning.HaloAlphaFraction
    val easeExp = RingTuning.EaseExp
    val fadeExp = RingTuning.FadeExp
    val ringsPerCycle = RingTuning.RingsPerCycle
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
        val totalRings = plan.cycles * ringsPerCycle
        val strokePx = strokeDp.dp.toPx()
        val haloPx = strokePx * haloScale
        for (index in 0 until totalRings) {
            // 环间错峰 = 单循环 ÷ 每循环环数（不再硬编码半循环错峰）。
            val offset = index * (plan.cycleMillis.toFloat() / ringsPerCycle)
            val progress = (now - offset) / plan.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(fadeExp)
            if (alpha <= 0.005f) continue
            val radius = startRadius + travelPx * progress.easedOut(easeExp)
            drawCircle(
                color = accent,
                alpha = alpha * haloAlphaFraction,
                radius = radius,
                center = center,
                style = Stroke(width = haloPx, cap = StrokeCap.Round),
            )
            drawCircle(
                color = accent,
                alpha = alpha,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}
