package hunoia.luno.runtime.floatball.alert

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.runtime.floatball.alert.engine.NormPoint
import hunoia.luno.runtime.floatball.alert.engine.ParticleFrame
import hunoia.luno.runtime.floatball.alert.engine.ParticleState
import hunoia.luno.runtime.floatball.alert.engine.advanceParticle
import hunoia.luno.runtime.floatball.alert.engine.noise01
import hunoia.luno.runtime.floatball.alert.engine.spawnParticle
import hunoia.luno.runtime.floatball.alert.engine.varianceScale
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
private const val PathSeed = 0x9A7A
private const val TwoPi = (Math.PI * 2.0).toFloat()

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
    /** 当前锚点：静态播放返回固定值；触钮跟手时每帧读最新手指坐标。 */
    anchorProvider: () -> AlertAnchor = { anchor },
) {
    Box(modifier = Modifier.fillMaxSize()) {
        plan.stageLayers.forEachIndexed { index, layer ->
            key(layer.layerId ?: "${layer.presetId ?: layer.style}-$index") {
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
                        EdgeAlertStyle.Ribbon -> EdgeRibbonLayer(layer, palette, clock)
                        EdgeAlertStyle.Lightning -> EdgeLightningLayer(layer, palette, clock, seed)
                        EdgeAlertStyle.Comet -> EdgeCometLayer(layer, palette, clock)
                    }

                    is ParticleAlertStyle -> ParticleEngineLayer(
                        layer,
                        anchor,
                        palette,
                        clock,
                        seed,
                        anchorProvider,
                    )

                    is AnchorAlertStyle -> when (style) {
                        AnchorAlertStyle.Ring ->
                            BallRingLayer(layer, anchor, palette, clock, anchorProvider)
                        AnchorAlertStyle.Ripple ->
                            FullRippleLayer(layer, anchor, palette, clock, anchorProvider)
                        AnchorAlertStyle.BallBurst ->
                            BallBurstLayer(layer, anchor, palette, clock, seed, anchorProvider)
                        AnchorAlertStyle.BallHalo ->
                            BallHaloLayer(layer, anchor, palette, clock, anchorProvider)
                        AnchorAlertStyle.Spiral ->
                            BallSpiralLayer(layer, anchor, palette, clock, anchorProvider)
                        AnchorAlertStyle.Orbit3D ->
                            BallOrbit3DLayer(layer, anchor, palette, clock, anchorProvider)
                        AnchorAlertStyle.Shockwave ->
                            BallShockwaveLayer(layer, anchor, palette, clock, anchorProvider)
                    }

                    is ScreenAlertStyle -> ScreenLayer(layer, palette, clock)

                    is RevealAlertStyle -> RevealLayer(layer, palette, clock)

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

/**
 * 粒子层（统一引擎）：把层的旋钮翻译成发射器 + 六族方程，逐帧积分后按形状绘制。
 *
 * 旧的五种手写循环收敛到这一层；样式差异只体现在参数（发射区域 / 方向 / 方程强度 / 形状）。
 * 种子来自播放计划，同种子同参数可复现；积分用 1/60 固定步长累积，避免帧率改变轨迹。
 */
@Composable
private fun ParticleEngineLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as ParticlesSpec
    val params = spec.params
    val emitter = params.emitter
    val look = params.look
    val style = layer.style as ParticleAlertStyle
    val image = rememberParticleImage(look, layer.appPackage)
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }
    val peakAlpha = look.alpha.coerceIn(0.05f, 1f)
    val count = (emitter.count * layer.cycles).coerceIn(1, 600)
    val cycleSeconds = layer.cycleMillis / 1000f
    // 寿命 = 单循环 × 寿命占比；至少留 5% 做错峰，否则一循环内所有粒子同刻出发。
    val lifeSeconds = (cycleSeconds * emitter.lifeShare.coerceIn(0.05f, 0.95f))
        .coerceIn(0.06f, cycleSeconds * 0.95f)
    val spreadMs = (layer.cycleMillis - lifeSeconds * 1000f).coerceAtLeast(1f)
    val motion = params.motion

    // 坐标口径：x 归一化到 0..aspect（aspect = 宽 / 高），像素 = x × 屏高；
    // 这样方向角才是真实角度（只按屏高缩放会在大屏上把 45° 拉成近 66°）。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val aspect = if (constraints.maxHeight > 0) {
            constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat()
        } else {
            1f
        }
        // 出生点只取起播瞬间的锚点：跟手是**整体平移**，不能重建粒子状态（否则每帧闪）。
        val anchorNorm = if (constraints.maxHeight > 0) {
            NormPoint(
                anchor.centerX / constraints.maxHeight,
                anchor.centerY / constraints.maxHeight,
            )
        } else {
            null
        }

        // 一次播放的粒子状态：初始位置 / 方向一次生成，之后按帧推进。
        val run = remember(layer, seed, aspect, anchorNorm) {
            val random = Random((layer.presetId?.hashCode() ?: engineSeed(style)) xor seed)
            val states = mutableListOf<ParticleState>()
            val sizeJitters = FloatArray(count)
            val alphaJitters = FloatArray(count)
            repeat(count) { index ->
                val base = spawnParticle(emitter, ParticleFrame(aspect), anchorNorm, random)
                val wave = index / (count / layer.cycles).coerceAtLeast(1)
                states += base.copy(
                    life = lifeSeconds,
                    delay = (wave * layer.cycleMillis + random.nextFloat() * spreadMs) / 1000f,
                    seed = base.seed xor index,
                )
                sizeJitters[index] = varianceScale(look.sizeJitter, random.nextFloat())
                alphaJitters[index] = varianceScale(look.sizeJitter, random.nextFloat())
            }
            ParticleRun(states, sizeJitters, alphaJitters)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = clock()
            if (now >= layer.totalMillis) return@Canvas
            val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
            if (envelope <= 0.005f) return@Canvas

            val frame = ParticleFrame(aspect = aspect)
            advanceRun(run, frame, motion, now)

            // 叠加混色：整层先开一个 Plus 混合的图层，暗背景上亮部更亮。
            val blendPaint = if (look.blendAdd) {
                androidx.compose.ui.graphics.Paint().apply {
                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus
                }
            } else {
                null
            }
            if (blendPaint != null) {
                drawContext.canvas.saveLayer(
                    androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                    blendPaint,
                )
            }
            // 跟手平移：当前锚点 - 起播锚点；只用屏幕高度归一化，口径与粒子引擎一致。
            val currentAnchor = anchorProvider()
            val followDx = (currentAnchor.centerX - anchor.centerX) / size.height
            val followDy = (currentAnchor.centerY - anchor.centerY) / size.height
            val nowSeconds = now / 1000f
            // sizeDp 是外接盒边长（不是半径）：换形状时视觉大小一致。
            val peakSizePx = look.sizeDp.dp.toPx() / 2f
            val trailPx = look.trailDp.dp.toPx()
            run.states.forEachIndexed { index, particle ->
                val age = nowSeconds - particle.delay
                if (age <= 0f) return@forEachIndexed
                val progress = (age / lifeSeconds).coerceIn(0f, 1f)
                var alpha = peakAlpha * (1f - progress).pow(ParticlesTuning.FadeExp) *
                    run.alphaJitters[index] * envelope
                // 萤火一类的闪烁：每颗粒子用自己的相位明灭，不整层同步。
                if (look.blink) {
                    val phase = (particle.seed and 0xFF) / 255f * TwoPi
                    val pulse = kotlin.math.abs(
                        kotlin.math.sin(progress * look.blinkCycles * TwoPi + phase),
                    )
                    alpha *= (0.35f + 0.65f * pulse)
                }
                if (alpha <= 0.005f) return@forEachIndexed
                val accent = palette.at(index)
                val center = Offset(
                    (particle.x + followDx) * size.height,
                    (particle.y + followDy) * size.height,
                )
                val radius = peakSizePx * run.sizeJitters[index]
                drawHalo(
                    style = look.haloStyle,
                    shape = look.shape,
                    image = image,
                    color = accent,
                    center = center,
                    radiusPx = radius,
                    rotationDeg = if (look.rotate || motion.spin > 0f) particle.spinDeg else 0f,
                    scale = look.haloScale,
                    alpha = (alpha * look.haloAlpha).coerceIn(0f, 1f),
                    textPaint = textPaint,
                )
                drawEngineShape(
                    shape = look.shape,
                    image = image,
                    color = accent.copy(alpha = alpha),
                    center = center,
                    radiusPx = radius,
                    rotationDeg = if (look.rotate || motion.spin > 0f) particle.spinDeg else 0f,
                    vx = particle.vx,
                    vy = particle.vy,
                    trailPx = trailPx,
                    trailAlpha = look.trailAlpha,
                    tint = look.tintImage && look.shape == AlertParticleShape.CustomImage,
                    text = look.text,
                    textPaint = textPaint,
                )
            }
            if (blendPaint != null) {
                drawContext.canvas.restore()
            }
        }
    }
}

