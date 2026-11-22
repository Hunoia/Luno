package hunoia.luno.runtime.floatball.alert

import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
 * 到达提示动效的窗口层效果：屏幕边缘呼吸光、屏幕边缘流光、顶部粒子掉落、
 * 球光环扩散、全屏涟漪（胶囊层的脉冲 / 边框流光在 [BallCapsuleWindow] 自己的 Compose 层）。
 *
 * 都是事件驱动的一次性时间线：进窗后由 [LaunchedEffect] 跑完 [AlertPlayPlan.totalMillis]，
 * 末帧之后回调 [onFinished]（拆窗等末帧，ui-guidelines §5.1），窗口随即被宿主摘除——
 * 不留任何常驻动画。用户旋钮随 [AlertPlayPlan] / [AlertEffectSpec] 下发（buildPlan 已夹取
 * 归一化），各效果按自己的语义映射到 [EdgeGlowTuning] / [FlowTuning] / [ParticlesTuning] /
 * [RingTuning] / [RippleTuning] 的曲线常量；时长全部来自各自 `MotionFamily.Alert*` 家族
 * （已含应用档位与速度倍率，系统动画缩放由 Compose 自动乘）。
 */

/**
 * 屏幕边缘呼吸光的 AGSL 距离场 shader：到圆角矩形屏缘的符号距离做指数衰减，
 * 辉光连续贴合圆角（替代旧的四条矩形渐变带——角部拼接生硬、与真实边缘光差距大）。
 *
 * - `falloff` = 光带衰减尺度（用户旋钮，px）
 * - `hardness` = 衰减硬度指数（用户旋钮；1 = 纯指数，>1 光带主体更实、外缘更干脆）
 * - 包络强度（呼吸）由宿主每帧写 `intensity` uniform
 * - 圆角外侧（屏幕物理圆角裁掉的区域）按 [EdgeGlowTuning.OuterFalloffDp] 快速收尾
 */
private const val EDGE_GLOW_SHADER = """
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float falloff;
    uniform float outerFalloff;
    uniform float hardness;
    uniform float intensity;
    layout(color) uniform half4 color;

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5;
        float2 p = fragCoord - halfSize;
        float2 q = abs(p) - (halfSize - float2(cornerRadius));
        float dist = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
        float inward = max(-dist, 0.0) / falloff;
        float outward = max(dist, 0.0) / outerFalloff;
        float glow = exp(-pow(inward, hardness)) * exp(-outward);
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
    val spec = plan.spec as EdgeGlowSpec
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
        shader.setFloatUniform("cornerRadius", spec.cornerRadiusDp.dp.toPx())
        shader.setFloatUniform("falloff", falloffPx)
        shader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
        shader.setFloatUniform("hardness", spec.hardness)
        shader.setFloatUniform("intensity", intensity)
        shader.setColorUniform("color", accent.copy(alpha = 1f).toArgb())
        paint.shader = shader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/**
 * 屏幕边缘流光的 AGSL shader：在呼吸光深度衰减的基础上，用圆角矩形**周长参数化**
 * （顺时针，顶缘左端为 0）放一道沿屏缘环绕的高斯亮带；主带后随更宽更暗的拖尾，
 * 双带时第二带相隔半周长反向追逐、可换第二颜色。多带按 premultiplied 叠加。
 *
 * - `headPos` = 主带头部沿周长的归一化位置（0..1，宿主每帧写）
 * - `coreSigma` / `trailSigma` = 主带 / 拖尾高斯 σ（周长占比；拖尾 0 = 无）
 * - `dualOffset` ≥ 1 表示无第二带；0.5 = 双带
 */
private const val EDGE_FLOW_SHADER = """
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float falloff;
    uniform float outerFalloff;
    uniform float intensity;
    uniform float headPos;
    uniform float coreSigma;
    uniform float trailSigma;
    uniform float trailAlpha;
    uniform float dualOffset;
    uniform float dualSecond;
    layout(color) uniform half4 color0;
    layout(color) uniform half4 color1;

    // 圆角矩形周长参数化：返回投影点的归一化周长坐标（顺时针，顶缘左端为 0，0..1）。
    float perimeterParam(float2 p, float2 halfSize, float r) {
        float sx = halfSize.x - r;
        float sy = halfSize.y - r;
        float arc = 1.57079632 * r;
        float sx2 = 2.0 * sx;
        float sy2 = 2.0 * sy;
        if (p.y <= -sy && abs(p.x) <= sx) {
            return p.x + sx;
        }
        if (p.x >= sx && abs(p.y) <= sy) {
            return sx2 + arc + (p.y + sy);
        }
        if (p.y >= sy && abs(p.x) <= sx) {
            return sx2 + arc + sy2 + arc + (sx - p.x);
        }
        if (p.x <= -sx && abs(p.y) <= sy) {
            return sx2 + arc + sy2 + arc + sx2 + arc + (sy - p.y);
        }
        if (p.x > sx && p.y < -sy) {
            float ang = atan(p.y + sy, p.x - sx);
            return sx2 + (ang + 1.57079632) * r;
        }
        if (p.x > sx && p.y > sy) {
            float ang = atan(p.y - sy, p.x - sx);
            return sx2 + arc + sy2 + ang * r;
        }
        if (p.x < -sx && p.y > sy) {
            float ang = atan(p.y - sy, p.x + sx);
            return sx2 + arc + sy2 + arc + sx2 + (ang - 1.57079632) * r;
        }
        float ang = atan(p.y + sy, p.x + sx);
        return sx2 + arc + sy2 + arc + sx2 + arc + sy2 + (ang + 3.14159265) * r;
    }

    // 环绕高斯：s 与带心 c 的周长距离按回卷取最短，σ 为周长占比。
    float bandGlow(float s, float c, float sigma) {
        float d = abs(s - c);
        d = min(d, 1.0 - d);
        float sn = d / max(sigma, 1e-5);
        return exp(-0.5 * sn * sn);
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5;
        float2 p = fragCoord - halfSize;
        float2 q = abs(p) - (halfSize - float2(cornerRadius));
        float dist = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
        float inward = max(-dist, 0.0) / falloff;
        float outward = max(dist, 0.0) / outerFalloff;
        float depth = exp(-inward) * exp(-outward);
        float len = 4.0 * (halfSize.x - cornerRadius) + 4.0 * (halfSize.y - cornerRadius) +
            6.2831853 * cornerRadius;
        float s = perimeterParam(p, halfSize, cornerRadius) / max(len, 1.0);
        float3 outRgb = float3(0.0);
        float outA = 0.0;
        float aB = bandGlow(s, headPos, coreSigma);
        if (trailSigma > 0.0) {
            aB += bandGlow(s, headPos - coreSigma * 2.0 - trailSigma, trailSigma) * trailAlpha;
        }
        aB *= depth * intensity;
        outRgb += color0.rgb * aB;
        outA += aB;
        if (dualOffset < 1.0) {
            float a2 = bandGlow(s, headPos + dualOffset, coreSigma);
            if (trailSigma > 0.0) {
                a2 += bandGlow(s, headPos + dualOffset - coreSigma * 2.0 - trailSigma, trailSigma) *
                    trailAlpha;
            }
            a2 *= depth * intensity;
            float3 c2 = dualSecond > 0.5 ? color1.rgb : color0.rgb;
            outRgb += c2 * a2;
            outA += a2;
        }
        return half4(half4(outRgb, outA));
    }
