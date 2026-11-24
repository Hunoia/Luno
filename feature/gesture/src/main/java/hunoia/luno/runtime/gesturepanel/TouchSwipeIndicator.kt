package hunoia.luno.runtime.gesturepanel

import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.SwipeIndicator
import hunoia.luno.config.model.SwipeIndicatorStyle
import hunoia.luno.gesture.bounds
import hunoia.luno.ui.theme.toMarkColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 触钮所在屏幕边（决定流体从哪条边鼓出来）。 */
internal enum class SwipeIndicatorEdge { Left, Right, Top, Bottom }

/**
 * 一条边的局部坐标系：切向坐标 + 向内深度。
 *
 * [edgeOrigin] 是切向 0 点的屏幕坐标；[point] 把「沿边 t、向内 d」换算成屏幕坐标。
 * 切向坐标对左右边是 y、对上下边是 x，[tangentStart]/[tangentEnd] 是安全区范围。
 */
internal data class EdgeFrame(
    val edgeOrigin: Offset,
    val along: Offset,
    val inward: Offset,
    val tangentStart: Float,
    val tangentEnd: Float,
) {
    fun point(tangent: Float, depth: Float): Offset =
        edgeOrigin + along * tangent + inward * depth
}

internal fun edgeFrame(
    edge: SwipeIndicatorEdge,
    safeLeft: Float,
    safeTop: Float,
    safeRight: Float,
    safeBottom: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
): EdgeFrame {
    // 安全区可能被异常 inset 挤压到负值；切向区间必须有序，否则 coerceIn 会抛异常。
    val verticalStart = minOf(safeTop, screenHeightPx - safeBottom)
    val verticalEnd = maxOf(safeTop, screenHeightPx - safeBottom)
    val horizontalStart = minOf(safeLeft, screenWidthPx - safeRight)
    val horizontalEnd = maxOf(safeLeft, screenWidthPx - safeRight)
    return when (edge) {
        SwipeIndicatorEdge.Left -> EdgeFrame(
            edgeOrigin = Offset(safeLeft, 0f),
            along = Offset(0f, 1f),
            inward = Offset(1f, 0f),
            tangentStart = verticalStart,
            tangentEnd = verticalEnd,
        )
        SwipeIndicatorEdge.Right -> EdgeFrame(
            edgeOrigin = Offset(screenWidthPx - safeRight, 0f),
            along = Offset(0f, 1f),
            inward = Offset(-1f, 0f),
            tangentStart = verticalStart,
            tangentEnd = verticalEnd,
        )
        SwipeIndicatorEdge.Top -> EdgeFrame(
            edgeOrigin = Offset(0f, safeTop),
            along = Offset(1f, 0f),
            inward = Offset(0f, 1f),
            tangentStart = horizontalStart,
            tangentEnd = horizontalEnd,
        )
        SwipeIndicatorEdge.Bottom -> EdgeFrame(
            edgeOrigin = Offset(0f, screenHeightPx - safeBottom),
            along = Offset(1f, 0f),
            inward = Offset(0f, -1f),
            tangentStart = horizontalStart,
            tangentEnd = horizontalEnd,
        )
    }
}

internal fun SwipeIndicatorEdge.tangentOf(point: Offset): Float = when (this) {
    SwipeIndicatorEdge.Left, SwipeIndicatorEdge.Right -> point.y
    SwipeIndicatorEdge.Top, SwipeIndicatorEdge.Bottom -> point.x
}

/** 手指离哪条屏幕边最近（没有按钮 bounds 时的兜底）。 */
internal fun nearestSwipeEdge(
    center: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
): SwipeIndicatorEdge {
    val left = center.x
    val right = screenWidthPx - center.x
    val top = center.y
    val bottom = screenHeightPx - center.y
    val min = minOf(left, right, top, bottom)
    return when (min) {
        left -> SwipeIndicatorEdge.Left
        right -> SwipeIndicatorEdge.Right
        top -> SwipeIndicatorEdge.Top
        else -> SwipeIndicatorEdge.Bottom
    }
}

