package hunoia.luno.runtime.floatball.alert

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.ParticleAlertStyle
import hunoia.luno.ui.theme.toMarkColor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 到达提示动效的舞台层绘制（全屏穿透窗内）：边缘呼吸光 / 边缘流光 / 粒子 / 球光环 / 全屏涟漪。
 * 胶囊层的脉冲 / 边框流光在 [hunoia.luno.runtime.floatball.BallCapsuleWindow] 自己的 Compose 层。
 *
 * 所有层**共用一个时钟**（由 [AlertStageWindow] 驱动 [AlertPlayPlan.durationMillis]），
 * 各层只在 `Canvas` 的绘制阶段读 `clock()` —— 每帧只重绘不重组，也没有各自的生命周期。
 * 层序固定：边缘 → 粒子 → 球心·全屏（后者压在上面），与 [AlertStageLayers] 的遍历顺序一致。
 *
 * 用户旋钮随 [AlertLayerPlan] / [AlertEffectSpec] 下发（[buildPlan] 已夹取归一化），
 * 各层按自己的语义映射到 [EdgeGlowTuning] / [FlowTuning] / [ParticlesTuning] /
 * [RingTuning] / [RippleTuning] 的曲线常量；时长已含应用档位与速度倍率。
 */

private const val TAG = "AlertEffects"

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
 * 屏幕边缘流光的 AGSL shader：在呼吸光深度衰减的基础上，用圆角矩形**周长参数化**
 * （顺时针，顶缘左端为 0）放一道沿屏缘环绕的高斯亮带；主带后随更宽更暗的拖尾。
 *
 * 刃感来自三个用户旋钮：`coreSigma`（主带宽，周长占比）、`hardness`（深度衰减硬度）、
 * `trailAlpha`（拖尾浓度）。双带时第二带**反向**行进（相隔半圈朝对方走），拖尾在它
 * 自己的行进反侧。
 *
 * - `headPos` / `headPos2` = 主带 / 第二带头部沿周长的归一化位置
 * - `trailBack2` = 第二带拖尾相对行进方向的侧别（-1 = 反向带）
 * - `dualOffset` ≥ 1 表示无第二带
 */
private const val EDGE_FLOW_SHADER = """
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float falloff;
    uniform float outerFalloff;
    uniform float hardness;
    uniform float intensity;
    uniform float headPos;
    uniform float headPos2;
    uniform float coreSigma;
    uniform float trailSigma;
    uniform float trailAlpha;
    uniform float dualOffset;
    uniform float dualSecond;
    uniform float trailBack2;
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
        float depth = exp(-pow(inward, hardness)) * exp(-outward);
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
            float a2 = bandGlow(s, headPos2, coreSigma);
            if (trailSigma > 0.0) {
                a2 += bandGlow(
                    s,
                    headPos2 - trailBack2 * (coreSigma * 2.0 + trailSigma),
                    trailSigma
                ) * trailAlpha;
            }
            a2 *= depth * intensity;
            float3 c2 = dualSecond > 0.5 ? color1.rgb : color0.rgb;
            outRgb += c2 * a2;
            outA += a2;
        }
        return half4(outRgb, outA);
    }
"""

/** 舞台层的总入口：按计划里的舞台层顺序绘制（胶囊层不在这里）。 */
@Composable
internal fun AlertStageLayers(
    plan: AlertPlayPlan,
    anchor: AlertAnchor,
    colorSpec: ColorSpec,
    secondColorSpec: ColorSpec,
    clock: () -> Float,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        for (layer in plan.stageLayers) {
            key(layer.style) {
                when (val style = layer.style) {
                    is EdgeAlertStyle -> when (style) {
                        EdgeAlertStyle.EdgeGlow -> EdgeGlowLayer(layer, colorSpec, clock)
                        EdgeAlertStyle.EdgeFlow -> EdgeFlowLayer(
                            layer = layer,
                            colorSpec = colorSpec,
                            secondColorSpec = secondColorSpec,
                            clock = clock,
                        )
                    }

                    is ParticleAlertStyle -> when (style) {
                        ParticleAlertStyle.TopRain -> TopRainLayer(layer, colorSpec, clock)
                    }

                    is AnchorAlertStyle -> when (style) {
                        AnchorAlertStyle.Ring -> BallRingLayer(layer, anchor, colorSpec, clock)
                        AnchorAlertStyle.Ripple -> FullRippleLayer(layer, anchor, colorSpec, clock)
                    }

                    is CapsuleAlertStyle -> Unit
                }
            }
        }
    }
}