"""

/**
 * 屏幕边缘流光：亮带沿屏缘匀速环绕（每循环一圈），深度衰减同呼吸光口径
 * （光带宽度 = 衰减尺度，用户旋钮）；拖尾 / 双带 / 双色按 [EdgeFlowSpec] 开关。
 * 第二颜色未设置时回落主题次色。绘制走 [EDGE_FLOW_SHADER]，仅播放期每帧求值。
 */
@Composable
internal fun EdgeFlowEffect(
    plan: AlertPlayPlan,
    colorSpec: ColorSpec,
    secondColorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val spec = plan.spec as EdgeFlowSpec
    val accent = colorSpec.toMarkColor()
    val secondAccent = if (secondColorSpec.isUnset) {
        MaterialTheme.colorScheme.secondary
    } else {
        secondColorSpec.toMarkColor()
    }
    val peakAlpha = plan.brightness.lerpInRange(FlowTuning.PeakAlphaMin, FlowTuning.PeakAlphaMax)
    val shader = remember { RuntimeShader(EDGE_FLOW_SHADER) }
    val paint = remember { Paint() }
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
        val falloffPx = (plan.amount
            .lerpInRange(
                FloatBallDefaults.AlertFlowBandWidthDpMin.toFloat(),
                FloatBallDefaults.AlertFlowBandWidthDpMax.toFloat(),
            ))
            .dp.toPx()
        // 绕圈进度连续跨循环（不逐圈重起步）；匀速环绕，不做缓出（多圈会停顿）。
        val headPos = (now / plan.cycleMillis) % 1f
        val coreSigma = FlowTuning.CoreBandFraction
        val trailSigma = coreSigma * spec.trailScale
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("cornerRadius", FloatBallDefaults.AlertEdgeGlowCornerDp.dp.toPx())
        shader.setFloatUniform("falloff", falloffPx)
        shader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
        shader.setFloatUniform("intensity", peakAlpha)
        shader.setFloatUniform("headPos", headPos)
        shader.setFloatUniform("coreSigma", coreSigma)
        shader.setFloatUniform("trailSigma", trailSigma)
        shader.setFloatUniform("trailAlpha", FlowTuning.TrailAlphaFraction)
        shader.setFloatUniform("dualOffset", if (spec.dualBand) 0.5f else 2f)
        shader.setFloatUniform("dualSecond", if (spec.dualBand && spec.dualColor) 1f else 0f)
        shader.setColorUniform("color0", accent.copy(alpha = 1f).toArgb())
        shader.setColorUniform("color1", secondAccent.copy(alpha = 1f).toArgb())
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
 * 顶部粒子掉落：粒子从窗口顶缘错峰出发，重力加速下落（重力指数是用户旋钮），
 * 下落途中横向正弦摆动（摆幅旋钮）；双层圆点辉光（实心核 + 低 alpha 光晕）；
 * alpha 先驻留后衰减。窗口高度 = 下落行程旋钮（宿主与效果同源）。
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
    val spec = plan.spec as ParticlesSpec
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
        val gravityExp = spec.gravityExp
        val fadeExp = ParticlesTuning.FadeExp
        val swayAmpPx = ParticlesTuning.SwayAmpNorm * spec.swayScale * w
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
            val radius = (particle.radiusDp * spec.sizeScale).dp.toPx()
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
 * 每循环发 [RingSpec.ringsPerCycle] 圈错峰重叠。
 *
 * 描边宽度与光晕缩放是用户旋钮（亮度只映射 alpha 峰值）；扩散行程由宿主按量旋钮经
 * [ringTravelPx] 算好传入，窗口按「球径 + 2×(行程 + 余量)」撑出，这里只画圆。
 */
@Composable
internal fun BallRingEffect(
    plan: AlertPlayPlan,
    ballRadiusPx: Float,
    travelPx: Float,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val spec = plan.spec as RingSpec
    val accent = colorSpec.toMarkColor()
    val peakAlpha = plan.brightness.lerpInRange(RingTuning.PeakAlphaMin, RingTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    val ringsPerCycle = spec.ringsPerCycle
    val haloScale = spec.haloScale
    val haloWidthScale = RingTuning.HaloStrokeScale
    val haloAlphaFraction = RingTuning.HaloAlphaFraction
    val easeExp = RingTuning.EaseExp
    val fadeExp = RingTuning.FadeExp
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
        val strokePx = strokeWidthDp.dp.toPx()
        val haloPx = strokePx * haloWidthScale * haloScale
        val haloAlphaFractionScaled = haloAlphaFraction * haloScale
        for (index in 0 until totalRings) {
            // 环间错峰 = 单循环时长 ÷ 每循环环数。
            val offset = index * (plan.cycleMillis.toFloat() / ringsPerCycle)
            val progress = (now - offset) / plan.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(fadeExp)
            if (alpha <= 0.005f) continue
            val radius = startRadius + travelPx * progress.easedOut(easeExp)
            if (haloPx > 0f && haloAlphaFractionScaled > 0f) {
                drawCircle(
                    color = accent,
                    alpha = alpha * haloAlphaFractionScaled,
                    radius = radius,
                    center = center,
                    style = Stroke(width = haloPx, cap = StrokeCap.Round),
                )
            }
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

/**
 * 全屏涟漪：从锚点（通知球心 / 屏幕中心，[RippleSpec.centerAtBall]）向外减速扩张、
 * 抵达屏缘并淡出的水波纹；每循环一道波（圈数旋钮 = 波数），核环 + 宽光晕双描边。
 *
 * 复用全屏穿透窗：窗口原点即屏幕原点，[ring] 的球心就是窗内坐标；
 * 行程 = 锚点到最远屏角的距离（球心锚点时从球缘起算）。描边宽度是用户旋钮。
 */
@Composable
internal fun FullRippleEffect(
    plan: AlertPlayPlan,
    ring: AlertRingGeometry?,
    colorSpec: ColorSpec,
    onFinished: () -> Unit,
) {
    val spec = plan.spec as RippleSpec
    val accent = colorSpec.toMarkColor()
    val peakAlpha = plan.brightness.lerpInRange(RippleTuning.PeakAlphaMin, RippleTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    val haloWidthScale = RippleTuning.HaloStrokeScale
    val haloAlphaFraction = RippleTuning.HaloAlphaFraction
    val easeExp = RippleTuning.EaseExp
    val fadeExp = RippleTuning.FadeExp
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
        val center = if (spec.centerAtBall && ring != null) {
            Offset(ring.centerX.toFloat(), ring.centerY.toFloat())
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }
        val startRadius = if (spec.centerAtBall) (ring?.ballPx ?: 0) / 2f * 0.9f else 0f
        val farthest = maxOf(
            (Offset.Zero - center).getDistance(),
            (Offset(size.width, 0f) - center).getDistance(),
            (Offset(0f, size.height) - center).getDistance(),
            (Offset(size.width, size.height) - center).getDistance(),
        )
        val travelPx = (farthest - startRadius).coerceAtLeast(0f)
        val strokePx = strokeWidthDp.dp.toPx()
        val haloPx = strokePx * haloWidthScale
        for (index in 0 until plan.cycles) {
            val progress = (now - index * plan.cycleMillis) / plan.cycleMillis
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