/**
 * 指示器跟随 **按钮所在边**，不是手指的即时最近边。
 *
 * 触钮是边缘触区，镜像钮用镜像后的 bounds；拖动中不换边，避免手指靠近角时
 * 指示器整条翻转。
 */
internal fun buttonSwipeEdge(
    bounds: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
): SwipeIndicatorEdge {
    val left = bounds.left
    val right = screenWidthPx - bounds.right
    val top = bounds.top
    val bottom = screenHeightPx - bounds.bottom
    val min = minOf(left, right, top, bottom)
    return when (min) {
        left -> SwipeIndicatorEdge.Left
        right -> SwipeIndicatorEdge.Right
        top -> SwipeIndicatorEdge.Top
        else -> SwipeIndicatorEdge.Bottom
    }
}

/**
 * 凸起量由「DOWN 点 → 手指」的总位移驱动（各向同性）。
 *
 * 沿边滑动也会持续把边拉出来；超过 [maxDragPx] 后不再变深，避免长距离拖动时
 * 流体铺满半个屏幕。
 */
internal fun dragDistance(origin: Offset, finger: Offset, maxDragPx: Float): Float {
    if (!origin.x.isFinite() || !origin.y.isFinite()) return 0f
    if (!finger.x.isFinite() || !finger.y.isFinite()) return 0f
    val distance = hypot(finger.x - origin.x, finger.y - origin.y)
    if (!distance.isFinite()) return 0f
    return distance.coerceIn(0f, maxDragPx.coerceAtLeast(0f))
}

/** 本体从安全区边向内的凸起深度。 */
internal fun indicatorPeek(basePeek: Float, bulge: Float, factor: Float): Float =
    (basePeek + bulge * factor).coerceAtLeast(0f)

internal data class DragSpan(val start: Float, val end: Float) {
    val length: Float get() = (end - start).coerceAtLeast(0f)
}

/**
 * 沿边跨度：覆盖 DOWN 点与当前点并各扩出一个基准半径，形成拖尾；
 * 再限制最大拉伸并夹进安全区，避免快速滑动时拉成一条长带或压到系统栏。
 */
internal fun dragSpan(
    originTangent: Float,
    fingerTangent: Float,
    radius: Float,
    maxSpan: Float,
    safeStart: Float,
    safeEnd: Float,
): DragSpan {
    val safeLo = minOf(safeStart, safeEnd)
    val safeHi = maxOf(safeStart, safeEnd)
    val from = minOf(originTangent, fingerTangent)
    val to = maxOf(originTangent, fingerTangent)
    var start = from - radius
    var end = to + radius
    val cap = maxSpan.coerceAtLeast(1f)
    if (end - start > cap) {
        val center = (start + end) / 2f
        start = center - cap / 2f
        end = center + cap / 2f
    }
    if (start < safeLo) {
        end += safeLo - start
        start = safeLo
    }
    if (end > safeHi) {
        start -= end - safeHi
        end = safeHi
    }
    if (start < safeLo) start = safeLo
    if (end < start) end = start
    return DragSpan(start, end)
}

/**
 * 沿边跨度的外扩半径：经典族用一个基准半径做拖尾，胶囊本身是短横条，
 * 用更小的外扩量，免得刚出现就有一百多 dp 长。
 */
internal fun SwipeIndicatorStyle.spanRadius(radius: Float): Float = when (this) {
    SwipeIndicatorStyle.Pill -> radius * 0.55f
    else -> radius
}

/** 各样式基准半径（dp），再乘用户「大小」百分比。 */
internal fun SwipeIndicatorStyle.baseRadiusDp(): Float = when (this) {
    SwipeIndicatorStyle.Pill -> 64f
    SwipeIndicatorStyle.Droplet -> 72f
    SwipeIndicatorStyle.Wave -> 80f
    SwipeIndicatorStyle.Glow -> 92f
    SwipeIndicatorStyle.Ripple -> 76f
}

/** 刚出现时的最小凸起（dp）：手指还没动也能看出反馈。 */
internal fun SwipeIndicatorStyle.basePeekDp(): Float = when (this) {
    SwipeIndicatorStyle.Pill -> 18f
    else -> 8f
}