/** 引擎层需要的、随帧推进的状态（抖动数组与状态同序，避免逐帧再算随机）。 */
private class ParticleRun(
    val states: MutableList<ParticleState>,
    val sizeJitters: FloatArray,
    val alphaJitters: FloatArray,
) {
    /** 上一次推进到的时间（ms）；由固定步长累积，重组不会重置。 */
    var lastMs: Float = 0f
}

/**
 * 固定步长推进：1/60 秒累积，避免帧率改变轨迹；单帧最多补 6 步（防止卡顿后大跳）。
 * 延迟期内的粒子不推进（从 0 开始），到点后按真实时间推进。
 */
private fun advanceRun(
    run: ParticleRun,
    frame: ParticleFrame,
    motion: ParticleMotion,
    nowMs: Float,
) {
    val elapsed = (nowMs - run.lastMs).coerceIn(0f, 100f) / 1000f
    run.lastMs = nowMs
    if (elapsed <= 0f || run.states.isEmpty()) return
    val stepSecond = 1f / 60f
    val nowSeconds = nowMs / 1000f
    var remaining = elapsed.coerceAtMost(stepSecond * 6)
    while (remaining > 0f) {
        val step = minOf(remaining, stepSecond)
        for (i in run.states.indices) {
            val state = run.states[i]
            if (nowSeconds < state.delay) continue
            run.states[i] = advanceParticle(state, step, motion, frame, null)
        }
        remaining -= step
    }
}

private fun engineSeed(style: ParticleAlertStyle): Int = when (style) {
    ParticleAlertStyle.TopRain -> RainSeed
    ParticleAlertStyle.BottomFountain -> FountainSeed
    ParticleAlertStyle.Meteor -> MeteorSeed
    ParticleAlertStyle.Confetti -> ConfettiSeed
    ParticleAlertStyle.Firefly -> FireflySeed
    ParticleAlertStyle.Path -> PathSeed
}

