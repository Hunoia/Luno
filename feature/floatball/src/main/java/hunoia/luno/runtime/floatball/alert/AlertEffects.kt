package hunoia.luno.runtime.floatball.alert

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.ParticleAlertStyle
import hunoia.luno.config.model.ParticleShape
import hunoia.luno.config.model.ParticleShapeSettings
import hunoia.luno.ui.theme.toMarkColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/**
 * 到达提示动效的舞台层绘制（全屏穿透窗内）：
 * 边缘（呼吸光 / 流光 / 波纹 / 烟花 / 双刃上升）、粒子（雨 / 喷泉 / 流星 / 彩带 / 萤火）、
 * 球心·全屏（光环 / 涟漪 / 球心爆发 / 球心光晕）。
 * 胶囊层的脉冲 / 边框流光在 [hunoia.luno.runtime.floatball.BallCapsuleWindow] 自己的 Compose 层。
 *
 * 所有层**共用一个时钟**（由 [AlertStageWindow] 驱动 [AlertPlayPlan.durationMillis]），
 * 各层只在 `Canvas` 的绘制阶段读 `clock()` —— 每帧只重绘不重组，也没有各自的生命周期。
 * 层序固定：边缘 → 粒子 → 球心·全屏（后者压在上面），与 [AlertStageLayers] 的遍历顺序一致。
 * 随机分布的层读取 [AlertPlayPlan.seed]：同一配置每次播放形态不同，测试传固定值复现。
 *
 * 用户旋钮随 [AlertLayerPlan] / [AlertEffectSpec] 下发（[buildPlan] 已夹取归一化），
 * 颜色走 [AlertPaletteColors]（每层按 [AlertLayerPlan.palette] 解析，单色回退 [colorSpec]）。
 */

private const val TAG = "AlertEffects"

/**
 * 各样式的随机种子基值；实际种子 = 基值 xor `AlertPlayPlan.seed`。
 * 基值保证不同样式形态不同，播放种子保证每次到达形态不同（测试传固定 seed 复现）。
 */
private const val RainSeed = 0x7A1A
private const val FountainSeed = 0xF04A
private const val MeteorSeed = 0x4E7E
private const val ConfettiSeed = 0xC0FF
private const val FireflySeed = 0xF1AE
private const val FireworksSeed = 0xF1AE0

/**
 * 调色板 AGSL 声明与取色助手：`paletteCount` + 5 色。
 *
 * `paletteAt` 在相邻两色之间**线性插值**（末色接回首色）：分桶取色会让 5 色在屏缘上
 * 读作 5 段硬色带，与「沿屏缘渐变」的取色口径不符；单色恒 `palette0`。
 */
private const val PALETTE_DECL = """
    uniform float paletteCount;
    layout(color) uniform half4 palette0;
    layout(color) uniform half4 palette1;
    layout(color) uniform half4 palette2;
    layout(color) uniform half4 palette3;
    layout(color) uniform half4 palette4;

    half4 paletteNth(float fi) {
        float i = mod(fi, max(paletteCount, 1.0));
        if (i < 0.5) return palette0;
        if (i < 1.5) return palette1;
        if (i < 2.5) return palette2;
        if (i < 3.5) return palette3;
        return palette4;
    }

    half4 paletteAt(float s) {
        if (paletteCount < 1.5) return palette0;
        float x = s * paletteCount;
        float fi = floor(x);
        half4 c0 = paletteNth(fi);
        half4 c1 = paletteNth(fi + 1.0);
        half f = half(x - fi);
        half g = half(1.0) - f;
        return c0 * g + c1 * f;
    }
"""

/** 圆角矩形周长参数化（顺时针，顶缘左端为 0，0..1）：边缘取色 / 流光定位共用。 */
private const val PERIMETER_FN = """
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
"""

/**
 * 屏幕边缘呼吸光的 AGSL 距离场 shader：到圆角矩形屏缘的符号距离做指数衰减，
 * 辉光连续贴合圆角。颜色按周长采样调色板（多色 = 沿边缘渐变）。
 */
private const val EDGE_GLOW_SHADER = """
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float falloff;
    uniform float outerFalloff;
    uniform float hardness;
    uniform float intensity;
    $PALETTE_DECL
    $PERIMETER_FN

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5;
        float2 p = fragCoord - halfSize;
        float2 q = abs(p) - (halfSize - float2(cornerRadius));
        float dist = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
        float inward = max(-dist, 0.0) / falloff;
        float outward = max(dist, 0.0) / outerFalloff;
        float glow = exp(-pow(inward, hardness)) * exp(-outward);
        float len = 4.0 * (halfSize.x - cornerRadius) + 4.0 * (halfSize.y - cornerRadius) +
            6.2831853 * cornerRadius;
        float s = perimeterParam(p, halfSize, cornerRadius) / max(len, 1.0);
        half4 col = paletteAt(s);
        half a = half(glow * intensity);
        return half4(col.rgb * a, a);
    }
"""

/**
 * 屏幕边缘流光的 AGSL shader：在呼吸光深度衰减的基础上，用圆角矩形**周长参数化**
 * 放一道沿屏缘环绕的高斯亮带；主带后随更宽更暗的拖尾。颜色按周长采样调色板；
 * 单色 + 双色模式走 [color0] / [color1] 回退（未选调色板时的主/次色）。
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
    $PALETTE_DECL
    $PERIMETER_FN

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
        half4 cB = paletteCount > 1.5 ? paletteAt(headPos) : palette0;
        outRgb += cB.rgb * aB;
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
            half4 c2 = paletteCount > 1.5
                ? paletteAt(headPos2)
                : (dualSecond > 0.5 ? color1 : palette0);
            outRgb += c2.rgb * a2;
            outA += a2;
        }
        return half4(outRgb, outA);
    }
"""

/**
 * 边缘波纹的 AGSL shader：圆角矩形周长参数化 + 等距波峰的高斯条带。
 *
 * 与 CPU 回退共用同一份参数口径：波峰数、sigma（波峰宽度）、行进位置。
 * 用「到最近波峰的周长距离」解析式，不写循环（`x = (s - head) * N` 取最近整数）。
 */