/** 位移 → 凸起深度的比例。 */
internal fun SwipeIndicatorStyle.peekFactor(): Float = when (this) {
    SwipeIndicatorStyle.Pill -> 0.5f
    SwipeIndicatorStyle.Droplet -> 0.6f
    SwipeIndicatorStyle.Wave -> 0.55f
    SwipeIndicatorStyle.Glow -> 0.72f
    SwipeIndicatorStyle.Ripple -> 0.5f
}

/**
 * 波浪剖面的深度：以手指位置为峰，向跨度两端平滑收敛，再叠一层正弦起伏。
 *
 * 是「贴着边的一条液面」而不是独立图形：两端深度自动回到 0，永远接在边上。
 */
internal fun waveDepth(
    tangent: Float,
    fingerTangent: Float,
    spanStart: Float,
    spanEnd: Float,
    peek: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
): Float {
    val halfLeft = (fingerTangent - spanStart).coerceAtLeast(1f)
    val halfRight = (spanEnd - fingerTangent).coerceAtLeast(1f)
    val k = if (tangent <= fingerTangent) {
        (tangent - fingerTangent) / halfLeft
    } else {
        (tangent - fingerTangent) / halfRight
    }
    val bell = cos(k * PI.toFloat() / 2f).coerceIn(0f, 1f)
    val shape = bell * bell
    val wobble = sin(tangent / wavelength.coerceAtLeast(1f) * 2f * PI.toFloat() + phase * 2.6f) *
        amplitude * shape
    return (peek * shape + wobble).coerceIn(0f, peek * 1.15f)
}

/** 是否已滑过该触钮的滑动触发距离（px）；点按不亮指示条。 */
internal fun crossedSwipeThreshold(
    origin: Offset,
    finger: Offset,
    triggerDistancePx: Float,
): Boolean {
    if (!origin.x.isFinite() || !origin.y.isFinite()) return false
    if (!finger.x.isFinite() || !finger.y.isFinite()) return false
    val dx = finger.x - origin.x
    val dy = finger.y - origin.y
    return dx * dx + dy * dy >= triggerDistancePx * triggerDistancePx
}

private data class IndicatorSafeInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Composable
private fun rememberSafeInsets(): IndicatorSafeInsets {
    val context = LocalContext.current
    val screenW = DensityProvider.screenWidthPx
    val screenH = DensityProvider.screenHeightPx
    return remember(context, screenW, screenH) {
        val insets = runCatching {
            context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
        }.getOrNull()
        IndicatorSafeInsets(
            left = (insets?.left ?: 0).toFloat(),
            top = (insets?.top ?: 0).toFloat(),
            right = (insets?.right ?: 0).toFloat(),
            bottom = (insets?.bottom ?: 0).toFloat(),
        )
    }
}

/**
 * 触钮滑动指示器：一条被手指拉出来的屏幕边（不是贴在手指上的图形）。
 *
 * - 只有滑过该触钮的 `slideTriggerDistance` 才出现（轻点不闪）；
 * - 边 = 按钮所在边，DOWN 时固定，底座夹在安全区；
 * - 凸起深度由 DOWN 点 → 手指的总位移驱动，沿边跨度拖尾覆盖两点；
 * - 出现时从边里浮出（180ms），抬手沉回边内并淡出（150ms）；
 * - 5 种样式共用同一套几何，只换剖面 / 内部纹理；描边可选，默认纯胶体。
 *
 * 不消费触摸（主 overlay 本身 `FLAG_NOT_TOUCHABLE`），纯绘制。
 */