/**
 * 光晕：**不再画硬边大圆**。
 *
 * [ParticleHaloStyle.Soft] 用径向渐变（边缘自然衰减）；[ParticleHaloStyle.Shape] 用同形状
 * 放大 + 低 alpha（雪花辉光是雪花轮廓）；[ParticleHaloStyle.Off] 不画。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHalo(
    style: ParticleHaloStyle,
    shape: AlertParticleShape,
    image: androidx.compose.ui.graphics.ImageBitmap?,
    color: Color,
    center: Offset,
    radiusPx: Float,
    rotationDeg: Float,
    scale: Float,
    alpha: Float,
    textPaint: Paint,
) {
    if (style == ParticleHaloStyle.Off || alpha <= 0.005f) return
    val haloRadius = (radiusPx * scale.coerceIn(1f, 4f)).coerceAtLeast(0.5f)
    when (style) {
        ParticleHaloStyle.Soft -> {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 1f), color.copy(alpha = 0f)),
                    center = center,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = center,
                alpha = alpha,
            )
        }
        ParticleHaloStyle.Shape -> {
            // 图片 / 图标 / 文字没有可放大的轮廓，回落柔光。
            if (image != null || shape == AlertParticleShape.Emoji ||
                shape == AlertParticleShape.Text || shape == AlertParticleShape.Line
            ) {
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 1f), color.copy(alpha = 0f)),
                        center = center,
                        radius = haloRadius,
                    ),
                    radius = haloRadius,
                    center = center,
                    alpha = alpha,
                )
            } else {
                drawEngineShape(
                    shape = shape,
                    image = null,
                    color = color.copy(alpha = alpha),
                    center = center,
                    radiusPx = haloRadius,
                    rotationDeg = rotationDeg,
                    vx = 0f,
                    vy = 0f,
                    trailPx = 0f,
                    tint = false,
                    text = "",
                    textPaint = textPaint,
                )
            }
        }
        ParticleHaloStyle.Off -> Unit
    }
}

/**
 * 形状绘制：几何形状走 Path / 描边，图片 / 图标复用 [drawMote]，文字 / Emoji 走原生文本。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEngineShape(
    shape: AlertParticleShape,
    image: androidx.compose.ui.graphics.ImageBitmap?,
    color: Color,
    center: Offset,
    radiusPx: Float,
    rotationDeg: Float,
    vx: Float,
    vy: Float,
    trailPx: Float,
    trailAlpha: Float = 0.5f,
    tint: Boolean,
    text: String,
    textPaint: Paint,
) {
    if (shape == AlertParticleShape.CustomImage || shape == AlertParticleShape.AppIcon || image != null) {
        drawMote(image = image, color = color, center = center, radiusPx = radiusPx, rotationDeg = rotationDeg, tint = tint)
        return
    }
    when (shape) {
        AlertParticleShape.Square -> rotate(rotationDeg, pivot = center) {
            // 彩带 / 纸片：随自转横缩，翻面感来自宽度变化而不是再加一个形状。
            val flutter = 0.3f + 0.7f *
                kotlin.math.abs(kotlin.math.cos(rotationDeg * (Math.PI.toFloat() / 180f)))
            scale(scaleX = flutter, scaleY = 1f, pivot = center) {
                drawRect(
                    color = color,
                    topLeft = Offset(center.x - radiusPx, center.y - radiusPx),
                    size = androidx.compose.ui.geometry.Size(radiusPx * 2f, radiusPx * 2f),
                )
            }
        }
        AlertParticleShape.Triangle -> polygonPath(center, radiusPx, 3, rotationDeg)
            .let { drawPath(it, color) }
        AlertParticleShape.Star -> starPath(center, radiusPx, points = 5, innerRatio = 0.45f, rotationDeg)
            .let { drawPath(it, color) }
        AlertParticleShape.Hexagon -> polygonPath(center, radiusPx, 6, rotationDeg)
            .let { drawPath(it, color) }
        AlertParticleShape.Ring, AlertParticleShape.Hollow -> drawCircle(
            color = color,
            radius = radiusPx,
            center = center,
            style = Stroke(width = (radiusPx * 0.4f).coerceAtLeast(1f)),
        )
        AlertParticleShape.Line -> {
            val length = maxOf(trailPx, radiusPx * 3f)
            val len = kotlin.math.hypot(vx, vy).coerceAtLeast(1e-3f)
            val end = Offset(center.x - vx / len * length, center.y - vy / len * length)
            val head = Offset(center.x - vx / len * length * 0.28f, center.y - vy / len * length * 0.28f)
            val stroke = (radiusPx * 0.6f).coerceAtLeast(1f)
            // 拖尾透明度只影响尾巴，亮头保持住；否则调低就整条看不见。
            drawLine(
                color = color.copy(alpha = color.alpha * trailAlpha.coerceIn(0f, 1f)),
                start = end,
                end = center,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = head,
                end = center,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        AlertParticleShape.Heart -> rotate(rotationDeg, pivot = center) {
            drawPath(heartPath(center, radiusPx), color)
        }
        AlertParticleShape.Petal -> rotate(rotationDeg, pivot = center) {
            drawPath(petalPath(center, radiusPx), color)
        }
        AlertParticleShape.Leaf -> rotate(rotationDeg, pivot = center) {
            drawPath(leafPath(center, radiusPx), color)
            drawLine(
                color = color.copy(alpha = color.alpha * 0.55f),
                start = Offset(center.x, center.y - radiusPx),
                end = Offset(center.x, center.y + radiusPx),
                strokeWidth = (radiusPx * 0.08f).coerceAtLeast(0.8f),
            )
        }
        AlertParticleShape.Snowflake -> drawSnowflake(center, radiusPx, rotationDeg, color)
        AlertParticleShape.Emoji, AlertParticleShape.Text -> {
            val content = text.trim()
            if (content.isEmpty()) {
                drawCircle(color = color, radius = radiusPx, center = center)
            } else {
                textPaint.color = color.toArgb()
                textPaint.alpha = (color.alpha * 255f).toInt().coerceIn(0, 255)
                textPaint.textSize = radiusPx * if (shape == AlertParticleShape.Emoji) 2.2f else 2.4f
                val baseline = center.y - (textPaint.ascent() + textPaint.descent()) / 2f
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(content, center.x, baseline, textPaint)
                }
            }
        }
        else -> drawCircle(color = color, radius = radiusPx, center = center)
    }
}

/** 心形：两个三次贝塞尔花瓣拼成，底部尖角。 */
private fun heartPath(center: Offset, radius: Float): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y + radius * 0.85f)
        cubicTo(
            center.x - radius * 1.5f, center.y - radius * 0.2f,
            center.x - radius * 0.65f, center.y - radius * 1.15f,
            center.x, center.y - radius * 0.35f,
        )
        cubicTo(
            center.x + radius * 0.65f, center.y - radius * 1.15f,
            center.x + radius * 1.5f, center.y - radius * 0.2f,
            center.x, center.y + radius * 0.85f,
        )
        close()
    }

/** 花瓣：上宽下尖的水滴形。 */
private fun petalPath(center: Offset, radius: Float): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticBezierTo(center.x + radius, center.y - radius * 0.1f, center.x, center.y + radius)
        quadraticBezierTo(center.x - radius, center.y - radius * 0.1f, center.x, center.y - radius)
        close()
    }

/** 叶片：上下尖、左右鼓。 */
private fun leafPath(center: Offset, radius: Float): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticBezierTo(center.x + radius * 0.85f, center.y, center.x, center.y + radius)
        quadraticBezierTo(center.x - radius * 0.85f, center.y, center.x, center.y - radius)
        close()
    }

/** 雪花：三条 60° 主轴 + 每轴 60% 处的两根短枝。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    rotationDeg: Float,
    color: Color,
) {
    val stroke = (radius * 0.18f).coerceAtLeast(0.8f)
    repeat(3) { axis ->
        val angle = Math.toRadians((rotationDeg + axis * 60f).toDouble()).toFloat()
        val dx = kotlin.math.cos(angle)
        val dy = kotlin.math.sin(angle)
        val end = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(color = color, start = center, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
        val branchAt = Offset(center.x + dx * radius * 0.62f, center.y + dy * radius * 0.62f)
        for (sign in listOf(-1f, 1f)) {
            val branchAngle = angle + sign * 0.55f
            val branchEnd = Offset(
                branchAt.x + kotlin.math.cos(branchAngle) * radius * 0.34f,
                branchAt.y + kotlin.math.sin(branchAngle) * radius * 0.34f,
            )
            drawLine(
                color = color,
                start = branchAt,
                end = branchEnd,
                strokeWidth = stroke * 0.85f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 正多边形路径（[points] 边形，起点在正上方）。 */
