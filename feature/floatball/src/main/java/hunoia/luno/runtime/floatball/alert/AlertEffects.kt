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
 * 不留任何常驻动画。各效果的观感参数在 [EdgeGlowTuning] / [ParticlesTuning] /
 * [RingTuning] 里独立调校（需求确认口径：参数不共用）；时长全部来自各自
 * `MotionFamily.Alert*` 家族（已含应用档位，系统动画缩放由 Compose 自动乘）。
 */

/**
 * 屏幕边缘呼吸光：四条边各一条向内衰减的**双层**光带（宽软带 + 窄亮核，三段软边衰减），
 * alpha 按 sin 包络呼吸一圈。
 *
 * 左右带在上下方向内缩一个光带宽度：上下带与左右带不再在四角重叠叠加发亮。
 * 强度映射光带宽度与 alpha 峰值（[EdgeGlowTuning]）；节奏 = 呼吸圈数。
 */
@Composable
internal fun EdgeGlowEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val bandDp = plan.intensity.lerpInRange(EdgeGlowTuning.BandMinDp, EdgeGlowTuning.BandMaxDp)
    val coreFraction = EdgeGlowTuning.CoreBandFraction
    val softBrightness = EdgeGlowTuning.SoftLayerBrightness
    val peakAlpha = plan.intensity.lerpInRange(EdgeGlowTuning.PeakAlphaMin, EdgeGlowTuning.PeakAlphaMax)
    val midStop = EdgeGlowTuning.FalloffMidStop
    val midBrightness = EdgeGlowTuning.FalloffMidBrightness
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
        val w = size.width
        val h = size.height
        val bandPx = bandDp.dp.toPx()
        val corePx = bandPx * coreFraction
        // 左右带的上下内缩：避开四角，防止与上下带重叠叠加。
        val sideInset = bandPx
        val sideHeight = (h - sideInset * 2f).coerceAtLeast(0f)
        val transparent = accent.copy(alpha = 0f)
        val softMid = accent.copy(alpha = midBrightness)
        // 双层辉光：宽软带（低亮度，负责「晕」）+ 窄亮核（峰值亮度贴边，负责「锐」）。
        // 三段衰减：边缘最亮 → 中途渐暗 → 内缘透明（软边，不再是硬截断）。
        for (layer in listOf(bandPx to alpha * softBrightness, corePx to alpha)) {
            val (thickness, layerAlpha) = layer
            drawRect(
                brush = Brush.verticalGradient(0f to accent, midStop to softMid, 1f to transparent),
                topLeft = Offset.Zero,
                size = Size(w, thickness),
                alpha = layerAlpha,
            )
            drawRect(
                brush = Brush.verticalGradient(0f to transparent, 1f - midStop to softMid, 1f to accent),
                topLeft = Offset(0f, h - thickness),
                size = Size(w, thickness),
                alpha = layerAlpha,
            )
            drawRect(
                brush = Brush.horizontalGradient(0f to accent, midStop to softMid, 1f to transparent),
                topLeft = Offset(0f, sideInset),
                size = Size(thickness, sideHeight),
                alpha = layerAlpha,
            )
            drawRect(
                brush = Brush.horizontalGradient(0f to transparent, 1f - midStop to softMid, 1f to accent),
                topLeft = Offset(w - thickness, sideInset),
                size = Size(thickness, sideHeight),
                alpha = layerAlpha,
            )
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
 * 强度映射粒子数量与 alpha 峰值；节奏 = 波次（出发时刻摊开在波次区间内），
 * 每颗下落时长 = 单循环基准。
 */
@Composable
internal fun TopParticlesEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val accent = colorSpec.toMarkColor()
    val peakAlpha = plan.intensity.lerpInRange(ParticlesTuning.PeakAlphaMin, ParticlesTuning.PeakAlphaMax)
    val count = plan.intensity
        .lerpInRange(ParticlesTuning.CountMin.toFloat(), ParticlesTuning.CountMax.toFloat())
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

/** 球光环扩散（雷达 ping）：从球缘向外**减速扩张**并淡出的圆环；核环 + 宽光晕双描边；
 * 每循环发 [RingTuning.RingsPerCycle] 圈错峰重叠。
 *
 * 强度映射扩散行程（[ringTravelPx]）、描边宽度与 alpha 峰值；节奏 = 圈数。
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
    val peakAlpha = plan.intensity.lerpInRange(RingTuning.PeakAlphaMin, RingTuning.PeakAlphaMax)
    val strokeDp = plan.intensity.lerpInRange(RingTuning.StrokeMinDp, RingTuning.StrokeMaxDp)
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