private const val EDGE_RIPPLE_SHADER = """
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float inset;
    uniform float crestCount;
    uniform float sigma;
    uniform float headPos;
    uniform float bandWidth;
    uniform float intensity;
    $PALETTE_DECL
    $PERIMETER_FN

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5 - float2(inset);
        float2 p = fragCoord - resolution * 0.5;
        float2 q = abs(p) - (halfSize - float2(cornerRadius));
        float dist = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
        float inward = max(-dist, 0.0) / max(bandWidth, 1.0);
        float outward = max(dist, 0.0) / max(bandWidth * 0.5, 1.0);
        float depth = exp(-pow(inward, 1.5)) * exp(-outward);
        float len = 4.0 * (halfSize.x - cornerRadius) + 4.0 * (halfSize.y - cornerRadius) +
            6.2831853 * cornerRadius;
        float s = perimeterParam(p, halfSize, cornerRadius) / max(len, 1.0);
        float x = (s - headPos) * max(crestCount, 1.0);
        float nearest = floor(x + 0.5);
        float minD = abs(x - nearest) / max(crestCount, 1.0);
        float sn = minD / max(sigma, 1e-5);
        float wave = exp(-0.5 * sn * sn);
        half a = half(wave * depth * intensity);
        half4 col = paletteAt(s);
        return half4(col.rgb * a, a);
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
                val palette = resolvePalette(layer, colorSpec)
                val seed = plan.seed
                when (val style = layer.style) {
                    is EdgeAlertStyle -> when (style) {
                        EdgeAlertStyle.EdgeGlow -> EdgeGlowLayer(layer, palette, clock)
                        EdgeAlertStyle.EdgeFlow -> EdgeFlowLayer(
                            layer = layer,
                            palette = palette,
                            secondColorSpec = secondColorSpec,
                            clock = clock,
                        )
                        EdgeAlertStyle.EdgeRipple -> EdgeRippleLayer(layer, palette, clock)
                        EdgeAlertStyle.EdgeFireworks -> EdgeFireworksLayer(layer, palette, clock, seed)
                        EdgeAlertStyle.EdgeBladeRise -> EdgeBladeRiseLayer(layer, palette, clock)
                    }

                    is ParticleAlertStyle -> when (style) {
                        ParticleAlertStyle.TopRain -> TopRainLayer(layer, palette, clock, seed)
                        ParticleAlertStyle.BottomFountain ->
                            BottomFountainLayer(layer, palette, clock, seed)
                        ParticleAlertStyle.Meteor -> MeteorLayer(layer, palette, clock, seed)
                        ParticleAlertStyle.Confetti -> ConfettiLayer(layer, palette, clock, seed)
                        ParticleAlertStyle.Firefly -> FireflyLayer(layer, palette, clock, seed)
                    }

                    is AnchorAlertStyle -> when (style) {
                        AnchorAlertStyle.Ring -> BallRingLayer(layer, anchor, palette, clock)
                        AnchorAlertStyle.Ripple -> FullRippleLayer(layer, anchor, palette, clock)
                        AnchorAlertStyle.BallBurst -> BallBurstLayer(layer, anchor, palette, clock, seed)
                        AnchorAlertStyle.BallHalo -> BallHaloLayer(layer, anchor, palette, clock)
                    }

                    is CapsuleAlertStyle -> Unit
                }
            }
        }
    }
}

/** 层 → 调色板：选了调色板用 [AlertPaletteSpec.toColors]，否则单色回退 [colorSpec]。 */
@Composable
private fun resolvePalette(layer: AlertLayerPlan, colorSpec: ColorSpec): AlertPaletteColors {
    val fallback = colorSpec.toMarkColor()
    return layer.palette?.toColors(fallback) ?: AlertPaletteColors(listOf(fallback), rainbow = false)
}

/**
 * 屏幕边缘呼吸光：alpha 按 sin 包络呼吸一圈，强度映射 alpha 峰值，光带宽度是用户旋钮。
 * 颜色按周长采样（多色 = 沿边缘渐变）。绘制走 [EDGE_GLOW_SHADER]，仅播放期每帧求值。
 */
@Composable
private fun EdgeGlowLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeGlowSpec
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
        setPaletteUniforms(activeShader, palette)
        paint.shader = activeShader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/**
 * 屏幕边缘流光：亮带沿屏缘匀速环绕（每循环一圈），深度衰减口径与呼吸光一致。
 * 刃感 = 主带宽（`coreBand`）+ 深度硬度（`hardness`）+ 拖尾浓度（`trailAlpha`）三件套；
 * 双带时第二带反向行进。颜色按周长采样调色板（单色 + 双色回退 [secondColorSpec]）。
 */
@Composable
private fun EdgeFlowLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    secondColorSpec: ColorSpec,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeFlowSpec
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
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
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
        // 单色回退对：主色 + 次色（未选调色板且双色时）。
        val accent = palette.colors[0]
        activeShader.setColorUniform("color0", accent.copy(alpha = 1f).toArgb())
        activeShader.setColorUniform("color1", secondAccent.copy(alpha = 1f).toArgb())
        setPaletteUniforms(activeShader, palette)
        paint.shader = activeShader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/** 一颗粒子的静态参数：横向位置、出发延迟、横向漂移、半径、摆动相位、取色序号、方差抖动。 */
private data class Particle(
    val xNorm: Float,
    val delayMs: Float,
    val driftNorm: Float,
    val radiusDp: Float,
    val phase: Float,
    val colorIndex: Int,
    val sizeJitter: Float,
    val alphaJitter: Float,
)

/**
 * 顶部雨：粒子从绘制带顶缘错峰出发，重力加速下落（重力指数是用户旋钮），
 * 下落途中横向正弦摆动（摆幅旋钮）+ 风偏移 + 阻力；双层圆点辉光（实心核 + 低 alpha 光晕）；
 * alpha 先驻留后衰减。颜色按粒子序号采样调色板。
 */
@Composable
private fun TopRainLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as ParticlesSpec
    val peakAlpha = layer.brightness
        .lerpInRange(ParticlesTuning.PeakAlphaMin, ParticlesTuning.PeakAlphaMax)
    val image = rememberParticleImage(spec, layer.appPackage)
    val count = layer.amount
        .lerpInRange(
            FloatBallDefaults.AlertParticlesCountMin.toFloat(),
            FloatBallDefaults.AlertParticlesCountMax.toFloat(),
        )
        .roundToInt()
    val lifetimeMs = layer.cycleMillis * ParticlesTuning.LifeShare
    val spreadMs = (layer.totalMillis - lifetimeMs).coerceAtLeast(1f)
    val particles = remember(layer, seed) {
        val random = Random(RainSeed xor seed)
        List(count) { index ->
            val hero = random.nextFloat() < ParticlesTuning.HeroFraction
            Particle(
                xNorm = 0.03f + random.nextFloat() * 0.94f,
                delayMs = random.nextFloat() * spreadMs,
                driftNorm = (random.nextFloat() - 0.5f) * ParticlesTuning.DriftNorm,
                radiusDp = (ParticlesTuning.RadiusMinDp +
                    random.nextFloat() * (ParticlesTuning.RadiusMaxDp - ParticlesTuning.RadiusMinDp))
                    .let { radius -> if (hero) radius * ParticlesTuning.HeroRadiusScale else radius },
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
                colorIndex = index,
                sizeJitter = varianceScale(spec.variance, random.nextFloat()),
                alphaJitter = varianceScale(spec.variance, random.nextFloat()),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val w = size.width
        val travelPx = size.height *
            travelNorm(spec.travelPercent, ParticlesTuning.TravelBasisRain)
        val fadeExp = ParticlesTuning.FadeExp
        val swayAmpPx = ParticlesTuning.SwayAmpNorm * spec.swayScale * w
        for (particle in particles) {
            val progress = (now - particle.delayMs) / lifetimeMs
            if (progress <= 0f || progress >= 1f) continue
            val eased = dragEase(progress, spec.drag)
            val alpha = peakAlpha * (1f - progress).pow(fadeExp) *
                particle.alphaJitter * envelope
            if (alpha <= 0.005f) continue
            val fall = eased.pow(spec.gravityExp)
            val sway = sin(
                2f * Math.PI.toFloat() * progress * ParticlesTuning.SwayCycles + particle.phase,
            ) * swayAmpPx
            val rainRadius = (particle.radiusDp * spec.sizeScale * particle.sizeJitter).dp.toPx()
            val accent = palette.at(particle.colorIndex)
            val center = Offset(
                x = (particle.xNorm + particle.driftNorm * progress) * w +
                    sway + windOffsetNorm(spec.wind, eased) * w,
                y = fall * travelPx,
            )
            if (image != null) {
                drawMote(
                    image = image,
                    color = accent.copy(alpha = alpha),
                    center = center,
                    radiusPx = rainRadius,
                    rotationDeg = 0f,
                    tint = spec.tintImage && spec.shape == ParticleShape.CustomImage,
                )
            } else {
                drawCircle(
                    color = accent,
                    alpha = alpha * ParticlesTuning.HaloAlphaFraction,
                    radius = rainRadius * ParticlesTuning.HaloScale,
                    center = center,
                )
                drawCircle(color = accent, alpha = alpha, radius = rainRadius, center = center)
            }
        }
    }
}

/**
 * 球光环扩散（雷达 ping）：从胶囊收起圆的边缘向外**减速扩张**并淡出的圆环；
 * 核环 + 宽光晕双描边；每循环发 [RingSpec.ringsPerCycle] 圈错峰重叠。
 * 颜色按角度采样调色板。
 */
@Composable
private fun BallRingLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as RingSpec
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
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val totalRings = layer.cycles * ringsPerCycle
        val strokePx = strokeWidthDp.dp.toPx()
        val haloPx = strokePx * RingTuning.HaloStrokeScale * haloScale
        val haloAlphaFraction = RingTuning.HaloAlphaFraction * haloScale
        for (index in 0 until totalRings) {
            val offset = index * (layer.cycleMillis.toFloat() / ringsPerCycle)
            val progress = (now - offset) / layer.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(RingTuning.FadeExp) * envelope
            if (alpha <= 0.005f) continue
            val radius = startRadius + travelPx * progress.easedOut(RingTuning.EaseExp)
            val accent = palette.at(index)
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
 * 抵达屏缘并淡出的水波纹；每循环一道波，核环 + 宽光晕双描边。
 * 颜色按角度采样调色板。
 */
@Composable
private fun FullRippleLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as RippleSpec
    val peakAlpha = layer.brightness.lerpInRange(RippleTuning.PeakAlphaMin, RippleTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
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
        val haloScale = spec.haloScale.coerceIn(0f, 2f)
        val haloPx = strokePx * RippleTuning.HaloStrokeScale * haloScale
        val haloAlphaFraction = RippleTuning.HaloAlphaFraction * haloScale
        for (index in 0 until layer.cycles) {
            val progress = (now - index * layer.cycleMillis) / layer.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = peakAlpha * (1f - progress).pow(RippleTuning.FadeExp) * envelope
            if (alpha <= 0.005f) continue
            val radius = startRadius + travelPx * progress.easedOut(RippleTuning.EaseExp)
            val accent = palette.at(index)
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
 * 圆角矩形周长的采样器：沿屏缘（内缩 [inset]）均匀取点，顺时针、顶缘左端为 0。
 * 各边缘效果共用，把「沿边缘」效果画在屏缘而不是屏幕中心。
 */
private class EdgePerimeter(
    private val size: androidx.compose.ui.geometry.Size,
    private val inset: Float,
    cornerRadius: Float,
) {
    private val w = size.width - inset * 2
    private val h = size.height - inset * 2
    private val r = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
    private val sx = (w / 2f - r).coerceAtLeast(0f)
    private val sy = (h / 2f - r).coerceAtLeast(0f)
    private val arc = (Math.PI.toFloat() / 2f) * r
    private val total: Float = 4f * sx + 4f * sy + 2f * Math.PI.toFloat() * r

    fun point(fraction: Float): Offset {
        val x = ((fraction - fraction.toInt()).let { if (it < 0f) it + 1f else it })
        var d = x * total
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset
        val seg0 = 2f * sx
        if (d < seg0) return Offset(left + r + d, top)
        d -= seg0
        if (d < arc) {
            val a = -Math.PI.toFloat() / 2f + d / arc * (Math.PI.toFloat() / 2f)
            return Offset(right - r + r * cos(a), top + r + r * sin(a))
        }
        d -= arc
        val seg2 = 2f * sy
        if (d < seg2) return Offset(right, top + r + d)
        d -= seg2
        if (d < arc) {
            val a = d / arc * (Math.PI.toFloat() / 2f)
            return Offset(right - r + r * cos(a), bottom - r + r * sin(a))
        }
        d -= arc
        val seg4 = 2f * sx
        if (d < seg4) return Offset(right - r - d, bottom)
        d -= seg4
        if (d < arc) {
            val a = Math.PI.toFloat() / 2f + d / arc * (Math.PI.toFloat() / 2f)
            return Offset(left + r + r * cos(a), bottom - r + r * sin(a))
        }
        d -= arc
        val seg6 = 2f * sy
        if (d < seg6) return Offset(left, bottom - r - d)
        d -= seg6
        val a = Math.PI.toFloat() + (d / arc).coerceIn(0f, 1f) * (Math.PI.toFloat() / 2f)
        return Offset(left + r + r * cos(a), top + r + r * sin(a))
    }
}

/** 归一化周长差：环面最小距离（0..0.5）。 */
private fun perimeterDist(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 1f
    return minOf(d, 1f - d)
}

/**
 * 边缘波纹：多道波峰沿屏缘匀速环行。主体走 AGSL 连续距离场（波峰间距均分、
 * 共速环行，[EdgeRippleSpec.crestCount] 个）；shader 编译失败时回退沿周长采样点阵。
 */
@Composable
private fun EdgeRippleLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeRippleSpec
    val peakAlpha = layer.brightness.lerpInRange(
        EdgeRippleTuning.PeakAlphaMin,
        EdgeRippleTuning.PeakAlphaMax,
    )
    val sizeDp = layer.amount
        .lerpInRange(
            FloatBallDefaults.AlertEdgeRippleSizeDpMin.toFloat(),
            FloatBallDefaults.AlertEdgeRippleSizeDpMax.toFloat(),
        )
    // 波纹主体走 AGSL（连续距离场，无采样步进）；编译失败时回退 CPU 点阵。
    val shader = remember { createShader(EDGE_RIPPLE_SHADER, "edgeRipple") }
    val paint = remember { Paint() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val crests = spec.crestCount
        val travel = (now / layer.cycleMillis) % 1f
        val insetPx = EdgeFrameTuning.InsetDp.dp.toPx()
        if (shader != null) {
            val sigma = (EdgeRippleTuning.Sigma * (sizeDp / 6f)).coerceIn(0.01f, 0.12f)
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("cornerRadius", spec.cornerRadiusDp.dp.toPx())
            shader.setFloatUniform("inset", insetPx)
            shader.setFloatUniform("crestCount", crests.toFloat())
            shader.setFloatUniform("sigma", sigma)
            shader.setFloatUniform("headPos", travel)
            shader.setFloatUniform("bandWidth", sizeDp.dp.toPx())
            shader.setFloatUniform("intensity", peakAlpha * envelope)
            setPaletteUniforms(shader, palette)
            paint.shader = shader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
            return@Canvas
        }
        // CPU 回退：沿周长采样，取各波峰的高斯贡献（sigma 与 shader 同口径）。
        val perim = EdgePerimeter(size, insetPx, spec.cornerRadiusDp.dp.toPx())
        val sizeR = sizeDp.dp.toPx()
        val sigma = (EdgeRippleTuning.Sigma * (sizeDp / 6f)).coerceIn(0.01f, 0.12f)
        val samples = EdgeRippleTuning.FallbackSamples
        for (i in 0 until samples) {
            val f = i.toFloat() / samples
            var wave = 0f
            for (c in 0 until crests) {
                val crestPos = (c.toFloat() / crests + travel) % 1f
                val dist = perimeterDist(f, crestPos)
                wave = maxOf(wave, exp(-dist * dist / (2f * sigma * sigma)))
            }
            if (wave <= 0.01f) continue
            val alpha = peakAlpha * wave * envelope
            val radius = sizeR * (0.5f + 0.5f * wave)
            drawCircle(
                color = palette.sample(f),
                alpha = alpha,
                radius = radius,
                center = perim.point(f),
            )
        }
    }
}

/**
 * 屏缘烟花：随机锚点（种子固定）各发一簇粒子向内迸射，随行程减速并淡出。
 * 行程由 [layer.amount] 映射；每循环一簇，簇间错峰。
 */
@Composable
private fun EdgeFireworksLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as EdgeFireworksSpec
    val peakAlpha = layer.brightness.lerpInRange(
        EdgeFireworksTuning.PeakAlphaMin,
        EdgeFireworksTuning.PeakAlphaMax,
    )
    val particles = remember(layer, seed) {
        val random = Random(FireworksSeed xor seed)
        val perBurst = EdgeFireworksTuning.PerBurst
        List(spec.burstCount) { b ->
            val anchor = random.nextFloat()
            List(perBurst) { i ->
                val angle = Math.PI.toFloat() * (0.75f + 1.5f * (i.toFloat() / (perBurst - 1))) +
                    (random.nextFloat() - 0.5f) * EdgeFireworksTuning.AngleJitter
                BurstParticle(
                    anchor = anchor,
                    angle = angle,
                    speed = EdgeFireworksTuning.SpeedMin +
                        random.nextFloat() *
                        (EdgeFireworksTuning.SpeedMax - EdgeFireworksTuning.SpeedMin),
                    delay = b * (1f / spec.burstCount) * EdgeFireworksTuning.DelaySpread +
                        random.nextFloat() * EdgeFireworksTuning.DelayJitter,
                    radiusDp = spec.particleSizeDp * (EdgeFireworksTuning.RadiusMinScale +
                        random.nextFloat() *
                        (EdgeFireworksTuning.RadiusMaxScale - EdgeFireworksTuning.RadiusMinScale)),
                    colorIndex = b * perBurst + i,
                )
            }
        }.flatten()
    }
    val spreadPx = layer.amount
        .lerpInRange(
            FloatBallDefaults.AlertEdgeFireworksSpreadDpMin.toFloat(),
            FloatBallDefaults.AlertEdgeFireworksSpreadDpMax.toFloat(),
        )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val perim = EdgePerimeter(size, EdgeFrameTuning.InsetDp.dp.toPx(), spec.cornerRadiusDp.dp.toPx())
        val travelPx = spreadPx.dp.toPx()
        for (p in particles) {
            val progress = (now / layer.cycleMillis - p.delay) % 1f
            if (progress <= 0f || progress >= 1f) continue
            val ease = progress.easedOut(EdgeFireworksTuning.EaseExp)
            val alpha = peakAlpha * (1f - progress) * envelope
            if (alpha <= 0.005f) continue
            val anchorP = perim.point(p.anchor)
            val dir = Offset(cos(p.angle), sin(p.angle))
            val center = Offset(
                anchorP.x + dir.x * travelPx * ease * p.speed,
                anchorP.y + dir.y * travelPx * ease * p.speed,
            )
            drawCircle(
                color = palette.at(p.colorIndex),
                alpha = alpha,
                radius = p.radiusDp.dp.toPx() * (1f - EdgeFireworksTuning.SizeShrink * progress),
                center = center,
            )
        }
    }
}

/** 屏缘烟花的一颗粒子：锚点（周长分数）、迸射角、速率、延迟、半径、取色序号。 */
private data class BurstParticle(
    val anchor: Float,
    val angle: Float,
    val speed: Float,
    val delay: Float,
    val radiusDp: Float,
    val colorIndex: Int,
)

/**
 * 双刃上升：左右两缘各一道竖直亮刃自下而上反复扫过，刃尖在上、尾迹向下渐隐。
 * 刃宽由 [layer.amount] 映射；扫动位置按循环推进（连续跨循环）。
 */
@Composable
private fun EdgeBladeRiseLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeBladeRiseSpec
    val peakAlpha = layer.brightness.lerpInRange(
        EdgeBladeTuning.PeakAlphaMin,
        EdgeBladeTuning.PeakAlphaMax,
    )
    val bladeWidth = layer.amount
        .lerpInRange(
            FloatBallDefaults.AlertEdgeBladeWidthDpMin.toFloat(),
            FloatBallDefaults.AlertEdgeBladeWidthDpMax.toFloat(),
        )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val progress = (now / layer.cycleMillis) % 1f
        val bladeW = bladeWidth.dp.toPx()
        val bladeH = spec.bladeHeightDp.dp.toPx()
        val headY = progress * (size.height + bladeH) - bladeH / 2f
        // 每侧一道刃：顶部最亮（刃尖），向下线性衰减到尾迹。
        for (side in intArrayOf(0, 1)) {
            val x = if (side == 0) bladeW / 2f else size.width - bladeW / 2f
            val steps = EdgeBladeTuning.Steps
            for (i in 0 until steps) {
                val dy = -bladeH / 2f + bladeH * (i.toFloat() / steps)
                val y = headY + dy
                if (y < 0f || y > size.height) continue
                val fall = 1f - (i.toFloat() / steps)
                val alpha = peakAlpha * fall.pow(EdgeBladeTuning.FadeExp) * envelope
                if (alpha <= 0.005f) continue
                val f = y / size.height
                drawCircle(
                    color = palette.sample(f),
                    alpha = alpha,
                    radius = bladeW *
                        (EdgeBladeTuning.RadiusScale +
                            EdgeBladeTuning.RadiusFallScale * fall),
                    center = Offset(x, y),
                )
            }
        }
    }
}

/**
 * 球心爆发：从胶囊球心向外径向迸射的粒子爆，减速扩张 + 淡出。
 * 每循环一次；行程由 [layer.amount] 映射；颜色按粒子序号采样。
 */
@Composable
private fun BallBurstLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as BallBurstSpec
    val peakAlpha = layer.brightness.lerpInRange(
        BallBurstTuning.PeakAlphaMin,
        BallBurstTuning.PeakAlphaMax,
    )
    val particles = remember(layer, seed) {
        val random = Random(0xB0A5 xor seed)
        List(spec.count) { index ->
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            BallBurstParticle(
                angle = angle,
                speed = BallBurstTuning.SpeedMin +
                    random.nextFloat() *
                    (BallBurstTuning.SpeedMax - BallBurstTuning.SpeedMin),
                radiusDp = spec.particleSizeDp * (BallBurstTuning.RadiusMinScale +
                    random.nextFloat() *
                    (BallBurstTuning.RadiusMaxScale - BallBurstTuning.RadiusMinScale)),
                colorIndex = index,
            )
        }
    }
    val spreadPx = layer.amount
        .lerpInRange(
            FloatBallDefaults.AlertBallBurstSpreadDpMin.toFloat(),
            FloatBallDefaults.AlertBallBurstSpreadDpMax.toFloat(),
        )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val progress = (now / layer.cycleMillis) % 1f
        if (progress <= 0f || progress >= 1f) return@Canvas
        val center = Offset(anchor.centerX, anchor.centerY)
        val travelPx = spreadPx.dp.toPx()
        val ease = progress.easedOut(BallBurstTuning.EaseExp)
        for (p in particles) {
            val alpha = peakAlpha * (1f - progress) * envelope
            if (alpha <= 0.005f) continue
            val dir = Offset(cos(p.angle), sin(p.angle))
            val c = Offset(
                center.x + dir.x * travelPx * ease * p.speed,
                center.y + dir.y * travelPx * ease * p.speed,
            )
            drawCircle(
                color = palette.at(p.colorIndex),
                alpha = alpha,
                radius = p.radiusDp.dp.toPx() * (1f - BallBurstTuning.SizeShrink * progress),
                center = c,
            )
        }
    }
}

/** 球心爆发的一颗粒子：迸射角、速率、半径、取色序号。 */
private data class BallBurstParticle(
    val angle: Float,
    val speed: Float,
    val radiusDp: Float,
    val colorIndex: Int,
)

/**
 * 球心光晕：胶囊球心处的柔和径向辉光，随呼吸明灭并轻微缩放。
 * 半径由 [layer.amount]（球径 %）映射；光晕强度走 [BallHaloSpec.haloScale]。
 */
@Composable
private fun BallHaloLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as BallHaloSpec
    val peakAlpha = layer.brightness.lerpInRange(
        BallHaloTuning.PeakAlphaMin,
        BallHaloTuning.PeakAlphaMax,
    )
    val baseRadius = anchor.capsulePx / 2f * (spec.radiusPercent / 100f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val t = (now / layer.cycleMillis) % 1f
        val breath = 0.5f + 0.5f * sin(2f * Math.PI.toFloat() * t)
        val radius = (baseRadius *
            (BallHaloTuning.RadiusMinScale + BallHaloTuning.RadiusBreathScale * breath))
            .coerceAtLeast(1f)
        val center = Offset(anchor.centerX, anchor.centerY)
        val peak = peakAlpha * (BallHaloTuning.AlphaFloor +
            BallHaloTuning.AlphaBreath * breath) * envelope
        val haloScale = spec.haloScale.coerceIn(0.2f, 2f)
        // 多色：首 / 中 / 尾三色做径向 stop（单色退化为同色，与旧观感一致）。
        val ring = palette.colors
        val inner = ring.first()
        val mid = if (ring.size > 1) ring[ring.size / 2] else inner
        val outerStop = if (ring.size > 2) ring.last() else mid
        // 柔和径向辉光：中心亮 → 边缘透明，避免硬边圆盘。
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    inner.copy(alpha = peak),
                    mid.copy(alpha = peak * BallHaloTuning.MidAlphaFraction),
                    outerStop.copy(alpha = 0f),
                ),
                center = center,
                radius = radius * haloScale,
            ),
            radius = radius * haloScale,
            center = center,
        )
    }
}

/**
 * 一颗粒子（④ 多运动共用）：起点/速度归一化、延迟、半径（dp）、相位、取色序号、自转角速度。
 * 各运动在生成时按自己的随机分布填；绘制时按运动语义求位置与形状。
 */
private data class Mote(
    val xNorm: Float,
    val yNorm: Float,
    val vx: Float,
    val vy: Float,
    val delay: Float,
    val radiusDp: Float,
    val phase: Float,
    val colorIndex: Int,
    val spin: Float,
    val sizeJitter: Float = 1f,
    val alphaJitter: Float = 1f,
)

/** 粒子层公共读数：数量由 [AlertLayerPlan.amount] 映射，亮度映射 alpha 峰值。 */
private fun moteCount(layer: AlertLayerPlan): Int = layer.amount
    .lerpInRange(
        FloatBallDefaults.AlertParticlesCountMin.toFloat(),
        FloatBallDefaults.AlertParticlesCountMax.toFloat(),
    )
    .roundToInt()

private fun motePeak(layer: AlertLayerPlan): Float =
    layer.brightness.lerpInRange(ParticlesTuning.PeakAlphaMin, ParticlesTuning.PeakAlphaMax)

/**
 * 形状渲染：圆点 / 自定义图片 / 应用图标（[image] 由层侧 remember 加载，失败回落圆点）。
 *
 * - [tint] 只对自定义图片生效（跟随调色板）；应用图标始终保留品牌原色。
 * - 图片按原始宽高比装在 `radiusPx * 2` 的方框内，不再拉伸成正方形。
 * - [horizontalScale] 用于彩带的 3D 翻面（1 = 正面）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMote(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    color: androidx.compose.ui.graphics.Color,
    center: Offset,
    radiusPx: Float,
    rotationDeg: Float,
    tint: Boolean = false,
    horizontalScale: Float = 1f,
) {
    if (image != null) {
        rotate(rotationDeg, pivot = center) {
            val box = radiusPx * 2f
            val aspect = image.width.toFloat() / image.height.toFloat().coerceAtLeast(1f)
            val width = if (aspect >= 1f) box else box * aspect
            val height = if (aspect >= 1f) box / aspect else box
            val drawW = (width * horizontalScale.coerceIn(0.05f, 1f)).coerceAtLeast(1f)
            drawImage(
                image = image,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(image.width, image.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (center.x - drawW / 2f).roundToInt(),
                    (center.y - height / 2f).roundToInt(),
                ),
                dstSize = androidx.compose.ui.unit.IntSize(
                    drawW.roundToInt(),
                    height.roundToInt(),
                ),
                alpha = color.alpha,
                colorFilter = if (tint) {
                    androidx.compose.ui.graphics.ColorFilter.tint(color)
                } else {
                    null
                },
            )
        }
    } else {
        drawCircle(color = color, radius = radiusPx * horizontalScale, center = center)
    }
}

/**
 * 形状图片的解码上限（px，长边）：粒子核最多几十像素，按 128 解码足够清晰，
 * 又不至于把一张 12MP 原图整张解进内存（ARGB 一格 ≈ 12MP × 4B ≈ 48MB）。
 */
private const val ParticleImageMaxPx = 128

/**
 * 形状图片的进程内缓存：同一次播放里每个粒子层都会取同一张；键 = 形状 + 图片名 + 来源包。
 * 条目很少（≤ 形状数 × 来源），容量 8 足够，避免整段动效期间反复解码。
 */
private val particleImageCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(8)

private fun particleImageKey(spec: ParticlesSpec, appPackage: String?): String =
    "${spec.shape}|${spec.customImage}|${appPackage.orEmpty()}"

/**
 * 粒子形状 → 位图：自定义图片 / 通知应用图标，**后台解码 + 降采样**，失败回落 null（圆点）。
 *
 * 解码不能放在 `remember` 里同步做：那是在主线程解一张全尺寸图，首帧直接卡住，
 * 大图还会 OOM。命中缓存时 `remember` 的初始值就是图，避免重播时闪一帧圆点。
 */
@Composable
private fun rememberParticleImage(
    spec: ParticlesSpec,
    appPackage: String?,
): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val key = particleImageKey(spec, appPackage)
    var image by remember(key) { mutableStateOf(particleImageCache.get(key)) }
    LaunchedEffect(key) {
        if (spec.shape == ParticleShape.Dot) {
            image = null
            return@LaunchedEffect
        }
        image = decodeParticleImage(context, spec, appPackage)
    }
    return if (spec.shape == ParticleShape.Dot) null else image
}

/** 后台解码并按 [ParticleImageMaxPx] 降采样；结果写入 [particleImageCache]。 */
private suspend fun decodeParticleImage(
    context: Context,
    spec: ParticlesSpec,
    appPackage: String?,
): androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
    val key = particleImageKey(spec, appPackage)
    particleImageCache.get(key)?.let { return@withContext it }
    val decoded = runCatching {
        when (spec.shape) {
            ParticleShape.Dot -> null
            ParticleShape.CustomImage -> decodeShapeFile(context, spec.customImage)
            ParticleShape.AppIcon -> decodeAppIcon(context, appPackage)
        }
    }.getOrNull()
    if (decoded != null) particleImageCache.put(key, decoded)
    decoded
}

/** 自定义形状文件：新数据只存文件名（文件在 `ParticleShapeSettings.ImageDirName` 下）；旧数据可能是绝对路径。 */
private fun decodeShapeFile(context: Context, stored: String): androidx.compose.ui.graphics.ImageBitmap? {
    val file = resolveShapeFile(context, stored)?.takeIf { it.isFile } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}

private fun resolveShapeFile(context: Context, stored: String): File? {
    val raw = stored.removePrefix("file://")
    if (raw.isBlank()) return null
    return if (raw.contains('/')) File(raw)
    else File(File(context.filesDir, ParticleShapeSettings.ImageDirName), raw)
}

private fun sampleSizeFor(width: Int, height: Int): Int {
    var maxEdge = maxOf(width, height)
    var sample = 1
    while (maxEdge / 2 >= ParticleImageMaxPx) {
        maxEdge /= 2
        sample *= 2
    }
    return sample
}

private fun decodeAppIcon(context: Context, appPackage: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val pkg = appPackage ?: return null
    val drawable = context.packageManager.getApplicationIcon(pkg)
    val intrinsic = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
    val scale = (ParticleImageMaxPx.toFloat() / intrinsic).coerceAtMost(1f)
    val width = (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val height = (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * 底部喷泉：粒子从底缘向上喷出，上冲到顶点后被重力拉回（对称二次弧），横向微漂。
 * 颜色按粒子序号采样；风横向推开、阻力收慢喷射、方差抖动尺寸 / alpha。
 */
@Composable
private fun BottomFountainLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as ParticlesSpec
    val peak = motePeak(layer)
    val image = rememberParticleImage(spec, layer.appPackage)
    val motes = remember(layer, seed) {
        val random = Random(FountainSeed xor seed)
        List(moteCount(layer)) { index ->
            Mote(
                xNorm = random.nextFloat(),
                yNorm = 1.05f,
                vx = (random.nextFloat() - 0.5f) * ParticlesTuning.FountainSideVx,
                vy = -(ParticlesTuning.FountainRiseMin +
                    random.nextFloat() *
                    (ParticlesTuning.FountainRiseMax - ParticlesTuning.FountainRiseMin)),
                delay = random.nextFloat(),
                radiusDp = (ParticlesTuning.RadiusMinDp +
                    random.nextFloat() * (ParticlesTuning.RadiusMaxDp - ParticlesTuning.RadiusMinDp)) *
                    spec.sizeScale,
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
                colorIndex = index,
                spin = 0f,
                sizeJitter = varianceScale(spec.variance, random.nextFloat()),
                alphaJitter = varianceScale(spec.variance, random.nextFloat()),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val travelPx = size.height *
            travelNorm(spec.travelPercent, ParticlesTuning.TravelBasisFountain)
        val spread = spec.spreadScale
        for (m in motes) {
            val t = ((now / layer.cycleMillis) - m.delay) % 1f
            if (t <= 0f || t >= 1f) continue
            val eased = dragEase(t, spec.drag)
            val alpha = peak * (1f - ParticlesTuning.FountainFade * t) *
                m.alphaJitter * envelope
            if (alpha <= 0.005f) continue
            // 真喷泉：上冲到顶点再被重力拉回（二次弧，0→峰→0），与顶部雨（只落不升）拉开。
            val arc = 4f * eased * (1f - eased)
            val height = travelPx * (-m.vy) * ParticlesTuning.FountainHeightScale
            val y = m.yNorm * size.height - arc * height
            val x = (m.xNorm + m.vx * t * 2f * spread) * size.width +
                windOffsetNorm(spec.wind, eased) * size.width
            drawMote(
                image = image,
                color = palette.at(m.colorIndex).copy(alpha = alpha),
                center = Offset(x, y),
                radiusPx = m.radiusDp.dp.toPx() * m.sizeJitter,
                rotationDeg = 0f,
                tint = spec.tintImage && spec.shape == ParticleShape.CustomImage,
            )
        }
    }
}

/** 流星：带拖尾的亮点斜向下划过，拖尾沿速度反方向渐隐。 */
@Composable
private fun MeteorLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as ParticlesSpec
    val peak = motePeak(layer)
    val image = rememberParticleImage(spec, layer.appPackage)
    val motes = remember(layer, seed) {
        val random = Random(MeteorSeed xor seed)
        List(moteCount(layer)) { index ->
            Mote(
                xNorm = random.nextFloat(),
                yNorm = ParticlesTuning.MeteorStartYMin +
                    random.nextFloat() * ParticlesTuning.MeteorStartYRange,
                vx = -(ParticlesTuning.MeteorVxMin +
                    random.nextFloat() *
                    (ParticlesTuning.MeteorVxMax - ParticlesTuning.MeteorVxMin)),
                vy = ParticlesTuning.MeteorVyMin +
                    random.nextFloat() *
                    (ParticlesTuning.MeteorVyMax - ParticlesTuning.MeteorVyMin),
                delay = random.nextFloat(),
                radiusDp = (ParticlesTuning.RadiusMinDp +
                    random.nextFloat() * ParticlesTuning.MeteorRadiusExtraDp) * spec.sizeScale,
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
                colorIndex = index,
                spin = 0f,
                sizeJitter = varianceScale(spec.variance, random.nextFloat()),
                alphaJitter = varianceScale(spec.variance, random.nextFloat()),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val travelPx = size.height *
            travelNorm(spec.travelPercent, ParticlesTuning.TravelBasisMeteor)
        val tail = spec.tailLengthDp.dp.toPx()
        for (m in motes) {
            val t = ((now / layer.cycleMillis) - m.delay) % 1f
            if (t <= 0f || t >= 1f) continue
            val eased = dragEase(t, spec.drag)
            val alpha = peak * (1f - t) * m.alphaJitter * envelope
            if (alpha <= 0.005f) continue
            val x = (m.xNorm + m.vx * eased) * size.width +
                windOffsetNorm(spec.wind, eased) * size.width
            val y = (m.yNorm + m.vy * eased) * travelPx
            val center = Offset(x, y)
            val dir = Offset(m.vx, m.vy)
            val len = kotlin.math.hypot(dir.x, dir.y).coerceAtLeast(1e-3f)
            val tailEnd = Offset(center.x - dir.x / len * tail, center.y - dir.y / len * tail)
            drawLine(
                color = palette.at(m.colorIndex).copy(
                    alpha = alpha * ParticlesTuning.MeteorTailAlpha,
                ),
                start = tailEnd,
                end = center,
                strokeWidth = m.radiusDp.dp.toPx() * m.sizeJitter * 0.6f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawMote(
                image = image,
                color = palette.at(m.colorIndex).copy(alpha = alpha),
                center = center,
                radiusPx = m.radiusDp.dp.toPx() * m.sizeJitter,
                rotationDeg = 0f,
                tint = spec.tintImage && spec.shape == ParticleShape.CustomImage,
            )
        }
    }
}

/** 彩带：彩色小片自转飘落（旋转 + 横向摆动 + 3D 翻面），用调色板逐片取色。 */
@Composable
private fun ConfettiLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as ParticlesSpec
    val peak = motePeak(layer)
    val image = rememberParticleImage(spec, layer.appPackage)
    val motes = remember(layer, seed) {
        val random = Random(ConfettiSeed xor seed)
        List(moteCount(layer)) { index ->
            Mote(
                xNorm = random.nextFloat(),
                yNorm = -0.05f,
                vx = 0f,
                vy = 0.35f + random.nextFloat() * 0.35f,
                delay = random.nextFloat(),
                radiusDp = (ParticlesTuning.ConfettiRadiusMinDp +
                    random.nextFloat() *
                    (ParticlesTuning.ConfettiRadiusMaxDp - ParticlesTuning.ConfettiRadiusMinDp)) *
                    spec.sizeScale,
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
                colorIndex = index,
                spin = (2f + random.nextFloat() * 6f) * (if (random.nextBoolean()) 1f else -1f),
                sizeJitter = varianceScale(spec.variance, random.nextFloat()),
                alphaJitter = varianceScale(spec.variance, random.nextFloat()),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val travelPx = size.height *
            travelNorm(spec.travelPercent, ParticlesTuning.TravelBasisConfetti)
        for (m in motes) {
            val t = ((now / layer.cycleMillis) - m.delay) % 1f
            if (t <= 0f || t >= 1f) continue
            val eased = dragEase(t, spec.drag)
            val alpha = peak * (1f - t) * m.alphaJitter * envelope
            if (alpha <= 0.005f) continue
            val swayBase = ParticlesTuning.ConfettiSwayNorm * spec.swayScale
            val sway = sin(
                2f * Math.PI.toFloat() * t * ParticlesTuning.ConfettiSwayCycles + m.phase,
            ) * swayBase
            val x = (m.xNorm + sway + windOffsetNorm(spec.wind, eased)) * size.width
            val y = m.yNorm * size.height +
                eased.pow(spec.gravityExp) * travelPx * m.vy
            val rot = m.spin * t * 360f
            val r = m.radiusDp.dp.toPx() * m.sizeJitter
            val color = palette.at(m.colorIndex).copy(alpha = alpha)
            // 3D 翻面：横向缩放随自转余弦变化，下限防止纸片消失。
            val flip = kotlin.math.abs(
                cos(
                    2f * Math.PI.toFloat() * t * ParticlesTuning.ConfettiFlipCycles +
                        m.phase,
                ),
            ).coerceAtLeast(ParticlesTuning.ConfettiMinFlipScale)
            if (image != null) {
                drawMote(
                    image = image,
                    color = color,
                    center = Offset(x, y),
                    radiusPx = r,
                    rotationDeg = rot,
                    tint = spec.tintImage && spec.shape == ParticleShape.CustomImage,
                    horizontalScale = flip,
                )
            } else {
                rotate(rot, pivot = Offset(x, y)) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - r * flip, y - r * 0.6f),
                        size = androidx.compose.ui.geometry.Size(r * 2f * flip, r * 1.2f),
                    )
                }
            }
        }
    }
}

/**
 * 萤火：散布全屏、慢速漂移、独立明灭的萤光点。
 * 无「行程」（原地漂移，滑杆在该运动下隐藏）；风 / 阻力 / 方差照常生效。
 */
@Composable
private fun FireflyLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as ParticlesSpec
    val peak = motePeak(layer) * ParticlesTuning.FireflyPeakScale
    val image = rememberParticleImage(spec, layer.appPackage)
    val motes = remember(layer, seed) {
        val random = Random(FireflySeed xor seed)
        List(moteCount(layer)) { index ->
            Mote(
                xNorm = random.nextFloat(),
                yNorm = random.nextFloat(),
                vx = (random.nextFloat() - 0.5f) * 0.08f,
                vy = (random.nextFloat() - 0.5f) * 0.08f,
                delay = random.nextFloat(),
                radiusDp = (1.5f + random.nextFloat() * 2.5f) * spec.sizeScale,
                phase = random.nextFloat() * 2f * Math.PI.toFloat(),
                colorIndex = index,
                spin = 0f,
                sizeJitter = varianceScale(spec.variance, random.nextFloat()),
                alphaJitter = varianceScale(spec.variance, random.nextFloat()),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        for (m in motes) {
            val t = ((now / layer.cycleMillis) - m.delay) % 1f
            if (t <= 0f || t >= 1f) continue
            val eased = dragEase(t, spec.drag)
            val blink = 0.5f + 0.5f * sin(
                2f * Math.PI.toFloat() * t * ParticlesTuning.FireflyBlinkCycles + m.phase,
            )
            val alpha = peak * (1f - ParticlesTuning.FireflyAlphaFade * t) *
                blink * m.alphaJitter * envelope
            if (alpha <= 0.005f) continue
            val jitter = ParticlesTuning.FireflyDriftJitterNorm * spec.swayScale
            val x = (m.xNorm + m.vx * eased +
                sin(t * ParticlesTuning.FireflyDriftCyclesX + m.phase) * jitter +
                windOffsetNorm(spec.wind, eased)) * size.width
            val y = (m.yNorm + m.vy * eased +
                cos(t * ParticlesTuning.FireflyDriftCyclesY + m.phase) * jitter) * size.height
            val r = m.radiusDp.dp.toPx() * m.sizeJitter
            val color = palette.at(m.colorIndex)
            // 外晕 + 内核，制造萤光感。
            drawCircle(
                color = color.copy(alpha = alpha * ParticlesTuning.FireflyHaloAlphaFraction),
                radius = r * ParticlesTuning.FireflyHaloScale,
                center = Offset(x, y),
            )
            drawMote(
                image = image,
                color = color.copy(alpha = alpha),
                center = Offset(x, y),
                radiusPx = r,
                rotationDeg = 0f,
                tint = spec.tintImage && spec.shape == ParticleShape.CustomImage,
            )
        }
    }
}

/** 写入调色板 AGSL uniforms（paletteCount + 5 色；空色表回退首色）。 */
private fun setPaletteUniforms(shader: RuntimeShader, palette: AlertPaletteColors) {
    val n = palette.colors.size.coerceIn(1, 5)
    shader.setFloatUniform("paletteCount", n.toFloat())
    val argb = palette.colors.map { it.copy(alpha = 1f).toArgb() }
    shader.setColorUniform("palette0", argb[0 % argb.size])
    shader.setColorUniform("palette1", argb[1 % argb.size])
    shader.setColorUniform("palette2", argb[2 % argb.size])
    shader.setColorUniform("palette3", argb[3 % argb.size])
    shader.setColorUniform("palette4", argb[4 % argb.size])
}

/** AGSL 编译失败不连坐整窗：失败只记日志并跳过该层（否则构造异常会把窗口一起带走）。 */
private fun createShader(source: String, name: String): RuntimeShader? =
    runCatching { RuntimeShader(source) }
        .onFailure { Log.e(TAG, "shader $name compile FAILED", it) }
        .getOrNull()

/** 首尾淡入淡出包络：整段时长的 [share] 比例内从 0 升到 1、再从 1 落到 0。 */
internal fun envelopeAt(now: Float, totalMillis: Int, share: Float): Float {
    val fade = (totalMillis * share).coerceAtLeast(1f)
    val inFraction = (now / fade).coerceIn(0f, 1f)
    val outFraction = ((totalMillis - now) / fade).coerceIn(0f, 1f)
    return minOf(inFraction, outFraction)
}