@Composable
internal fun TouchSwipeIndicator(
    pipeline: GestureSessionPipeline,
    indicator: SwipeIndicator,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val state = pipeline.sideGestureState
    val sourceButton = state.button
    val effectiveButton = state.effectiveButton ?: sourceButton
    val origin = state.origin
    val finger = state.finger
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val screenWidthPx = DensityProvider.screenWidthPx.toFloat()
    val screenHeightPx = DensityProvider.screenHeightPx.toFloat()
    val safe = rememberSafeInsets()

    var visible by remember { mutableStateOf(false) }
    var lastOrigin by remember { mutableStateOf(Offset.Zero) }
    var lastFinger by remember { mutableStateOf(Offset.Zero) }
    var lastEdge by remember { mutableStateOf(SwipeIndicatorEdge.Left) }
    var lastColor by remember { mutableStateOf(Color.White) }
    var lastStroke by remember { mutableStateOf(Color.White) }
    var phase by remember { mutableFloatStateOf(0f) }
    // 0 = 完全沉在边里，1 = 完整凸出；出现 / 抬手都靠它收放。
    val reveal = remember { Animatable(0f) }

    // 关开关 / 离开组合都必须把全屏窗口收回去；否则 1×1 窗口会永久停在全屏。
    DisposableEffect(Unit) { onDispose { onVisibilityChanged(false) } }
    LaunchedEffect(indicator.enabled) {
        if (!indicator.enabled) {
            visible = false
            reveal.snapTo(0f)
            onVisibilityChanged(false)
        }
    }

    if (!indicator.enabled) return

    val sessionActive = sourceButton != null &&
        origin.x.isFinite() && origin.y.isFinite() &&
        finger.x.isFinite() && finger.y.isFinite()
    val edge = effectiveButton?.let {
        buttonSwipeEdge(it.bounds(), screenWidthPx, screenHeightPx)
    } ?: nearestSwipeEdge(finger, screenWidthPx, screenHeightPx)
    val crossed = sessionActive && sourceButton != null &&
        crossedSwipeThreshold(origin, finger, sourceButton.slideTriggerDistance.toFloat())

    if (sessionActive) {
        lastOrigin = origin
        lastFinger = finger
        lastEdge = edge
        lastColor = if (indicator.followButtonColor) {
            sourceButton?.colorSpec?.toMarkColor(scheme) ?: scheme.primary
        } else {
            indicator.color.toMarkColor(scheme)
        }
        // 未选描边色 = 跟随本体，避免默认勾出一条主题色描边。
        lastStroke = if (indicator.strokeColor.isUnset) {
            lastColor
        } else {
            indicator.strokeColor.toMarkColor(scheme)
        }
    }

    LaunchedEffect(sessionActive, crossed) {
        if (crossed) {
            if (!visible) {
                // 先放大窗口再置可见：第一帧就是全屏坐标系，不会先画在 1×1 里被裁掉。
                onVisibilityChanged(true)
                visible = true
                reveal.snapTo(0f)
                reveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = RevealInMs, easing = LinearOutSlowInEasing),
                )
            }
        } else if (!sessionActive && visible) {
            // 缩回边内 + 淡出，不做整条平移回抽。
            reveal.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = RevealOutMs, easing = FastOutLinearInEasing),
            )
            visible = false
            onVisibilityChanged(false)
        } else if (visible && reveal.value < 1f) {
            // 淡出途中又按下（还没过阈值）：立刻收起，别留半透明的残影。
            reveal.snapTo(0f)
            visible = false
            onVisibilityChanged(false)
        }
    }

    // 只有连续动画形态需要相位；胶囊 / 水滴保持静态，别每帧白重组。
    val animatedStyle = indicator.style == SwipeIndicatorStyle.Wave ||
        indicator.style == SwipeIndicatorStyle.Ripple
    LaunchedEffect(visible, animatedStyle) {
        if (!visible || !animatedStyle) return@LaunchedEffect
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L && now > lastFrame) {
                    phase += (now - lastFrame) / 1_000_000_000f
                }
                lastFrame = now
            }
        }
    }

    if (!visible) return

    val progress = reveal.value.coerceIn(0f, 1f)
    if (progress <= 0.001f) return

    val sizeScale = indicator.effectiveSizePercent / 100f
    val radius = with(density) { (indicator.style.baseRadiusDp() * sizeScale).dp.toPx() }
    val basePeek = with(density) { (indicator.style.basePeekDp() * sizeScale).dp.toPx() }
    val maxDrag = with(density) { indicator.effectiveMaxDragDp.dp.toPx() }
    val strokeWidth = with(density) { indicator.effectiveStrokeWidthDp.dp.toPx() }
    val bulge = dragDistance(lastOrigin, lastFinger, maxDrag)
    val peek = indicatorPeek(basePeek, bulge, indicator.style.peekFactor()) * progress
    if (peek <= 0.5f) return

    val frame = edgeFrame(
        edge = lastEdge,
        safeLeft = safe.left,
        safeTop = safe.top,
        safeRight = safe.right,
        safeBottom = safe.bottom,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
    )
    val originTangent = lastEdge.tangentOf(lastOrigin)
        .coerceIn(frame.tangentStart, frame.tangentEnd)
    val fingerTangent = lastEdge.tangentOf(lastFinger)
        .coerceIn(frame.tangentStart, frame.tangentEnd)
    val spanRadius = indicator.style.spanRadius(radius)
    val span = dragSpan(
        originTangent = originTangent,
        fingerTangent = fingerTangent,
        radius = spanRadius,
        maxSpan = spanRadius * 2f + maxDrag,
        safeStart = frame.tangentStart,
        safeEnd = frame.tangentEnd,
    )

    val geometry = FluidGeometry(
        frame = frame,
        span = span,
        originTangent = originTangent,
        fingerTangent = fingerTangent,
        radius = radius,
        peek = peek,
        alpha = 0.92f * progress,
        color = lastColor,
        strokeColor = lastStroke,
        strokeWidthPx = strokeWidth,
        phase = phase,
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawFluid(indicator.style, geometry)
    }
}

