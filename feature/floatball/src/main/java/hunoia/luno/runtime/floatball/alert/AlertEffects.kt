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
import hunoia.luno.config.model.AlertRenderBackend
import hunoia.luno.config.model.EdgeShape
import hunoia.luno.config.model.BandPath
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.CoverMask
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.RingStyle
import hunoia.luno.config.model.WashShape
import hunoia.luno.runtime.floatball.alert.engine.NormPoint
import hunoia.luno.runtime.floatball.alert.engine.ParticleFrame
import hunoia.luno.runtime.floatball.alert.engine.ParticleState
import hunoia.luno.runtime.floatball.alert.engine.advanceParticle
import hunoia.luno.runtime.floatball.alert.engine.noise01
import hunoia.luno.runtime.floatball.alert.engine.spawnPoint
import hunoia.luno.runtime.floatball.alert.engine.spawnParticle
import hunoia.luno.runtime.floatball.alert.engine.varianceScale
import hunoia.luno.ui.theme.toMarkColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
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
    uniform float tailPower;
    uniform float headBoost;
    uniform float trailDir;
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
            float tail = bandGlow(
                s,
                headPos + trailDir * (coreSigma * 2.0 + trailSigma),
                trailSigma
            );
            aB += pow(tail, max(tailPower, 0.5)) * trailAlpha;
        }
        aB *= depth * intensity * (1.0 + headBoost * bandGlow(s, headPos, coreSigma));
        half4 cB = paletteCount > 1.5 ? paletteAt(headPos) : palette0;
        outRgb += cB.rgb * aB;
        outA += aB;

        if (dualOffset < 1.0) {
            float a2 = bandGlow(s, headPos2, coreSigma);
            if (trailSigma > 0.0) {
                float tail2 = bandGlow(
                    s,
                    headPos2 - trailBack2 * (coreSigma * 2.0 + trailSigma),
                    trailSigma
                );
                a2 += pow(tail2, max(tailPower, 0.5)) * trailAlpha;
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
    uniform float hardness;
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
        float depth = exp(-pow(inward, max(hardness, 0.5))) * exp(-outward);
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

@Composable
internal fun AlertStageLayers(
    plan: AlertPlayPlan,
    anchor: AlertAnchor,
    colorSpec: ColorSpec,
    clock: () -> Float,
    /** 当前锚点：静态播放返回固定值；触钮跟手时每帧读最新手指坐标。 */
    anchorProvider: () -> AlertAnchor = { anchor },
) {
    Box(modifier = Modifier.fillMaxSize()) {
        plan.stageLayers.forEachIndexed { index, layer ->
            key(layer.layerId ?: "${layer.presetId ?: layer.style}-$index") {
                val palette = resolvePalette(layer, colorSpec)
                val seed = plan.seed
                when (layer.style) {
                    AlertRenderStyle.Edge -> EdgeLayer(layer, palette, clock, seed)
                    AlertRenderStyle.Ring ->
                        RingLayer(layer, anchor, palette, clock, anchorProvider)
                    AlertRenderStyle.Wash -> WashLayer(layer, palette, clock)
                    AlertRenderStyle.Cover -> CoverLayer(layer, palette, clock)
                    AlertRenderStyle.Particles -> ParticleEngineLayer(
                        layer,
                        anchor,
                        palette,
                        clock,
                        seed,
                        anchorProvider,
                    )
                    AlertRenderStyle.Reveal -> RevealLayer(layer, palette, clock)
                    AlertRenderStyle.Capsule -> Unit
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
    val image = rememberParticleImage(look, layer.appPackage)
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }
    val peakAlpha = look.alpha.coerceIn(0.05f, 1f)
    val waveCount = layer.cycles.coerceAtLeast(1)
    val anchors = if (emitter.anchorPerWave) emitter.anchorCount.coerceIn(1, 8) else 1
    val perWave = emitter.count.coerceAtLeast(1)
    val clustersPerCycle = anchors * waveCount
    val count = (perWave * clustersPerCycle).coerceIn(1, 600)
    val perWaveClamped = (count / clustersPerCycle).coerceAtLeast(1)
    val cycleSeconds = layer.cycleMillis / 1000f
    val launchShare = emitter.launchShare.coerceIn(0.05f, 0.9f)
    val launch = emitter.launchHeight.coerceIn(0f, 0.9f)
    // 寿命 = 单循环 × 寿命占比；升空形态要给上升留出时间，否则最后一波会被总时长截断。
    val availableSeconds = if (launch > 0f) {
        cycleSeconds * (1f - launchShare).coerceAtLeast(0.1f)
    } else {
        cycleSeconds
    }
    val lifeSeconds = (cycleSeconds * emitter.lifeShare.coerceIn(0.05f, 0.95f))
        .coerceIn(0.06f, availableSeconds.coerceAtLeast(0.06f))
    // 错峰窗口：delaySpread 是「可用窗口」的比例，0 = 同刻出发，1 = 铺满整个窗口。
    val spreadMs = ((layer.cycleMillis - lifeSeconds * 1000f) *
        emitter.delaySpread.coerceIn(0f, 1f)).coerceAtLeast(1f)
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
        val screenCenter = NormPoint(aspect / 2f, 0.5f)
        val focus = if (emitter.area == ParticleArea.Point ||
            emitter.area == ParticleArea.BallRing
        ) {
            anchorNorm ?: screenCenter
        } else {
            screenCenter
        }

        // 一次播放的粒子状态：初始位置 / 方向一次生成，之后按帧推进。
        val run = remember(layer, seed, aspect, anchorNorm) {
            val random = Random((layer.presetId?.hashCode() ?: emitter.area.hashCode()) xor seed)
            val frame = ParticleFrame(aspect)
            val states = ArrayList<ParticleState>(count)
            val sizeJitters = FloatArray(count)
            val alphaJitters = FloatArray(count)
            val waveOf = IntArray(count)
            val rockets = mutableListOf<RocketRun>()
            // 一簇一爆：每次波次共用一个锚点；烟花升空也必须先固定起点。
            val clustered = emitter.anchorPerWave || launch > 0f
            var index = 0
            for (wave in 0 until waveCount) {
                val waveStartMs = (wave * layer.cycleMillis).toFloat()
                val burstMs = waveStartMs + if (launch > 0f) {
                    launchShare * layer.cycleMillis.toFloat()
                } else {
                    0f
                }
                // 同一波次可以有多个锚点：每处各发射 count 颗，互相独立取色 / 升空。
                for (anchorIndex in 0 until anchors) {
                    val cluster = wave * anchors + anchorIndex
                    val waveOrigin = if (clustered) {
                        spawnPoint(emitter.area, frame, anchorNorm, random)
                    } else {
                        null
                    }
                    val burstOrigin = waveOrigin?.let {
                        NormPoint(it.x, (it.y - launch).coerceIn(-0.2f, 1.2f))
                    }
                    if (waveOrigin != null && burstOrigin != null && launch > 0f) {
                        rockets += RocketRun(
                            start = waveOrigin,
                            end = burstOrigin,
                            startMillis = waveStartMs,
                            endMillis = burstMs,
                            colorIndex = cluster,
                        )
                    }
                    repeat(perWaveClamped) {
                        if (index >= count) return@repeat
                        val base = spawnParticle(
                            emitter = emitter,
                            frame = frame,
                            anchor = anchorNorm,
                            random = random,
                            origin = burstOrigin,
                            center = focus,
                        )
                        states += base.copy(
                            life = lifeSeconds,
                            delay = (burstMs + random.nextFloat() * spreadMs) / 1000f,
                            seed = base.seed xor index,
                        )
                        sizeJitters[index] = varianceScale(look.sizeJitter, random.nextFloat())
                        alphaJitters[index] = varianceScale(look.alphaJitter, random.nextFloat())
                        waveOf[index] = cluster
                        index++
                    }
                }
            }
            ParticleRun(states, sizeJitters, alphaJitters, waveOf, rockets)
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
            val shrink = look.sizeShrink.coerceIn(0f, 0.9f)
            val fadeExp = look.fadeExp.coerceIn(0.5f, 4f)

            // 升空烟花的火箭：本体是一条短拖尾 + 亮头，到顶点后由下面的粒子接棒。
            run.rockets.forEach { rocket ->
                if (now < rocket.startMillis || now > rocket.endMillis) return@forEach
                val span = (rocket.endMillis - rocket.startMillis).coerceAtLeast(1f)
                val progress = ((now - rocket.startMillis) / span).coerceIn(0f, 1f)
                val ease = progress * progress
                val x = rocket.start.x + (rocket.end.x - rocket.start.x) * ease
                val y = rocket.start.y + (rocket.end.y - rocket.start.y) * ease
                val center = Offset(
                    (x + followDx) * size.height,
                    (y + followDy) * size.height,
                )
                val dx = rocket.end.x - rocket.start.x
                val dy = rocket.end.y - rocket.start.y
                val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f)
                val dir = Offset(dx / length, dy / length)
                val rocketColor = palette.at(rocket.colorIndex)
                val tail = maxOf(trailPx, RocketTrailDp.dp.toPx())
                drawLine(
                    color = rocketColor.copy(alpha = 0.65f * envelope),
                    start = center - dir * tail,
                    end = center,
                    strokeWidth = maxOf(2.dp.toPx(), trailPx * 0.35f),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = rocketColor.copy(alpha = envelope),
                    radius = 2.5.dp.toPx(),
                    center = center,
                )
            }

            run.states.forEachIndexed { index, particle ->
                val age = nowSeconds - particle.delay
                if (age <= 0f) return@forEachIndexed
                val progress = (age / lifeSeconds).coerceIn(0f, 1f)
                var alpha = peakAlpha * (1f - progress).pow(fadeExp) *
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
                val accent = if (look.colorPerWave) {
                    palette.at(run.waveOf[index])
                } else {
                    palette.at(index)
                }
                val center = Offset(
                    (particle.x + followDx) * size.height,
                    (particle.y + followDy) * size.height,
                )
                val radius = peakSizePx * run.sizeJitters[index] * (1f - shrink * progress)
                if (radius <= 0.05f) return@forEachIndexed
                // tilt：绕纵轴压扁；spin 为 0 时也由 tilt 驱动相位，否则翻面是死的。
                val tilt = if (motion.tilt > 0f) {
                    val c = kotlin.math.abs(
                        kotlin.math.cos(Math.toRadians(particle.spinDeg.toDouble())),
                    ).toFloat()
                    1f - motion.tilt.coerceIn(0f, 1f) * (1f - c)
                } else {
                    1f
                }
                scale(
                    scaleX = tilt,
                    scaleY = 1f,
                    pivot = center,
                ) {
                    drawHalo(
                        style = look.haloStyle,
                        shape = look.shape,
                        image = image,
                        color = accent,
                        center = center,
                        radiusPx = radius,
                        rotationDeg = particle.spinDeg,
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
                        rotationDeg = particle.spinDeg,
                        vx = particle.vx,
                        vy = particle.vy,
                        trailPx = trailPx,
                        trailAlpha = look.trailAlpha,
                        tint = look.tintImage && look.shape == AlertParticleShape.CustomImage,
                        text = look.text,
                        textPaint = textPaint,
                    )
                }
            }
            if (blendPaint != null) {
                drawContext.canvas.restore()
            }
        }
    }
}

/** 升空烟花的火箭拖尾最短长度（dp）；用户把 trailDp 调大时取更大值。 */
private const val RocketTrailDp = 18f

/** 一簇烟花的升空轨迹：起止点 + 起止时刻 + 取色序号。 */
private class RocketRun(
    val start: NormPoint,
    val end: NormPoint,
    val startMillis: Float,
    val endMillis: Float,
    val colorIndex: Int,
)

/** 引擎层需要的、随帧推进的状态（抖动数组与状态同序，避免逐帧再算随机）。 */
/** 引擎层需要的、随帧推进的状态（抖动数组与状态同序，避免逐帧再算随机）。 */
private class ParticleRun(
    val states: MutableList<ParticleState>,
    val sizeJitters: FloatArray,
    val alphaJitters: FloatArray,
    /** 每颗粒子属于哪一波（烟花一簇一色用）。 */
    val waveOf: IntArray,
    /** 升空烟花的火箭轨迹；非升空形态为空。 */
    val rockets: List<RocketRun>,
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
    // 通用拖尾：所有形状都沿速度反方向拉一段，线形状下面会画更长的一条，这里跳过。
    if (shape != AlertParticleShape.Line && trailPx > 0.5f) {
        val len = kotlin.math.hypot(vx, vy)
        if (len > 0.01f) {
            val tail = Offset(
                center.x - vx / len * trailPx,
                center.y - vy / len * trailPx,
            )
            drawLine(
                color = color.copy(alpha = color.alpha * trailAlpha.coerceIn(0f, 1f)),
                start = tail,
                end = center,
                strokeWidth = (radiusPx * 0.9f).coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
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
            RevealPattern.Icon -> {
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
            RevealPattern.Text -> {
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
 * 全屏着色（舞台窗）：平铺闪光 / 径向暗角 / 环形缩放脉冲。
 *
 * 效果窗是全屏穿透的，不能真的缩放其它内容；环形用径向亮环模拟「向外顶一下」，
 * 观感与「屏幕像被轻推」一致，且不依赖任何应用内容。
 */
@Composable
private fun WashLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as WashSpec
    val accent = palette.at(0)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        val t = (now / layer.cycleMillis) % 1f
        val wave = if (spec.breath) sin(PI.toFloat() * t).coerceAtLeast(0f) else 1f - t
        val alpha = (layer.brightness * wave * envelope).coerceIn(0f, 1f)
        if (alpha <= 0.003f) return@Canvas
        when (spec.shape) {
            WashShape.Flat -> drawRect(color = accent.copy(alpha = alpha * 0.8f))
            WashShape.Radial -> {
                val soft = spec.softness.coerceIn(0.05f, 1f)
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
            WashShape.Ring -> {
                val scale = 1f + spec.zoomPercent.coerceIn(0, 40) / 100f * t.easedOut(2f)
                val radius = maxOf(size.width, size.height) * 0.42f * scale
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0f),
                            0.65f to accent.copy(alpha = alpha * 0.35f),
                            0.92f to accent.copy(alpha = alpha),
                            1f to accent.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}

/**
 * 全屏覆盖（关闭屏幕的遮罩形态）：圆盘 / 圆瞳 / 中间亮缝 / 百叶 / 碎片。
 *
 * 五种遮罩共用一套时序；形状参数（条数 / 片数 / 亮缝 / 边缘）全在 [CoverSpec] 里。
 * 这一类**不套尾部淡出**：最后必须真的盖满黑，否者锁屏前会“亮一下”。
 */
@Composable
private fun CoverLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val spec = layer.spec as CoverSpec
    val accent = palette.at(0)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val amplitude = (spec.amplitude * layer.brightness).coerceIn(0f, 1f)
        when (spec.mask) {
            CoverMask.Circle -> drawCoverCircle(t, amplitude, spec.curve, spec.softness)
            CoverMask.Iris -> drawCoverIris(t, amplitude, spec.curve, spec.rimDp, accent)
            CoverMask.Slit -> drawCoverSlit(t, amplitude, spec.curve, spec.slitDp)
            CoverMask.Bands -> drawCoverBands(t, amplitude, spec.curve, spec.bands)
            CoverMask.Shards -> drawCoverShards(
                t,
                amplitude,
                accent,
                spec.softness,
                spec.shards,
                layer.presetId?.hashCode() ?: 0x5A7E,
            )
        }
    }
}

/** 圆盘扩散：半径覆盖后直接铺满黑，四角不再透。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverCircle(
    t: Float,
    amplitude: Float,
    curve: Float,
    softness: Float,
) {
    val cover = t.easedOut(curve.coerceIn(1f, 4f))
    val center = Offset(size.width / 2f, size.height / 2f)
    val farthest = hypot(size.width / 2f, size.height / 2f)
    val radius = farthest * cover
    val alpha = (amplitude * (0.35f + 0.65f * cover)).coerceIn(0f, 1f)
    if (alpha <= 0.004f) return
    if (cover >= 0.995f || radius >= farthest - 0.5f) {
        drawRect(color = Color.Black.copy(alpha = amplitude.coerceIn(0f, 1f)))
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

/** 圆瞳收束：黑色遮罩留圆形透明孔，孔径收到 0；边缘强调色描边。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverIris(
    t: Float,
    amplitude: Float,
    curve: Float,
    rimDp: Float,
    accent: Color,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val farthest = hypot(size.width / 2f, size.height / 2f)
    val radius = farthest * (1f - t.easedOut(curve.coerceIn(1f, 4f))).pow(1.4f)
    val alpha = amplitude.coerceIn(0f, 1f)
    if (radius <= 0.5f) {
        drawRect(color = Color.Black.copy(alpha = alpha))
        return
    }
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(0f, 0f, size.width, size.height))
        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
    }
    drawPath(path = path, color = Color.Black.copy(alpha = alpha))
    val rimAlpha = ((1f - t) * 0.8f).coerceIn(0f, 1f)
    if (rimAlpha > 0.01f && rimDp > 0f) {
        drawCircle(
            color = accent.copy(alpha = rimAlpha),
            radius = radius,
            center = center,
            style = Stroke(width = rimDp.dp.toPx()),
        )
    }
}

/** 中间亮缝：上下黑幕夹住一条亮缝，缝收窄到 0 后只留中心光点。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverSlit(
    t: Float,
    amplitude: Float,
    curve: Float,
    slitDp: Float,
) {
    val centerY = size.height / 2f
    val open = (1f - t.easedOut(curve.coerceIn(1f, 4f))).coerceIn(0f, 1f)
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
        val thickness = (slitDp * (0.2f + 0.8f * open)).dp.toPx()
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

/** 百叶：奇偶条带从上下两端交错合拢。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverBands(
    t: Float,
    amplitude: Float,
    curve: Float,
    count: Int,
) {
    val bands = count.coerceIn(3, 15)
    val bandHeight = size.height / bands
    val alpha = amplitude.coerceIn(0f, 1f)
    val ease = curve.coerceIn(1f, 4f)
    for (index in 0 until bands) {
        val local = ((t - index * 0.045f) / 0.68f).coerceIn(0f, 1f).easedOut(ease)
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

/** 碎片：先放射裂纹，再让黑色碎片从中心压满整屏。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverShards(
    t: Float,
    amplitude: Float,
    accent: Color,
    softness: Float,
    count: Int,
    seed: Int,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val farthest = hypot(size.width / 2f, size.height / 2f) * 1.15f
    val random = Random(seed)
    val shardCount = count.coerceIn(6, 30)
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
                    center.x + cos(angle) * length,
                    center.y + sin(angle) * length,
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
            lineTo(center.x + cos(a0) * farthest * scale, center.y + sin(a0) * farthest * scale)
            lineTo(center.x + cos(a1) * farthest * scale, center.y + sin(a1) * farthest * scale)
            close()
        }
        drawPath(path = path, color = Color.Black.copy(alpha = amplitude))
    }
    if (t >= 0.985f) {
        drawRect(color = Color.Black.copy(alpha = amplitude))
    } else {
        val edgeAlpha = (1f - shardProgress) * (0.2f + softness.coerceIn(0f, 1f) * 0.5f)
        if (edgeAlpha > 0.01f) {
            for (index in 0 until shardCount) {
                val angle = angles[index]
                drawLine(
                    color = accent.copy(alpha = edgeAlpha),
                    start = center,
                    end = Offset(
                        center.x + cos(angle) * farthest * scale,
                        center.y + sin(angle) * farthest * scale,
                    ),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

/**
 * 屏缘族：`EdgeSpec.shape` 决定当前形态，同一时刻只画一种（不叠加）。
 */
@Composable
private fun EdgeLayer(
    layer: AlertLayerPlan,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    val spec = layer.spec as EdgeSpec
    when (spec.shape) {
        EdgeShape.Breathing -> EdgeBreathingLayer(layer, spec, palette, clock)
        EdgeShape.Sweep -> EdgeSweepLayer(layer, spec, palette, clock)
        EdgeShape.Wave -> EdgeWaveLayer(layer, spec, palette, clock)
        EdgeShape.Lightning -> EdgeLightningLayer(layer, spec, palette, clock, seed)
    }
}

/**
 * 呼吸光：整圈只做亮度呼吸，不移动。AGSL 距离场保证圆角处连续；
 * CPU 回退沿周长分段描边，不会退化成点阵。
 */
@Composable
private fun EdgeBreathingLayer(
    layer: AlertLayerPlan,
    spec: EdgeSpec,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val shader = remember(spec.backend) {
        if (spec.backend == AlertRenderBackend.Cpu) {
            null
        } else {
            createShader(EDGE_GLOW_SHADER, "edgeBreath")
        }
    }
    val paint = remember { Paint() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val wave = sin(PI.toFloat() * t).coerceAtLeast(0f)
        val pulse = 1f - spec.breathAmount + spec.breathAmount * wave
        val intensity = (layer.brightness * pulse * envelope).coerceIn(0f, 1f)
        if (intensity <= 0.003f) return@Canvas
        val cornerPx = spec.cornerRadiusDp.dp.toPx()
        val depthPx = spec.depthDp.dp.toPx()
        val activeShader = shader
        if (activeShader != null) {
            activeShader.setFloatUniform("resolution", size.width, size.height)
            activeShader.setFloatUniform("cornerRadius", cornerPx)
            activeShader.setFloatUniform("falloff", depthPx)
            activeShader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
            activeShader.setFloatUniform("hardness", spec.hardness)
            activeShader.setFloatUniform("intensity", intensity)
            setPaletteUniforms(activeShader, palette)
            paint.shader = activeShader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
            return@Canvas
        }
        // CPU 回退：整圈均匀亮度，按周长取色；描边宽度 = 发光深度。
        val perimeter = EdgePerimeter(size, depthPx * 0.5f, cornerPx)
        val samples = 240
        for (index in 0 until samples) {
            val f = index.toFloat() / samples
            val a = perimeter.point(f)
            val b = perimeter.point((index + 1).toFloat() / samples)
            drawLine(
                color = palette.sample(f).copy(alpha = intensity),
                start = a,
                end = b,
                strokeWidth = depthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 滚动光带：头 + 拖尾沿路径移动。圆角矩形周长优先走 AGSL 距离场；
 * 竖 / 横路径、强制 CPU 或噪声放电时走分段描边（圆头相接，不出现圆球串）。
 */
@Composable
private fun EdgeSweepLayer(
    layer: AlertLayerPlan,
    spec: EdgeSpec,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val shader = remember(spec.backend) {
        if (spec.backend == AlertRenderBackend.Cpu) {
            null
        } else {
            createShader(EDGE_FLOW_SHADER, "edgeSweep")
        }
    }
    val paint = remember { Paint() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        // 滚动形态不套整体淡入淡出：那会被读成「呼吸」，与用户的形态切换冲突。
        val envelope = 1f
        val t = (now / layer.cycleMillis) % 1f
        val inset = EdgeFrameTuning.InsetDp.dp.toPx()
        val depthPx = spec.depthDp.dp.toPx()
        val cornerPx = spec.cornerRadiusDp.dp.toPx()
        val perimeter = if (spec.path == BandPath.Perimeter) {
            EdgePerimeter(size, inset, cornerPx)
        } else {
            null
        }
        val shaderGeometry = perimeter != null && spec.noise <= 0.001f
        val activeShader = shader
        if (activeShader != null && shaderGeometry && perimeter != null) {
            val intensity = (layer.brightness * envelope).coerceIn(0f, 1f)
            if (intensity <= 0.003f) return@Canvas
            val pathLength = perimeter.length.coerceAtLeast(1f)
            val headFrac = (spec.headWidthDp.dp.toPx() / pathLength).coerceIn(0.004f, 0.25f)
            val moved = (t * spec.travel.coerceIn(0f, 1f)) % 1f
            val pos = if (spec.reverse) 1f - moved else moved
            activeShader.setFloatUniform("resolution", size.width, size.height)
            activeShader.setFloatUniform("cornerRadius", cornerPx)
            activeShader.setFloatUniform("falloff", depthPx)
            activeShader.setFloatUniform("outerFalloff", EdgeGlowTuning.OuterFalloffDp.dp.toPx())
            activeShader.setFloatUniform("hardness", spec.hardness)
            activeShader.setFloatUniform("intensity", intensity)
            activeShader.setFloatUniform("headPos", pos)
            activeShader.setFloatUniform("headPos2", if (spec.dual) (1.5f - pos) % 1f else 0f)
            activeShader.setFloatUniform("coreSigma", headFrac)
            activeShader.setFloatUniform("trailSigma", spec.tailLength)
            activeShader.setFloatUniform("trailAlpha", spec.tailAlpha)
            activeShader.setFloatUniform("tailPower", spec.tailFalloff)
            activeShader.setFloatUniform("headBoost", spec.headBoost)
            activeShader.setFloatUniform("trailDir", if (spec.reverse) 1f else -1f)
            activeShader.setFloatUniform("dualOffset", if (spec.dual) 0.5f else 2f)
            activeShader.setFloatUniform("dualSecond", 0f)
            activeShader.setFloatUniform("trailBack2", -1f)
            val accent = palette.colors[0]
            val second = palette.colors.getOrElse(1) { accent }
            activeShader.setColorUniform("color0", accent.copy(alpha = 1f).toArgb())
            activeShader.setColorUniform("color1", second.copy(alpha = 1f).toArgb())
            setPaletteUniforms(activeShader, palette)
            paint.shader = activeShader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
            return@Canvas
        }

        // CPU 回退 / 非圆角矩形路径：沿路径逐段描边，圆头相接，避免变成圆球串。
        val samples = if (perimeter != null) 240 else 160
        val pathLength = (perimeter?.length ?: maxOf(size.width, size.height)).coerceAtLeast(1f)
        val headFrac = (spec.headWidthDp.dp.toPx() / pathLength).coerceIn(0.004f, 0.5f)
        val movedHead = (t * spec.travel.coerceIn(0f, 1f)) % 1f
        val head = if (spec.reverse) 1f - movedHead else movedHead
        val sides = if (perimeter != null) 1 else 2
        val strokeWidth = (depthPx * 0.9f).coerceAtLeast(1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        for (side in 0 until sides) {
            var previous: Offset? = null
            var previousAlpha = 0f
            var previousColor = palette.sample(0f)
            for (index in 0..samples) {
                val f = index.toFloat() / samples
                val forward = bandProfile(headDelta(head, f, spec.reverse), headFrac, spec)
                val second = if (spec.dual) {
                    bandProfile(headDelta((head + 0.5f) % 1f, f, spec.reverse), headFrac, spec)
                } else {
                    0f
                }
                val alpha = (maxOf(forward, second) * layer.brightness * envelope)
                    .coerceIn(0f, 1f)
                val base = bandPoint(spec.path, f, size, inset, perimeter)
                val mirrored = if (side == 0) base else mirrorBandPoint(base, spec.path, size)
                val normal = when {
                    perimeter != null -> {
                        val dx = mirrored.x - center.x
                        val dy = mirrored.y - center.y
                        val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                        Offset(dx / length, dy / length)
                    }
                    spec.path == BandPath.Vertical -> Offset(if (side == 0) 1f else -1f, 0f)
                    else -> Offset(0f, if (side == 0) 1f else -1f)
                }
                val jitter = if (spec.noise > 0.001f) {
                    sin(index * 1.7f + now * 0.06f) * depthPx * 0.9f * spec.noise
                } else {
                    0f
                }
                val point = mirrored + normal * jitter
                val color = palette.sample(f)
                if (previous != null && previousAlpha > 0.008f && alpha > 0.008f) {
                    val segmentAlpha = minOf(previousAlpha, alpha)
                    val blend = Color(
                        red = (previousColor.red + color.red) / 2f,
                        green = (previousColor.green + color.green) / 2f,
                        blue = (previousColor.blue + color.blue) / 2f,
                        alpha = segmentAlpha,
                    )
                    drawLine(
                        color = blend,
                        start = previous,
                        end = point,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
                previous = point
                previousAlpha = alpha
                previousColor = color
            }
        }
    }
}

/** 路径采样：周长 / 左竖线 / 上横线；竖横线的另一半由 [mirrorBandPoint] 镜像。 */
private fun bandPoint(
    path: BandPath,
    fraction: Float,
    size: Size,
    inset: Float,
    perimeter: EdgePerimeter?,
): Offset = when (path) {
    BandPath.Perimeter -> perimeter?.point(fraction) ?: Offset.Zero
    BandPath.Vertical -> Offset(inset, inset + fraction * (size.height - inset * 2f))
    BandPath.Horizontal -> Offset(inset + fraction * (size.width - inset * 2f), inset)
}

/** 竖 / 横路径的对面镜像点。 */
private fun mirrorBandPoint(point: Offset, path: BandPath, size: Size): Offset = when (path) {
    BandPath.Perimeter -> point
    BandPath.Vertical -> Offset(size.width - point.x, point.y)
    BandPath.Horizontal -> Offset(point.x, size.height - point.y)
}

/** 路径上的「离头部多远」（0 = 头部，越大越靠后）。 */
private fun headDelta(head: Float, fraction: Float, reverse: Boolean): Float {
    val signed = if (reverse) fraction - head else head - fraction
    return ((signed % 1f) + 1f) % 1f
}

/** 头部 + 拖尾的强度剖面：头亮、尾按 falloff 衰减。 */
private fun bandProfile(delta: Float, headFrac: Float, spec: EdgeSpec): Float {
    if (delta < headFrac) {
        val k = 1f - delta / headFrac
        return (0.72f + 0.28f * k) * (1f + spec.headBoost * k).coerceAtMost(1.6f)
    }
    val tail = spec.tailLength.coerceIn(0f, 0.9f)
    if (tail <= 0.001f) return 0f
    val behind = delta - headFrac
    if (behind >= tail) return 0f
    val k = 1f - behind / tail
    return spec.tailAlpha * k.pow(spec.tailFalloff.coerceIn(0.5f, 4f))
}

/**
 * 屏缘波纹：等距波峰沿周长起伏。优先走 AGSL 距离场，CPU 回退按波峰高斯采样。
 */
@Composable
private fun EdgeWaveLayer(
    layer: AlertLayerPlan,
    spec: EdgeSpec,
    palette: AlertPaletteColors,
    clock: () -> Float,
) {
    val shader = remember(spec.backend) {
        if (spec.backend == AlertRenderBackend.Cpu) {
            null
        } else {
            createShader(EDGE_RIPPLE_SHADER, "edgeWave")
        }
    }
    val paint = remember { Paint() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        // 波纹同样不套整体淡入淡出，避免与「呼吸」混淆。
        val envelope = 1f
        val inset = EdgeFrameTuning.InsetDp.dp.toPx()
        val cornerPx = spec.cornerRadiusDp.dp.toPx()
        val depthPx = spec.depthDp.dp.toPx()
        val waveWidthPx = spec.waveWidthDp.dp.toPx()
        val perimeter = EdgePerimeter(size, inset, cornerPx)
        val crests = spec.crests.coerceIn(2, 6)
        val travel = (now / layer.cycleMillis) % 1f
        val activeShader = shader
        if (activeShader != null) {
            val sigma = (waveWidthPx / perimeter.length.coerceAtLeast(1f)).coerceIn(0.004f, 0.2f)
            activeShader.setFloatUniform("resolution", size.width, size.height)
            activeShader.setFloatUniform("cornerRadius", cornerPx)
            activeShader.setFloatUniform("inset", inset)
            activeShader.setFloatUniform("crestCount", crests.toFloat())
            activeShader.setFloatUniform("sigma", sigma)
            activeShader.setFloatUniform("headPos", travel)
            activeShader.setFloatUniform("bandWidth", depthPx)
            activeShader.setFloatUniform("hardness", spec.hardness)
            activeShader.setFloatUniform("intensity", layer.brightness * envelope)
            setPaletteUniforms(activeShader, palette)
            paint.shader = activeShader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
            return@Canvas
        }
        // CPU 回退：波峰高斯采样；半径随采样间距放大，避免小深度时出现断点。
        val samples = 360
        val spacing = perimeter.length / samples
        val radius = maxOf(depthPx * 0.55f, spacing * 0.7f)
        val sigma = (waveWidthPx / perimeter.length.coerceAtLeast(1f)).coerceIn(0.002f, 0.2f)
        for (index in 0 until samples) {
            val f = index.toFloat() / samples
            val x = (f - travel) * crests
            val nearest = kotlin.math.round(x)
            val distance = kotlin.math.abs(x - nearest) / crests
            val sn = distance / sigma
            val wave = kotlin.math.exp(-0.5f * sn.pow(spec.hardness))
            val alpha = (layer.brightness * wave * envelope).coerceIn(0f, 1f)
            if (alpha <= 0.004f) continue
            drawCircle(
                color = palette.sample(f).copy(alpha = alpha),
                radius = radius,
                center = perimeter.point(f),
            )
        }
    }
}

/**
 * 屏缘雷电：多道独立落点的折线放电，向屏幕内短促炸开；每一道有自己的闪断窗口。
 * 顶点抖动由播放种子决定，同种子可复现。
 */
@Composable
private fun EdgeLightningLayer(
    layer: AlertLayerPlan,
    spec: EdgeSpec,
    palette: AlertPaletteColors,
    clock: () -> Float,
    seed: Int,
) {
    if (spec.bolts <= 0) return
    val path = remember { androidx.compose.ui.graphics.Path() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val t = (now / layer.cycleMillis) % 1f
        val inset = EdgeFrameTuning.InsetDp.dp.toPx()
        val perimeter = EdgePerimeter(size, inset, spec.cornerRadiusDp.dp.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        val flashes = spec.boltFlashes.coerceIn(1, 8)
        val boltsPerFlash = spec.bolts.coerceIn(1, 8)
        val window = spec.boltWindow.coerceIn(0.05f, 0.9f)
        val reach = spec.boltReachPercent.coerceIn(10, 90) / 100f
        val steps = 7
        // 闪断次数（flashes）与每次的道数（bolts）分开：调道数不再连带改频率。
        repeat(flashes) { flash ->
            val local = ((t * flashes - flash) % 1f + 1f) % 1f
            if (local > window) return@repeat
            val fade = 1f - local / window
            val alpha = (layer.brightness * fade * envelope).coerceIn(0f, 1f)
            if (alpha <= 0.01f) return@repeat
            repeat(boltsPerFlash) { bolt ->
                val anchor = ((seed * 0.0001f + flash * 0.37f + bolt * 0.11f) % 1f + 1f) % 1f
                val start = perimeter.point(anchor)
                path.reset()
                path.moveTo(start.x, start.y)
                var px = start.x
                var py = start.y
                val dx = center.x - start.x
                val dy = center.y - start.y
                val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                for (step in 1..steps) {
                    val f = step.toFloat() / steps
                    val tx = start.x + dx * f * reach
                    val ty = start.y + dy * f * reach
                    val jitter = noise01(seed + flash * 97 + bolt * 131, step * 1.7f) - 0.5f
                    px = tx - (dy / length) * jitter * size.minDimension * 0.045f
                    py = ty + (dx / length) * jitter * size.minDimension * 0.045f
                    path.lineTo(px, py)
                }
                val color = palette.sample(anchor)
                drawPath(
                    path = path,
                    color = color.copy(alpha = alpha * 0.35f),
                    style = Stroke(width = spec.boltWidthDp.dp.toPx() * 3.2f),
                )
                drawPath(
                    path = path,
                    color = color.copy(alpha = alpha),
                    style = Stroke(width = spec.boltWidthDp.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun RingLayer(
    layer: AlertLayerPlan,
    anchor: AlertAnchor,
    palette: AlertPaletteColors,
    clock: () -> Float,
    anchorProvider: () -> AlertAnchor,
) {
    val spec = layer.spec as RingSpec
    val peak = layer.brightness
    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = clock()
        if (now >= layer.totalMillis) return@Canvas
        val envelope = envelopeAt(now, layer.totalMillis, AlertEnvelopeShare)
        if (envelope <= 0.005f) return@Canvas
        val live = anchorProvider()
        val center = if (spec.centerAtAnchor) {
            Offset(live.centerX, live.centerY)
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }
        val startRadius = if (spec.centerAtAnchor) live.capsulePx / 2f * 0.9f else 0f
        val travelPx = live.capsulePx * spec.travelPercent.coerceIn(0, 300) / 100f
        val strokePx = spec.strokeWidthDp.dp.toPx()
        val haloPx = strokePx * 3.4f * spec.haloScale
        val totalRings = (layer.cycles * spec.ringsPerCycle).coerceAtLeast(1)
        val ringOffsetMillis = layer.cycleMillis.toFloat() / spec.ringsPerCycle.coerceAtLeast(1)
        for (index in 0 until totalRings) {
            val progress = (now - index * ringOffsetMillis) / layer.cycleMillis
            if (progress <= 0f || progress >= 1f) continue
            val alpha = (peak * (1f - progress).pow(1.35f) * envelope).coerceIn(0f, 1f)
            if (alpha <= 0.005f) continue
            val radius = (startRadius + travelPx * progress.easedOut(2f)).coerceAtLeast(1f)
            val color = palette.at(index)
            when (spec.style) {
                RingStyle.Nested -> {
                    if (haloPx > 0f && spec.haloScale > 0f) {
                        drawCircle(
                            color = color.copy(alpha = alpha * 0.3f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = haloPx, cap = StrokeCap.Round),
                        )
                    }
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
                RingStyle.ThreeStroke -> {
                    drawCircle(
                        color = color.copy(alpha = alpha * 0.35f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokePx * 3.4f),
                    )
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokePx * 1.4f),
                    )
                    drawCircle(
                        color = color.copy(alpha = alpha * 0.5f),
                        radius = (radius - strokePx * 1.2f).coerceAtLeast(0f),
                        center = center,
                        style = Stroke(width = strokePx * 0.8f),
                    )
                }
                RingStyle.Halo -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to color.copy(alpha = 0f),
                                0.62f to color.copy(alpha = 0f),
                                0.82f to color.copy(alpha = alpha * 0.75f),
                                1f to color.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = radius * (1f + spec.haloScale).coerceAtLeast(1.05f),
                        ),
                        radius = radius * (1f + spec.haloScale).coerceAtLeast(1.05f),
                        center = center,
                    )
                }
            }
        }
    }
}

private class EdgePerimeter(
    private val size: Size,
    private val inset: Float,
    cornerRadius: Float,
) {
    private val w = size.width - inset * 2
    private val h = size.height - inset * 2
    private val r = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
    private val sx = (w / 2f - r).coerceAtLeast(0f)
    private val sy = (h / 2f - r).coerceAtLeast(0f)
    private val arc = (PI.toFloat() / 2f) * r
    private val total: Float = 4f * sx + 4f * sy + 2f * PI.toFloat() * r

    /** 周长（px），拖尾长度按它折算比例。 */
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
            val a = -PI.toFloat() / 2f + d / arc * (PI.toFloat() / 2f)
            return Offset(right - r + r * cos(a), top + r + r * sin(a))
        }
        d -= arc
        val seg2 = 2f * sy
        if (d < seg2) return Offset(right, top + r + d)
        d -= seg2
        if (d < arc) {
            val a = d / arc * (PI.toFloat() / 2f)
            return Offset(right - r + r * cos(a), bottom - r + r * sin(a))
        }
        d -= arc
        val seg4 = 2f * sx
        if (d < seg4) return Offset(right - r - d, bottom)
        d -= seg4
        if (d < arc) {
            val a = PI.toFloat() / 2f + d / arc * (PI.toFloat() / 2f)
            return Offset(left + r + r * cos(a), bottom - r + r * sin(a))
        }
        d -= arc
        val seg6 = 2f * sy
        if (d < seg6) return Offset(left, bottom - r - d)
        d -= seg6
        val a = PI.toFloat() + (d / arc).coerceIn(0f, 1f) * (PI.toFloat() / 2f)
        return Offset(left + r + r * cos(a), top + r + r * sin(a))
    }
}

/** 归一化周长差：环面最小距离（0..0.5）。 */
private fun perimeterDist(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 1f
    return minOf(d, 1f - d)
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