private fun polygonPath(
    center: Offset,
    radius: Float,
    points: Int,
    rotationDeg: Float,
): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    val startAngle = -Math.PI.toFloat() / 2f + Math.toRadians(rotationDeg.toDouble()).toFloat()
    repeat(points) { i ->
        val angle = startAngle + i * (2f * Math.PI.toFloat() / points)
        val x = center.x + kotlin.math.cos(angle) * radius
        val y = center.y + kotlin.math.sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** 五角星路径：外顶点 [points] 个，内顶点按 [innerRatio] 收缩。 */
private fun starPath(
    center: Offset,
    radius: Float,
    points: Int,
    innerRatio: Float,
    rotationDeg: Float,
): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    val startAngle = -Math.PI.toFloat() / 2f + Math.toRadians(rotationDeg.toDouble()).toFloat()
    val step = Math.PI.toFloat() / points
    repeat(points * 2) { i ->
        val r = if (i % 2 == 0) radius else radius * innerRatio
        val angle = startAngle + i * step
        val x = center.x + kotlin.math.cos(angle) * r
        val y = center.y + kotlin.math.sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * 揭示类：标题 / 正文逐字显现（或应用图标逐元素），首帧从 [RevealSpec.scaleFrom] 放大到 1。
 * 内容来自播放请求（通知路径受「允许动效读取通知文字」控制），渲染层不落日志。
 */
@Composable
private fun RevealLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as RevealSpec
    val icon = rememberRevealIcon(spec.iconPackage)
    val titlePaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val bodyPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
    }
    val accent = palette.at(0)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val progress = (now / layer.totalMillis).coerceIn(0f, 1f)
        val stagger = spec.staggerPercent.coerceIn(0, 90) / 100f
        val cx = size.width / 2f
        when (spec.pattern) {
            RevealAlertStyle.Icon -> {
                val bitmap = icon ?: return@Canvas
                val unit = size.minDimension * 0.14f
                val pulse = spec.scaleFrom + (1f - spec.scaleFrom) * progress.coerceIn(0f, 1f)
                val sizePx = unit * pulse
                drawImage(
                    image = bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (cx - sizePx / 2f).toInt(),
                        (size.height * 0.35f - sizePx / 2f).toInt(),
                    ),
                    dstSize = androidx.compose.ui.unit.IntSize(sizePx.toInt(), sizePx.toInt()),
                    alpha = envelope,
                )
            }
            RevealAlertStyle.Text -> {
                val titleSize = size.minDimension * 0.052f
                val bodySize = size.minDimension * 0.034f
                titlePaint.textSize = titleSize
                titlePaint.color = accent.copy(alpha = envelope).toArgb()
                bodyPaint.textSize = bodySize
                bodyPaint.color = accent.copy(alpha = 0.85f * envelope).toArgb()
                val titleWidth = titlePaint.measureText(spec.title)
                val bodyWidth = bodyPaint.measureText(spec.body)
                val titleBaseline = size.height * 0.44f
                val bodyBaseline = titleBaseline + bodySize * 1.9f
                // 揭示顺序：LTR / TTB = 标题先、正文后；RTL / BTT = 反向（逐字从右往左、
                // 正文先）。四种方向都有可见差别，不做「选了没反应」的假旋钮。
                val bodyFirst = spec.direction == RevealDirection.BottomToTop
                val reverseChars = spec.direction == RevealDirection.RightToLeft ||
                    spec.direction == RevealDirection.BottomToTop
                val titleProgress = if (bodyFirst) {
                    ((progress - 0.18f).coerceAtLeast(0f) / 0.82f)
                } else {
                    progress
                }
                val bodyProgress = if (bodyFirst) {
                    progress
                } else {
                    ((progress - 0.18f).coerceAtLeast(0f) / 0.82f)
                }
                if (spec.title.isNotEmpty()) {
                    drawStaggeredLine(
                        text = spec.title,
                        paint = titlePaint,
                        centerX = cx - titleWidth / 2f,
                        baselineY = titleBaseline,
                        progress = titleProgress,
                        stagger = stagger,
                        scaleFrom = spec.scaleFrom,
                        baseColor = accent,
                        envelope = envelope,
                        reverse = reverseChars,
                    )
                }
                if (spec.body.isNotEmpty()) {
                    drawStaggeredLine(
                        text = spec.body,
                        paint = bodyPaint,
                        centerX = cx - bodyWidth / 2f,
                        baselineY = bodyBaseline,
                        progress = bodyProgress,
                        stagger = stagger,
                        scaleFrom = spec.scaleFrom,
                        baseColor = accent.copy(alpha = 0.85f),
                        envelope = envelope,
                        reverse = reverseChars,
                    )
                }
            }
        }
    }
}

/** 逐字显现：每字按错峰进度给 alpha + 缩放；[progress] 0..1。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStaggeredLine(
    text: String,
    paint: Paint,
    centerX: Float,
    baselineY: Float,
    progress: Float,
    stagger: Float,
    scaleFrom: Float,
    baseColor: Color,
    envelope: Float,
    reverse: Boolean,
) {
    if (text.isEmpty()) return
    val widths = FloatArray(text.length) { paint.measureText(text, it, it + 1) }
    val total = widths.sum()
    var x = centerX.coerceAtLeast(0f)
    val charStagger = if (text.length > 1) stagger / (text.length - 1) else 0f
    for (i in text.indices) {
        // 字位仍按阅读顺序排布，只把「出现次序」反向。
        val order = if (reverse) text.lastIndex - i else i
        val local = if (charStagger <= 0f) {
            progress
        } else {
            ((progress - order * charStagger) /
                (1f - (text.length - 1) * charStagger)).coerceIn(0f, 1f)
        }
        if (local > 0f) {
            val scale = scaleFrom + (1f - scaleFrom) * local
            val color = baseColor.copy(alpha = baseColor.alpha * local * envelope)
            paint.color = color.toArgb()
            withTransform({
                scale(scale, scale, Offset(x + widths[i] / 2f, baselineY))
            }) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(text.substring(i, i + 1), x, baselineY, paint)
                }
            }
        }
        x += widths[i]
        if (x > centerX + total + 1f) break
    }
}

/**
 * 全屏着色（舞台窗最上层之外的独立层）：闪光 / 暗角呼吸 / 缩放脉冲 / 关闭屏幕形态。
 *
 * 效果窗是全屏穿透的，不能真的缩放其它内容；缩放脉冲用径向亮环模拟「向外顶一下」，
 * 观感与「屏幕像被轻推」一致，且不依赖任何应用内容。关闭屏幕类形态都利用这个穿透窗
 * 露出真实画面（亮缝 / 碎片间隙 / 圆孔），结束统一盖满黑。
 */