private data class FluidGeometry(
    val frame: EdgeFrame,
    val span: DragSpan,
    val originTangent: Float,
    val fingerTangent: Float,
    val radius: Float,
    val peek: Float,
    val alpha: Float,
    val color: Color,
    val strokeColor: Color,
    val strokeWidthPx: Float,
    val phase: Float,
)

private fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)

private fun Path.moveTo(point: Offset) = moveTo(point.x, point.y)

private fun Path.cubicTo(control1: Offset, control2: Offset, end: Offset) =
    cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)

/** 经典鼓包剖面：边上的平滑鼓包，峰在手指切向位置，向两端收敛。 */
private fun classicBody(g: FluidGeometry): Path {
    val control = (g.radius * 0.5f).coerceAtLeast(1f)
    val finger = g.fingerTangent
    return Path().apply {
        moveTo(g.frame.point(g.span.start, 0f))
        cubicTo(
            g.frame.point(finger - control, 0f),
            g.frame.point(finger - control, g.peek),
            g.frame.point(finger, g.peek),
        )
        cubicTo(
            g.frame.point(finger + control, g.peek),
            g.frame.point(finger + control, 0f),
            g.frame.point(g.span.end, 0f),
        )
        close()
    }
}

/** 水滴剖面：宽底贴边，收成一个朝手指方向的尖。 */
private fun dropletBody(g: FluidGeometry): Path {
    val shoulder = (g.radius * 0.62f).coerceAtLeast(1f)
    val finger = g.fingerTangent
    return Path().apply {
        moveTo(g.frame.point(g.span.start, 0f))
        cubicTo(
            g.frame.point(finger - shoulder, 0f),
            g.frame.point(finger - shoulder * 0.18f, g.peek * 0.9f),
            g.frame.point(finger, g.peek),
        )
        cubicTo(
            g.frame.point(finger + shoulder * 0.18f, g.peek * 0.9f),
            g.frame.point(finger + shoulder, 0f),
            g.frame.point(g.span.end, 0f),
        )
        close()
    }
}

/** 波浪剖面：沿边采样液面，两端自动回到边上。 */
private fun waveBody(g: FluidGeometry): Path {
    val steps = ((g.span.length / 3f).toInt()).coerceIn(16, 120)
    val amplitude = g.radius * 0.16f
    val wavelength = (g.radius * 2f).coerceAtLeast(1f)
    return Path().apply {
        moveTo(g.frame.point(g.span.start, 0f))
        for (index in 1..steps) {
            val tangent = g.span.start + g.span.length * (index.toFloat() / steps)
            val depth = waveDepth(
                tangent = tangent,
                fingerTangent = g.fingerTangent,
                spanStart = g.span.start,
                spanEnd = g.span.end,
                peek = g.peek,
                amplitude = amplitude,
                wavelength = wavelength,
                phase = g.phase,
            )
            lineTo(g.frame.point(tangent, depth))
        }
        lineTo(g.frame.point(g.span.end, 0f))
        close()
    }
}