/**
 * 屏幕边缘呼吸光：alpha 按 sin 包络呼吸一圈，强度映射 alpha 峰值，光带宽度是用户旋钮。
 * 绘制走 [EDGE_GLOW_SHADER]，仅播放期每帧求值。
 */
@Composable
private fun EdgeGlowLayer(
    layer: AlertLayerPlan,
    colorSpec: ColorSpec,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeGlowSpec
    val accent = colorSpec.toMarkColor()
    val peakAlpha = layer.brightness.lerpInRange(EdgeGlowTuning.PeakAlphaMin, EdgeGlowTuning.PeakAlphaMax)
    val shader = remember { createShader(EDGE_GLOW_SHADER, "edgeGlow") }
    val paint = remember { Paint() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val activeShader = shader ?: return@Canvas
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val intensity = peakAlpha * sin(Math.PI.toFloat() * t)
        if (intensity <= 0.005f) return@Canvas
        val falloffPx = layer.amount
            .lerpInRange(
                FloatBallDefaults.AlertEdgeGlowBandWidthDpMin.toFloat(),
                FloatBallDefaults.AlertEdgeGlowBandWidthDpMax.toFloat(),
            )
            .dp.toPx()
        activeShader.setFloatUniform("resolution", size.width, size.height)
        activeShader.setFloatUniform("cornerRadius", spec.cornerRadiusDp.dp.toPx())
        activeShader.setFloatUniform("falloff", falloffPx)
        activeShader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
        activeShader.setFloatUniform("hardness", spec.hardness)
        activeShader.setFloatUniform("intensity", intensity)
        activeShader.setColorUniform("color", accent.copy(alpha = 1f).toArgb())
        paint.shader = activeShader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/**
 * 屏幕边缘流光：亮带沿屏缘匀速环绕（每循环一圈），深度衰减口径与呼吸光一致。
 * 刃感 = 主带宽（`coreBand`）+ 深度硬度（`hardness`）+ 拖尾浓度（`trailAlpha`）三件套；
 * 双带时第二带反向行进。整段首尾各有 [FlowTuning.EnvelopeShare] 的淡入淡出。
 */
@Composable
private fun EdgeFlowLayer(
    layer: AlertLayerPlan,
    colorSpec: ColorSpec,
    secondColorSpec: ColorSpec,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeFlowSpec
    val accent = colorSpec.toMarkColor()
    val secondAccent = if (secondColorSpec.isUnset) {
        MaterialTheme.colorScheme.secondary
    } else {
        secondColorSpec.toMarkColor()
    }
    val peakAlpha = layer.brightness.lerpInRange(FlowTuning.PeakAlphaMin, FlowTuning.PeakAlphaMax)
    val shader = remember { createShader(EDGE_FLOW_SHADER, "edgeFlow") }
    val paint = remember { Paint() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val activeShader = shader ?: return@Canvas
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, FlowTuning.EnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val falloffPx = layer.amount
            .lerpInRange(
                FloatBallDefaults.AlertFlowBandWidthDpMin.toFloat(),
                FloatBallDefaults.AlertFlowBandWidthDpMax.toFloat(),
            )
            .dp.toPx()
        // 绕圈进度连续跨循环（不逐圈重起步）；匀速环绕，不做缓出（多圈会停顿）。
        val headPos = (now / layer.cycleMillis) % 1f
        // 双带相向：第二带从对侧起手、沿反方向走（文案口径）。
        val headPos2 = if (spec.dualBand) (1.5f - headPos) % 1f else 0f
        val trailSigma = spec.coreBand * spec.trailScale
        activeShader.setFloatUniform("resolution", size.width, size.height)
        activeShader.setFloatUniform("cornerRadius", spec.cornerRadiusDp.dp.toPx())
        activeShader.setFloatUniform("falloff", falloffPx)
        activeShader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
        activeShader.setFloatUniform("hardness", spec.hardness)
        activeShader.setFloatUniform("intensity", peakAlpha * envelope)
        activeShader.setFloatUniform("headPos", headPos)
        activeShader.setFloatUniform("headPos2", headPos2)
        activeShader.setFloatUniform("coreSigma", spec.coreBand)
        activeShader.setFloatUniform("trailSigma", trailSigma)
        activeShader.setFloatUniform("trailAlpha", spec.trailAlpha)
        activeShader.setFloatUniform("dualOffset", if (spec.dualBand) 0.5f else 2f)
        activeShader.setFloatUniform("dualSecond", if (spec.dualBand && spec.dualColor) 1f else 0f)
        activeShader.setFloatUniform("trailBack2", if (spec.dualBand) -1f else 1f)
        activeShader.setColorUniform("color0", accent.copy(alpha = 1f).toArgb())
        activeShader.setColorUniform("color1", secondAccent.copy(alpha = 1f).toArgb())
        paint.shader = activeShader
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
 * 顶部雨：粒子从绘制带顶缘错峰出发，重力加速下落（重力指数是用户旋钮），
 * 下落途中横向正弦摆动（摆幅旋钮）；双层圆点辉光（实心核 + 低 alpha 光晕）；
 * alpha 先驻留后衰减。
 *
 * 单颗粒子寿命 = 单循环 × [ParticlesTuning.LifeShare]，其余时长用来摊开出发时刻——
 * 这样 `cycles = 1` 也是一场雨，而不是所有粒子同刻出发的一堵墙。
 * 绘制带高 = 下落行程旋钮（[ParticlesSpec.travelDp]）。
 */
@Composable
private fun TopRainLayer(
    layer: AlertLayerPlan,
    colorSpec: ColorSpec,
    clock: () -> Float,
) {
    val spec = layer.spec as ParticlesSpec
    val accent = colorSpec.toMarkColor()
    val peakAlpha = layer.brightness
        .lerpInRange(ParticlesTuning.PeakAlphaMin, ParticlesTuning.PeakAlphaMax)
    val count = layer.amount
        .lerpInRange(
            FloatBallDefaults.AlertParticlesCountMin.toFloat(),
            FloatBallDefaults.AlertParticlesCountMax.toFloat(),
        )
        .roundToInt()
    val lifetimeMs = layer.cycleMillis * ParticlesTuning.LifeShare
    val spreadMs = (layer.totalMillis - lifetimeMs).coerceAtLeast(1f)
    val particles = remember(layer) {
        val random = Random.Default
        List(count) {
            val hero = random.nextFloat() < ParticlesTuning.HeroFraction
            Particle(
                xNorm = 0.03f + random.nextFloat() * 0.94f,
                delayMs = random.nextFloat() * spreadMs,
                driftNorm = (random.nextFloat() - 0.5f) * ParticlesTuning.DriftNorm,
                radiusDp = (ParticlesTuning.RadiusMinDp +
                    random.nextFloat() * (ParticlesTuning.RadiusMaxDp - ParticlesTuning.RadiusMinDp))
                    .let { radius -> if (hero) radius * ParticlesTuning.HeroRadiusScale else radius },
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val w = size.width
        val travelPx = spec.travelDp.dp.toPx()
        val fadeExp = ParticlesTuning.FadeExp
        val swayAmpPx = ParticlesTuning.SwayAmpNorm * spec.swayScale * w
        for (particle in particles) {
            val progress = (now - particle.delayMs) / lifetimeMs
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(fadeExp)
            if (alpha <= 0.005f) continue
            val fall = progress.pow(spec.gravityExp)
            val sway = sin(
                2f * Math.PI.toFloat() * progress * ParticlesTuning.SwayCycles + particle.phase,
            ) * swayAmpPx
            val radius = (particle.radiusDp * spec.sizeScale).dp.toPx()
            val center = Offset(
                x = (particle.xNorm + particle.driftNorm * progress) * w + sway,
                y = fall * travelPx,
            )
            // 光晕层（大而淡）+ 实心核：双层圆点辉光，替代平面实心点。
            drawCircle(
                color = accent,
                alpha = alpha * ParticlesTuning.HaloAlphaFraction,
                radius = radius * ParticlesTuning.HaloScale,
                center = center,
            )
            drawCircle(color = accent, alpha = alpha, radius = radius, center = center)
        }
    }
}

/**
 * 球光环扩散（雷达 ping）：从胶囊收起圆的边缘向外**减速扩张**并淡出的圆环；
 * 核环 + 宽光晕双描边；每循环发 [RingSpec.ringsPerCycle] 圈错峰重叠。
 *
 * 锚点是胶囊的屏幕绝对坐标（[AlertAnchor]），起始半径按胶囊行高算——不再误用悬浮球尺寸。
 */
@Composable
private fun BallRingLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    colorSpec: ColorSpec,
    clock: () -> Float,
) {
    val spec = layer.spec as RingSpec
    val accent = colorSpec.toMarkColor()
    val peakAlpha = layer.brightness.lerpInRange(RingTuning.PeakAlphaMin, RingTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    val ringsPerCycle = spec.ringsPerCycle
    val haloScale = spec.haloScale
    val travelPx = ringTravelPx(anchor.capsulePx, layer.amount).toFloat()
    val center = Offset(anchor.centerX, anchor.centerY)
    val startRadius = anchor.capsulePx / 2f * 0.9f
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val totalRings = layer.cycles * ringsPerCycle
        val strokePx = strokeWidthDp.dp.toPx()
        val haloPx = strokePx * RingTuning.HaloStrokeScale * haloScale
        val haloAlphaFraction = RingTuning.HaloAlphaFraction * haloScale
        for (index in 0 until totalRings) {
            // 环间错峰 = 单循环时长 ÷ 每循环环数。
            val offset = index * (layer.cycleMillis.toFloat() / ringsPerCycle)
            val progress = (now - offset) / layer.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(RingTuning.FadeExp)
            if (alpha <= 0.005f) continue
            val radius = startRadius + travelPx * progress.easedOut(RingTuning.EaseExp)
            if (haloPx > 0f && haloAlphaFraction > 0f) {
                drawCircle(
                    color = accent,
                    alpha = alpha * haloAlphaFraction,
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
 * 窗口是整屏舞台，锚点直接用屏幕绝对坐标；行程 = 锚点到最远屏角的距离。
 */
@Composable
private fun FullRippleLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    colorSpec: ColorSpec,
    clock: () -> Float,
) {
    val spec = layer.spec as RippleSpec
    val accent = colorSpec.toMarkColor()
    val peakAlpha = layer.brightness.lerpInRange(RippleTuning.PeakAlphaMin, RippleTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val center = if (spec.centerAtBall) {
            Offset(anchor.centerX, anchor.centerY)
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }
        val startRadius = if (spec.centerAtBall) anchor.capsulePx / 2f * 0.9f else 0f
        val farthest = maxOf(
            (Offset.Zero - center).getDistance(),
            (Offset(size.width, 0f) - center).getDistance(),
            (Offset(0f, size.height) - center).getDistance(),
            (Offset(size.width, size.height) - center).getDistance(),
        )
        val travelPx = (farthest - startRadius).coerceAtLeast(0f)
        val strokePx = strokeWidthDp.dp.toPx()
        val haloPx = strokePx * RippleTuning.HaloStrokeScale
        for (index in 0 until layer.cycles) {
            val progress = (now - index * layer.cycleMillis) / layer.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(RippleTuning.FadeExp)
            if (alpha <= 0.005f) continue
            val radius = startRadius + travelPx * progress.easedOut(RippleTuning.EaseExp)
            drawCircle(
                color = accent,
                alpha = alpha * RippleTuning.HaloAlphaFraction,
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

/** AGSL 编译失败不连坐整窗：失败只记日志并跳过该层（否则构造异常会把窗口一起带走）。 */
private fun createShader(source: String, name: String): RuntimeShader? =
    runCatching { RuntimeShader(source) }
        .onFailure { Log.e(TAG, "shader $name compile FAILED", it) }
        .getOrNull()

/** 首尾淡入淡出包络：整段时长的 [share] 比例内从 0 升到 1、再从 1 落到 0。 */
private fun envelopeAt(now: Float, totalMillis: Int, share: Float): Float {
    val fade = (totalMillis * share).coerceAtLeast(1f)
    val inFraction = (now / fade).coerceIn(0f, 1f)
    val outFraction = ((totalMillis - now) / fade).coerceIn(0f, 1f)
    return minOf(inFraction, outFraction)
}