@Composable
private fun ScreenLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as ScreenSpec
    val amplitude = layer.brightness.coerceIn(0f, 1f)
    val accent = palette.at(0)
    // 关闭屏幕形态自己会盖满全黑：不套尾部淡出，否则最后一帧又把黑幕淡回去（看着没全黑）。
    // 预览 / 动作动效同样适用；窗口结束自然消失。
    val holdsBlack = spec.pattern == ScreenAlertStyle.CenterBlackout ||
        spec.pattern == ScreenAlertStyle.CrtOff ||
        spec.pattern == ScreenAlertStyle.Shatter ||
        spec.pattern == ScreenAlertStyle.Blinds ||
        spec.pattern == ScreenAlertStyle.Iris
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = if (holdsBlack) 1f
        else envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        when (spec.pattern) {
            ScreenAlertStyle.Flash -> {
                val wave = sin(Math.PI.toFloat() * t).coerceAtLeast(0f)
                val alpha = amplitude * wave * envelope * 0.8f
                if (alpha > 0.003f) {
                    drawRect(color = accent.copy(alpha = alpha))
                }
            }
            ScreenAlertStyle.Vignette -> {
                val breath = 0.5f + 0.5f * sin(2f * Math.PI.toFloat() * t)
                val alpha = amplitude * (0.35f + 0.65f * breath) * envelope
                val soft = spec.vignetteSoftness.coerceIn(0.05f, 1f)
                val fadeIn = 1f - soft * 0.75f
                val radius = maxOf(size.width, size.height) * 0.78f
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0f),
                            fadeIn to accent.copy(alpha = 0f),
                            1f to accent.copy(alpha = alpha),
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius,
                    ),
                )
            }
            ScreenAlertStyle.CenterBlackout ->
                drawCenterBlackout(t, amplitude, spec.vignetteSoftness, envelope)
            ScreenAlertStyle.CrtOff -> drawCrtOff(t, amplitude)
            ScreenAlertStyle.Shatter -> drawShatter(
                t,
                amplitude,
                accent,
                spec.vignetteSoftness,
                layer.presetId?.hashCode() ?: 0x5A7E,
            )
            ScreenAlertStyle.Blinds -> drawBlinds(t, amplitude)
            ScreenAlertStyle.Iris -> drawIris(t, amplitude, accent, spec.vignetteSoftness)
            ScreenAlertStyle.ZoomPulse -> {
                val scale = 1f +
                    spec.zoomPercent.coerceIn(0, 40) / 100f * t.easedOut(2f)
                val alpha = amplitude * (1f - t).pow(1.4f) * envelope
                val radius = maxOf(size.width, size.height) * 0.42f * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0f),
                            0.65f to accent.copy(alpha = alpha * 0.35f),
                            0.92f to accent.copy(alpha = alpha),
                            1f to accent.copy(alpha = 0f),
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
        }
    }
}

/** 中心黑幕：实心圆扩散；半径覆盖后直接铺满黑，四个角不会再透。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCenterBlackout(
    t: Float,
    amplitude: Float,
    softness: Float,
    envelope: Float,
) {
    val cover = t.easedOut(2.2f)
    val center = Offset(size.width / 2f, size.height / 2f)
    val farthest = kotlin.math.hypot(size.width / 2f, size.height / 2f)
    val radius = farthest * cover
    val alpha = (amplitude * (0.35f + 0.65f * cover) * envelope).coerceIn(0f, 1f)
    if (alpha <= 0.004f) return
    if (cover >= 0.995f || radius >= farthest - 0.5f) {
        drawRect(color = Color.Black.copy(alpha = (amplitude * envelope).coerceIn(0f, 1f)))
        return
    }
    val soft = softness.coerceIn(0.02f, 1f)
    val feather = (radius * soft * 0.28f).coerceAtLeast(1f)
    val outer = (radius + feather).coerceAtLeast(1f)
    val solidStop = (radius / outer).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = alpha),
                solidStop to Color.Black.copy(alpha = alpha),
                1f to Color.Black.copy(alpha = 0f),
            ),
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
    )
}

/** 电视关闭：上下黑幕夹住一条亮缝，缝收窄到 0 后只留中心光点。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrtOff(
    t: Float,
    amplitude: Float,
) {
    val centerY = size.height / 2f
    val open = (1f - t).coerceIn(0f, 1f)
    val gap = (size.height / 2f) * open * open
    val coverAlpha = amplitude.coerceIn(0f, 1f)
    if (gap > 0.5f) {
        val topHeight = (centerY - gap).coerceAtLeast(0f)
        val bottomTop = (centerY + gap).coerceAtMost(size.height)
        drawRect(
            color = Color.Black.copy(alpha = coverAlpha),
            topLeft = Offset.Zero,
            size = Size(size.width, topHeight),
        )
        drawRect(
            color = Color.Black.copy(alpha = coverAlpha),
            topLeft = Offset(0f, bottomTop),
            size = Size(size.width, (size.height - bottomTop).coerceAtLeast(0f)),
        )
    } else {
        drawRect(color = Color.Black.copy(alpha = coverAlpha))
    }
    // 亮缝快速出现再逐渐收细；关到底时换成中心光点，避免最后一帧只剩纯黑。
    val lineIn = (t / 0.08f).coerceIn(0f, 1f)
    val lineAlpha = (lineIn * (1f - t).pow(1.5f)).coerceIn(0f, 1f)
    if (lineAlpha > 0.01f) {
        val thickness = (2f + 8f * open).dp.toPx()
        drawRect(
            color = Color.White.copy(alpha = lineAlpha * 0.9f),
            topLeft = Offset(0f, centerY - thickness / 2f),
            size = Size(size.width, thickness),
        )
    }
    val dot = ((t - 0.72f) / 0.28f).coerceIn(0f, 1f)
    if (dot > 0f) {
        val dotAlpha = (1f - dot) * 0.85f
        val dotRadius = (2f + 8f * (1f - dot)).dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = dotAlpha),
                    1f to Color.White.copy(alpha = 0f),
                ),
                center = Offset(size.width / 2f, centerY),
                radius = dotRadius,
            ),
            radius = dotRadius,
            center = Offset(size.width / 2f, centerY),
        )
    }
}

/** 屏幕碎裂：先放射裂纹，再让黑色碎片从中心压满整屏。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShatter(
    t: Float,
    amplitude: Float,
    accent: Color,
    softness: Float,
    seed: Int,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val farthest = kotlin.math.hypot(size.width / 2f, size.height / 2f) * 1.15f
    val random = Random(seed)
    val shardCount = 16
    val angles = FloatArray(shardCount + 1) { index ->
        val base = index.toFloat() / shardCount * TwoPi
        if (index == 0 || index == shardCount) base
        else base + (random.nextFloat() - 0.5f) * 0.2f
    }
    val crackAlpha = (1f - t / 0.5f).coerceIn(0f, 1f)
    if (crackAlpha > 0.01f) {
        for (index in 0..shardCount) {
            val angle = angles[index]
            val length = farthest * (0.35f + 0.65f * (t / 0.3f).coerceIn(0f, 1f))
            drawLine(
                color = Color.White.copy(alpha = crackAlpha * 0.7f),
                start = center,
                end = Offset(
                    center.x + kotlin.math.cos(angle) * length,
                    center.y + kotlin.math.sin(angle) * length,
                ),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
    val shardProgress = ((t - 0.2f) / 0.8f).coerceIn(0f, 1f).easedOut(2f)
    val scale = 0.12f + 1.03f * shardProgress
    for (index in 0 until shardCount) {
        val a0 = angles[index]
        val a1 = angles[index + 1]
        val path = Path().apply {
            moveTo(center.x, center.y)
            lineTo(
                center.x + kotlin.math.cos(a0) * farthest * scale,
                center.y + kotlin.math.sin(a0) * farthest * scale,
            )
            lineTo(
                center.x + kotlin.math.cos(a1) * farthest * scale,
                center.y + kotlin.math.sin(a1) * farthest * scale,
            )
            close()
        }
        drawPath(path = path, color = Color.Black.copy(alpha = amplitude))
    }
    if (t >= 0.985f) {
        drawRect(color = Color.Black.copy(alpha = amplitude))
    } else {
        // 碎片缝里的裂纹收尾：靠近尾声时用强调色描边，碎裂感更明确。
        val edgeAlpha = (1f - shardProgress) * (0.2f + softness.coerceIn(0f, 1f) * 0.5f)
        if (edgeAlpha > 0.01f) {
            for (index in 0 until shardCount) {
                val angle = angles[index]
                drawLine(
                    color = accent.copy(alpha = edgeAlpha),
                    start = center,
                    end = Offset(
                        center.x + kotlin.math.cos(angle) * farthest * scale,
                        center.y + kotlin.math.sin(angle) * farthest * scale,
                    ),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

/** 百叶关闭：奇偶条带从上下两端交错合拢。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlinds(
    t: Float,
    amplitude: Float,
) {
    val bands = 7
    val bandHeight = size.height / bands
    val alpha = amplitude.coerceIn(0f, 1f)
    for (index in 0 until bands) {
        val local = ((t - index * 0.045f) / 0.68f).coerceIn(0f, 1f).easedOut(2f)
        val height = bandHeight * local
        if (height <= 0f) continue
        val top = if (index % 2 == 0) {
            index * bandHeight
        } else {
            (index + 1) * bandHeight - height
        }
        drawRect(
            color = Color.Black.copy(alpha = alpha),
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
        )
    }
    if (t >= 0.985f) {
        drawRect(color = Color.Black.copy(alpha = alpha))
    }
}

/** 圆瞳收束：黑色遮罩留圆形透明孔，孔径收到 0。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIris(
    t: Float,
    amplitude: Float,
    accent: Color,
    softness: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val farthest = kotlin.math.hypot(size.width / 2f, size.height / 2f)
    val radius = farthest * (1f - t).pow(1.4f)
    val alpha = amplitude.coerceIn(0f, 1f)
    if (radius <= 0.5f) {
        drawRect(color = Color.Black.copy(alpha = alpha))
        return
    }
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(0f, 0f, size.width, size.height))
        addOval(
            Rect(
                center.x - radius,
                center.y - radius,
                center.x + radius,
                center.y + radius,
            ),
        )
    }
    drawPath(path = path, color = Color.Black.copy(alpha = alpha))
    val soft = softness.coerceIn(0f, 1f)
    val rimAlpha = ((1f - t) * (0.35f + soft * 0.55f)).coerceIn(0f, 1f)
    if (rimAlpha > 0.01f) {
        drawCircle(
            color = accent.copy(alpha = rimAlpha),
            radius = radius,
            center = center,
            style = Stroke(width = (1f + soft * 3f).dp.toPx()),
        )
    }
}

/**
 * 缎带：沿屏缘环行的宽柔光带，带高斯边缘与可调拖尾；颜色沿周长采样。
 * 与流光的区别是「带宽 / 柔和度」优先，头部没有锐利主带。
 */