/** 胶囊剖面：贴边的横条，只圆内侧两角，沿边方向随拖尾拉伸。 */
private fun pillBody(g: FluidGeometry): Path {
    val corner = minOf(g.peek * 0.55f, g.span.length / 2f).coerceAtLeast(1f)
    return Path().apply {
        moveTo(g.frame.point(g.span.start, 0f))
        lineTo(g.frame.point(g.span.start, g.peek - corner))
        quadraticBezierTo(
            g.frame.point(g.span.start, g.peek).x,
            g.frame.point(g.span.start, g.peek).y,
            g.frame.point(g.span.start + corner, g.peek).x,
            g.frame.point(g.span.start + corner, g.peek).y,
        )
        lineTo(g.frame.point(g.span.end - corner, g.peek))
        quadraticBezierTo(
            g.frame.point(g.span.end, g.peek).x,
            g.frame.point(g.span.end, g.peek).y,
            g.frame.point(g.span.end, g.peek - corner).x,
            g.frame.point(g.span.end, g.peek - corner).y,
        )
        lineTo(g.frame.point(g.span.end, 0f))
        close()
    }
}

private fun DrawScope.drawFluid(style: SwipeIndicatorStyle, g: FluidGeometry) {
    when (style) {
        SwipeIndicatorStyle.Glow -> drawGlow(g)
        SwipeIndicatorStyle.Ripple -> drawRipple(g)
        SwipeIndicatorStyle.Pill -> drawBody(g, pillBody(g))
        SwipeIndicatorStyle.Droplet -> drawBody(g, dropletBody(g))
        SwipeIndicatorStyle.Wave -> drawBody(g, waveBody(g))
    }
}

private fun DrawScope.drawBody(g: FluidGeometry, path: Path) {
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                g.color.copy(alpha = g.alpha),
                g.color.copy(alpha = g.alpha * 0.45f),
            ),
            start = g.frame.point(g.fingerTangent, 0f),
            end = g.frame.point(g.fingerTangent, g.peek),
        ),
    )
    if (g.strokeWidthPx > 0f) {
        drawPath(
            path = path,
            color = g.strokeColor.copy(alpha = g.alpha),
            style = Stroke(width = g.strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 光晕：圆心在屏幕外，只露一段贴着边的月牙光。 */
private fun DrawScope.drawGlow(g: FluidGeometry) {
    val center = g.frame.point(g.fingerTangent, -(g.radius - g.peek))
    val edgePoint = g.frame.point(g.fingerTangent, 0f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to g.color.copy(alpha = g.alpha * 0.95f),
                0.55f to g.color.copy(alpha = g.alpha * 0.5f),
                1f to g.color.copy(alpha = 0f),
            ),
            center = edgePoint,
            radius = g.radius,
        ),
        radius = g.radius,
        center = center,
    )
}

/** 涟漪：本体是贴边鼓包，同心环裁在本体内，从 DOWN 点向外扩。 */
private fun DrawScope.drawRipple(g: FluidGeometry) {
    val body = classicBody(g)
    clipPath(body) {
        drawPath(body, g.color.copy(alpha = g.alpha * 0.42f))
        val ringCenter = g.frame.point(g.originTangent, 0f)
        val strokeWidth = (g.radius * 0.06f).coerceAtLeast(1.5f)
        for (index in 0..2) {
            val local = (g.phase * 0.9f + index / 3f) % 1f
            val ringAlpha = g.alpha * (1f - local) * 0.6f
            if (ringAlpha <= 0.01f) continue
            drawCircle(
                color = g.color.copy(alpha = ringAlpha),
                radius = g.radius * (0.25f + local * 1.05f),
                center = ringCenter,
                style = Stroke(width = strokeWidth),
            )
        }
    }
    if (g.strokeWidthPx > 0f) {
        drawPath(
            path = body,
            color = g.strokeColor.copy(alpha = g.alpha),
            style = Stroke(width = g.strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private const val RevealInMs = 180
private const val RevealOutMs = 150