@Composable
private fun EdgeRibbonLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeRibbonSpec
    val peakAlpha = layer.brightness.lerpInRange(
        EdgeGlowTuning.PeakAlphaMin,
        EdgeGlowTuning.PeakAlphaMax,
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val head = t.easedOut(2f)
        val inset = EdgeFrameTuning.InsetDp.dp.toPx()
        val perim = EdgePerimeter(size, inset, spec.cornerRadiusDp.dp.toPx())
        val bandPx = spec.bandWidthDp.dp.toPx()
        val band = (bandPx / maxOf(size.width, size.height)).coerceIn(0.01f, 0.25f)
        val tail = (band * spec.trailScale).coerceIn(0f, 0.9f)
        val samples = 180
        for (i in 0 until samples) {
            val f = i.toFloat() / samples
            val d = perimeterDist(f, head)
            // 头部高斯带 + 后方拖尾（沿行进方向衰减）。
            val forward = if (d <= band) 1f - (d / band) else 0f
            val back = if (d <= band + tail && d >= band) {
                (1f - (d - band) / tail) * spec.trailAlpha
            } else {
                0f
            }
            val strength = minOf(1f, forward + back)
            if (strength <= 0.01f) continue
            drawCircle(
                color = palette.sample(f),
                alpha = peakAlpha * strength * envelope,
                radius = (bandPx * 0.65f).coerceAtLeast(1f),
                center = perim.point(f),
            )
        }
    }
}

/**
 * 屏缘彗星：一个亮头 + 沿行进反方向的渐细拖尾，长度与头宽独立可调。
 * 与流光的区别是「拖尾长、头锐、无第二带」，不是同一形态换参数。
 */
@Composable
private fun EdgeCometLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as EdgeCometSpec
    val peakAlpha = layer.brightness.lerpInRange(
        EdgeGlowTuning.PeakAlphaMin,
        EdgeGlowTuning.PeakAlphaMax,
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val inset = EdgeFrameTuning.InsetDp.dp.toPx()
        val perim = EdgePerimeter(size, inset, spec.cornerRadiusDp.dp.toPx())
        val head = ((now / layer.cycleMillis) % 1f).easedOut(1.6f)
        val tailFrac = spec.tailFraction.coerceIn(0.02f, 0.45f)
        val headWidth = spec.headWidthDp.dp.toPx()
        val samples = 360
        for (i in 0 until samples) {
            val f = i.toFloat() / samples
            // 行进方向为正：delta = 头部已经走过该点的周长比例。
            val delta = ((head - f) % 1f + 1f) % 1f
            if (delta > tailFrac) continue
            val k = 1f - delta / tailFrac
            val alpha = peakAlpha * k.pow(1.7f) * spec.tailAlpha * envelope
            if (alpha <= 0.01f) continue
            drawCircle(
                color = palette.sample(f),
                alpha = alpha,
                radius = headWidth * (0.3f + 0.7f * k) / 2f,
                center = perim.point(f),
            )
        }
        // 头部额外提亮一层，避免长拖尾把「头」读没了。
        val headPoint = perim.point(head)
        drawCircle(
            color = palette.sample(head),
            alpha = (peakAlpha * envelope).coerceIn(0f, 1f),
            radius = headWidth * 0.8f,
            center = headPoint,
        )
    }
}

/**
 * 闪电：沿屏缘随机落点的折线放电，向屏幕内短促炸开；每一道有自己的闪断窗口。
 * 顶点抖动由播放种子决定，同种子可复现。
 */
@Composable
private fun EdgeLightningLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as EdgeLightningSpec
    val peakAlpha = layer.brightness.lerpInRange(
        EdgeRippleTuning.PeakAlphaMin,
        EdgeRippleTuning.PeakAlphaMax,
    )
    val path = remember { androidx.compose.ui.graphics.Path() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val inset = EdgeFrameTuning.InsetDp.dp.toPx()
        val perim = EdgePerimeter(size, inset, spec.cornerRadiusDp.dp.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        val strikeWindow = 0.34f
        repeat(spec.strikeCount) { strike ->
            val local = ((t * spec.strikeCount - strike) % 1f + 1f) % 1f
            if (local > strikeWindow) return@repeat
            val flash = 1f - local / strikeWindow
            val alpha = peakAlpha * flash * envelope
            if (alpha <= 0.01f) return@repeat
            val anchor = ((seed * 0.0001f + strike * 0.37f) % 1f + 1f) % 1f
            val start = perim.point(anchor)
            path.reset()
            path.moveTo(start.x, start.y)
            val steps = 7
            var px = start.x
            var py = start.y
            for (step in 1..steps) {
                val f = step.toFloat() / steps
                val tx = start.x + (center.x - start.x) * f * 0.38f
                val ty = start.y + (center.y - start.y) * f * 0.38f
                val jitter = (noise01(seed + strike * 97, step * 1.7f) - 0.5f)
                val dx = (center.x - start.x) / maxOf(1f, kotlin.math.hypot(center.x - start.x, center.y - start.y))
                val dy = (center.y - start.y) / maxOf(1f, kotlin.math.hypot(center.x - start.x, center.y - start.y))
                px = tx - dy * jitter * size.minDimension * 0.045f
                py = ty + dx * jitter * size.minDimension * 0.045f
                path.lineTo(px, py)
            }
            val color = palette.sample(anchor)
            drawPath(
                path = path,
                color = color.copy(alpha = alpha * 0.35f),
                style = Stroke(width = spec.lineWidthDp.dp.toPx() * 3.2f),
            )
            drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = Stroke(width = spec.lineWidthDp.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * 球锚点螺旋：粒子从球环出发，一边绕球一边向内卷吸，尽头淡出。
 * 角度与半径都按单循环进度解析计算，不逐帧积分。
 */
@Composable
private fun BallSpiralLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as BallSpiralSpec
    val peakAlpha = layer.brightness.lerpInRange(
        BallBurstTuning.PeakAlphaMin,
        BallBurstTuning.PeakAlphaMax,
    )
    val phases = remember(layer) {
        Random(0x5A17 xor (layer.presetId?.hashCode() ?: 0)).let { random ->
            List(spec.count) {
                SpiralParticle(
                    phase = random.nextFloat(),
                    speed = 0.7f + random.nextFloat() * 0.6f,
                    colorIndex = it,
                )
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val liveAnchor = anchorProvider()
        val center = Offset(liveAnchor.centerX, liveAnchor.centerY)
        val baseRadius = liveAnchor.capsulePx / 2f * (spec.travelPercent / 100f)
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        for (particle in phases) {
            val local = ((t * particle.speed + particle.phase) % 1f + 1f) % 1f
            val radius = baseRadius * (1f - 0.75f * local)
            val angle = particle.phase * TwoPi + local * spec.turns * TwoPi
            val x = center.x + cos(angle) * radius
            val y = center.y + sin(angle) * radius
            val tailX = center.x + cos(angle - 0.35f) * (radius + baseRadius * 0.06f)
            val tailY = center.y + sin(angle - 0.35f) * (radius + baseRadius * 0.06f)
            val alpha = peakAlpha * (1f - local).pow(1.2f) * envelope
            drawLine(
                color = palette.at(particle.colorIndex),
                start = Offset(tailX, tailY),
                end = Offset(x, y),
                strokeWidth = spec.particleSizeDp.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = alpha,
            )
        }
    }
}

/**
 * 球锚点 3D 环绕：粒子在倾斜椭圆轨道上运行，深度用尺寸 / alpha 模拟透视假 3D。
 */
@Composable
private fun BallOrbit3DLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as BallOrbit3DSpec
    val peakAlpha = layer.brightness.lerpInRange(
        BallBurstTuning.PeakAlphaMin,
        BallBurstTuning.PeakAlphaMax,
    )
    val phases = remember(layer) {
        Random(0x3D0B xor (layer.presetId?.hashCode() ?: 0)).let { random ->
            List(spec.count) {
                OrbitParticle3D(
                    phase = random.nextFloat(),
                    speed = 0.6f + random.nextFloat() * 0.5f,
                    colorIndex = it,
                )
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val liveAnchor = anchorProvider()
        val center = Offset(liveAnchor.centerX, liveAnchor.centerY)
        val radius = liveAnchor.capsulePx / 2f * (spec.travelPercent / 100f)
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        for (particle in phases) {
            val angle = (t * particle.speed + particle.phase) % 1f * TwoPi
            val depth = sin(angle)
            val y = center.y + depth * radius * spec.tilt
            val x = center.x + cos(angle) * radius
            val size = spec.particleSizeDp.dp.toPx() * (0.7f + 0.5f * (depth + 1f) / 2f)
            val alpha = peakAlpha * (0.4f + 0.6f * (depth + 1f) / 2f) * envelope
            drawCircle(
                color = palette.at(particle.colorIndex),
                alpha = alpha,
                radius = size,
                center = Offset(x, y),
            )
        }
    }
}

/**
 * 球心冲击波：单环快速外扩，前沿粗、内侧渐隐；不画涟漪的多重尾环。
 */
@Composable
private fun BallShockwaveLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as ShockwaveSpec
    val peakAlpha = layer.brightness.lerpInRange(
        BallBurstTuning.PeakAlphaMin,
        BallBurstTuning.PeakAlphaMax,
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val liveAnchor = anchorProvider()
        val center = Offset(liveAnchor.centerX, liveAnchor.centerY)
        val maxRadius = liveAnchor.capsulePx / 2f * (spec.travelPercent / 100f)
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val progress = t.easedOut(2.4f)
        val radius = (maxRadius * progress).coerceAtLeast(1f)
        val alpha = (peakAlpha * (1f - progress).pow(1.1f) * envelope).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return@Canvas
        val stroke = spec.strokeWidthDp.dp.toPx()
        val color = palette.at(0)
        // 三层同心描边：前沿亮、外侧柔、内侧收，读作「一堵墙推出去」。
        drawCircle(
            color = color,
            alpha = alpha * 0.35f,
            radius = radius,
            center = center,
            style = Stroke(width = stroke * 3.4f),
        )
        drawCircle(
            color = color,
            alpha = alpha,
            radius = radius,
            center = center,
            style = Stroke(width = stroke * 1.4f),
        )
        drawCircle(
            color = color,
            alpha = alpha * 0.5f,
            radius = (radius - stroke * 1.2f).coerceAtLeast(0f),
            center = center,
            style = Stroke(width = stroke * 0.8f),
        )
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
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as RingSpec
    val peakAlpha = layer.brightness.lerpInRange(RingTuning.PeakAlphaMin, RingTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    val ringsPerCycle = spec.ringsPerCycle
    val haloScale = spec.haloScale
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        val liveAnchor = anchorProvider()
        val travelPx = ringTravelPx(liveAnchor.capsulePx, layer.amount).toFloat()
        val center = Offset(liveAnchor.centerX, liveAnchor.centerY)
        val startRadius = liveAnchor.capsulePx / 2f * 0.9f
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
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as RippleSpec
    val peakAlpha = layer.brightness.lerpInRange(RippleTuning.PeakAlphaMin, RippleTuning.PeakAlphaMax)
    val strokeWidthDp = spec.strokeWidthDp.toFloat()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val liveAnchor = anchorProvider()
        val center = if (spec.centerAtBall) {
            Offset(liveAnchor.centerX, liveAnchor.centerY)
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }
        val startRadius = if (spec.centerAtBall) liveAnchor.capsulePx / 2f * 0.9f else 0f
        val farthest = maxOf(
            (Offset.Zero - center).getDistance(),
            (Offset(size.width, 0f) - center).getDistance(),
            (Offset(0f, size.height) - center).getDistance(),
            (Offset(size.width, size.height) - center).getDistance(),
        )
        // 行程旋钮：100% = 到屏幕最远角；<100 收成局部涟漪，>100 提前出屏。
        val travelPx = (farthest - startRadius).coerceAtLeast(0f) *
            (spec.travelPercent / 100f)
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

    /** 周长（px），彗星拖尾按它折算长度比例。 */
    val length: Float get() = total

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
    anchorProvider: () -> AlertAnchor,
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
        val liveAnchor = anchorProvider()
        val center = Offset(liveAnchor.centerX, liveAnchor.centerY)
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

/** 螺旋粒子：相位、速度、取色序号。 */
private data class SpiralParticle(
    val phase: Float,
    val speed: Float,
    val colorIndex: Int,
)

/** 3D 环绕粒子：相位、速度、取色序号。 */
private data class OrbitParticle3D(
    val phase: Float,
    val speed: Float,
    val colorIndex: Int,
)

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
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as BallHaloSpec
    val peakAlpha = layer.brightness.lerpInRange(
        BallHaloTuning.PeakAlphaMin,
        BallHaloTuning.PeakAlphaMax,
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        val liveAnchor = anchorProvider()
        val baseRadius = liveAnchor.capsulePx / 2f * (spec.radiusPercent / 100f)
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val t = (now / layer.cycleMillis) % 1f
        val breath = 0.5f + 0.5f * sin(2f * Math.PI.toFloat() * t)
        val radius = (baseRadius *
            (BallHaloTuning.RadiusMinScale + BallHaloTuning.RadiusBreathScale * breath))
            .coerceAtLeast(1f)
        val center = Offset(liveAnchor.centerX, liveAnchor.centerY)
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

private fun particleImageKey(look: ParticleLook, appPackage: String?): String =
    "${look.shape}|${look.customImage}|${appPackage.orEmpty()}"

/** 揭示图标：应用图标按 128px 解码并缓存（与粒子图标同一 LRU）。 */
@Composable
private fun rememberRevealIcon(appPackage: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val key = "reveal|${appPackage.orEmpty()}"
    var image by remember(key) { mutableStateOf(particleImageCache.get(key)) }
    LaunchedEffect(key) {
        if (appPackage == null) {
            image = null
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeAppIcon(context, appPackage) }.getOrNull()
        }
        if (decoded != null) particleImageCache.put(key, decoded)
        image = decoded
    }
    return image
}

/**
 * 粒子形状 → 位图：自定义图片 / 通知应用图标，**后台解码 + 降采样**，失败回落 null（圆点）。
 *
 * 解码不能放在 `remember` 里同步做：那是在主线程解一张全尺寸图，首帧直接卡住，
 * 大图还会 OOM。命中缓存时 `remember` 的初始值就是图，避免重播时闪一帧圆点。
 */
@Composable
private fun rememberParticleImage(
    look: ParticleLook,
    appPackage: String?,
): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val key = particleImageKey(look, appPackage)
    var image by remember(key) { mutableStateOf(particleImageCache.get(key)) }
    LaunchedEffect(key) {
        if (look.shape == AlertParticleShape.Dot) {
            image = null
            return@LaunchedEffect
        }
        image = decodeParticleImage(context, look, appPackage)
    }
    return if (look.shape == AlertParticleShape.Dot) null else image
}

/** 后台解码并按 [ParticleImageMaxPx] 降采样；结果写入 [particleImageCache]。 */
private suspend fun decodeParticleImage(
    context: Context,
    look: ParticleLook,
    appPackage: String?,
): androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
    val key = particleImageKey(look, appPackage)
    particleImageCache.get(key)?.let { return@withContext it }
    val decoded = runCatching {
        when (look.shape) {
            AlertParticleShape.CustomImage -> decodeShapeFile(context, look.customImage)
            AlertParticleShape.AppIcon -> decodeAppIcon(context, appPackage)
            else -> null
        }
    }.getOrNull()
    if (decoded != null) particleImageCache.put(key, decoded)
    decoded
}

/** 自定义形状文件：新数据只存文件名（文件在 `ParticleLook.ImageDirName` 下）；旧数据可能是绝对路径。 */
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
    else File(File(context.filesDir, ParticleLook.ImageDirName), raw)
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
