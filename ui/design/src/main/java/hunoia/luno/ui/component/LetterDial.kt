package hunoia.luno.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.LetterDialApexRise
import hunoia.luno.ui.theme.LetterDialArcBandWidth
import hunoia.luno.ui.theme.LetterDialArcVisibleCount
import hunoia.luno.ui.theme.LetterDialCollapseGraceMillis
import hunoia.luno.ui.theme.LetterDialEdgeBandFraction
import hunoia.luno.ui.theme.LetterDialEdgeSpeedFloor
import hunoia.luno.ui.theme.LetterDialEdgeSpeedMax
import hunoia.luno.ui.theme.LetterDialDragGainFast
import hunoia.luno.ui.theme.LetterDialDragGainSlow
import hunoia.luno.ui.theme.LetterDialDragSpeedFast
import hunoia.luno.ui.theme.LetterDialDragSpeedSlow
import hunoia.luno.ui.theme.LetterDialLetterInset
import hunoia.luno.ui.theme.LetterDialPointerHeight
import hunoia.luno.ui.theme.LetterDialPointerWidth
import hunoia.luno.ui.theme.LetterDialRowHeight
import hunoia.luno.ui.theme.LetterDialRowItemWidth
import hunoia.luno.ui.theme.LetterDialRowVisibleCount
import hunoia.luno.ui.theme.MotionPress
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 动作选择页底部的字母拨盘。
 *
 * 两态：**收起** = 底栏上方居中一行字母；**展开** = 一大段圆弧（不是圆/半圆）——
 * 弧的两端落在「屏幕左右边缘 × 底栏上线」，圆心水平居中且远在屏幕下方，屏上只露顶部一段弧。
 * 字母落在弧线**内侧**（朝圆心），当前字母固定在弧顶；沿弧横拖/点字母都会滚到该字母
 * （位置动画，不硬跳）。两端渐隐；更靠近圆心（弧下方）的月牙区点按收起。
 *
 * 手势挂在一条**常驻的透明层**上（不随两态重建），所以收起态长按可直接展开并**继续拖动**。
 *
 * 灵敏度按滑动速度分配：慢拖细、快拖粗；拖到左右触发带并停留会自动加速滚字母；用户自己滚列表时拨盘收起。
 */
@Composable
fun LetterDial(
    entries: List<IndexBarEntry>,
    activeKey: String?,
    onJump: (IndexBarEntry) -> Unit,
    scrollInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val currentJump by rememberUpdatedState(onJump)
    val idleIndex = remember(entries, activeKey) {
        entries.indexOfFirst { it.key == activeKey }.takeIf { it >= 0 } ?: 0
    }
    // 条带位置（条目下标，可带小数）。按压/拖动期间由手势驱动，其余时间滚向 idleIndex。
    var stripPos by remember(entries) { mutableFloatStateOf(idleIndex.toFloat()) }
    var dragging by remember(entries) { mutableStateOf(false) }
    var expanded by remember(entries) { mutableStateOf(false) }
    // 本次拖动的分解：手势起点 + 手指位移贡献 + 贴边自动滚贡献。三者分开存，
    // 帧循环与指针事件才能各自累加而不互相覆盖。
    var dragBasePos by remember(entries) { mutableFloatStateOf(0f) }
    var dragManualLetters by remember(entries) { mutableFloatStateOf(0f) }
    var dragAutoLetters by remember(entries) { mutableFloatStateOf(0f) }
    var pointerXPx by remember(entries) { mutableFloatStateOf(0f) }
    var dialWidthPx by remember(entries) { mutableIntStateOf(0) }
    // 平滑后的横向滑动速度（dp/ms）：拖动增益与贴边自动滚的速度系数都读它。
    var dragSpeed by remember(entries) { mutableFloatStateOf(0f) }
    var lastPointerEventAt by remember(entries) { mutableLongStateOf(0L) }
    val lastJumpAt = remember(entries) { AtomicLong(0L) }

    fun jumpTo(index: Int) {
        val target = index.coerceIn(0, entries.lastIndex)
        lastJumpAt.set(SystemClock.uptimeMillis())
        currentJump(entries[target])
    }

    fun commitPosition(pos: Float) {
        val previous = stripPos.roundToInt()
        val clamped = pos.coerceIn(0f, entries.lastIndex.toFloat())
        stripPos = clamped
        val next = clamped.roundToInt()
        if (next != previous) jumpTo(next)
    }

    fun commitDrag() {
        val target = (dragBasePos + dragManualLetters + dragAutoLetters)
            .coerceIn(0f, entries.lastIndex.toFloat())
        dragManualLetters = target - dragBasePos - dragAutoLetters
        commitPosition(target)
    }

    fun speedRamp(): Float = ((dragSpeed - LetterDialDragSpeedSlow) /
        (LetterDialDragSpeedFast - LetterDialDragSpeedSlow)).coerceIn(0f, 1f)

    fun dragGain(): Float =
        LetterDialDragGainSlow + (LetterDialDragGainFast - LetterDialDragGainSlow) * speedRamp()

    // 贴边自动滚：停住只留底速，越滑得快越接近满速。
    fun edgeSpeedFactor(): Float =
        LetterDialEdgeSpeedFloor + (1f - LetterDialEdgeSpeedFloor) * speedRamp()

    LaunchedEffect(idleIndex, dragging, expanded) {
        if (dragging) return@LaunchedEffect
        animate(stripPos, idleIndex.toFloat(), animationSpec = tween(MotionPress)) { value, _ ->
            // 动画重启晚一帧也别把手势写入的位置覆盖回去。
            if (!dragging) stripPos = value
        }
    }
    // 跨字母轻振：只在拨盘展开时触发（含拖动与点选滚动），收起态跟列表滚不震。
    LaunchedEffect(entries, expanded) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { stripPos.roundToInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    // 贴边自动滚：拖到左右触发带时滚字母；速度按越界深度 × 当前滑动速度，松手即停。
    LaunchedEffect(dragging, expanded) {
        if (!dragging || !expanded) return@LaunchedEffect
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val frames = (now - lastFrame) / 16_666_667f
                    val w = dialWidthPx.toFloat()
                    val nowMs = now / 1_000_000L
                    // 手指停住时没有新事件：速度衰减，自动滚回到底速。
                    if (lastPointerEventAt != 0L && nowMs - lastPointerEventAt > 40L) {
                        dragSpeed *= 0.85f
                    }
                    if (w > 0f && frames > 0f) {
                        val band = w * LetterDialEdgeBandFraction
                        val x = pointerXPx
                        val over = when {
                            x < band -> (band - x) / band
                            x > w - band -> (x - (w - band)) / band
                            else -> 0f
                        }
                        if (over > 0f) {
                            val dir = if (x < w / 2f) -1f else 1f
                            // 位置用平方根曲线：进带就有效果，靠边才拉满。
                            val depth = sqrt(over.coerceAtMost(1f))
                            dragAutoLetters += dir * LetterDialEdgeSpeedMax *
                                depth * edgeSpeedFactor() * frames
                            commitDrag()
                        }
                    }
                }
                lastFrame = now
            }
        }
    }

    // 用户自己滚列表（含 fling）就收起拨盘；拨盘刚触发的程序化跳转有短暂宽限。
    LaunchedEffect(scrollInProgress, dragging, expanded) {
        if (!scrollInProgress || dragging || !expanded) return@LaunchedEffect
        while (true) {
            if (!dragging &&
                SystemClock.uptimeMillis() - lastJumpAt.get() >= LetterDialCollapseGraceMillis
            ) {
                expanded = false
                break
            }
            delay(32)
        }
    }

    val rowHeight = LetterDialRowHeight
    val expandedHeight = rowHeight + LetterDialApexRise + rowHeight / 2
    // 透明手势层的高度跟着两态走：收起 = 一行高，展开 = 整段弧高。
    val gestureHeight by animateDpAsState(
        targetValue = if (expanded) expandedHeight else rowHeight,
        animationSpec = tween(MotionPress),
        label = "letterDialGestureHeight",
    )

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = expanded,
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                (fadeIn(tween(MotionPress)) togetherWith fadeOut(tween(MotionPress)))
                    .using(SizeTransform(clip = false))
            },
            label = "letterDial",
        ) { open ->
            if (open) {
                ExpandedArc(entries = entries, stripPos = stripPos)
            } else {
                CollapsedRow(entries = entries, stripPos = stripPos)
            }
        }

        // 常驻透明手势层：两态共用一份手势，收起态长按/横滑可以「展开并继续拖」。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(gestureHeight)
                .onSizeChanged { dialWidthPx = it.width }
                .pointerInput(entries) {
                    awaitEachGesture {
                        val wPx = size.width.toFloat()
                        if (wPx <= 0f) return@awaitEachGesture
                        val itemPx = rowHeight.toPx()
                        val hPx = LetterDialApexRise.toPx()
                        val insetPx = LetterDialLetterInset.toPx()
                        val rPx = (wPx * wPx / 4f + hPx * hPx) / (2f * hPx)
                        val letterRPx = rPx - insetPx
                        val usableHalf = asin(((wPx / 2f - itemPx / 2f) / letterRPx).coerceIn(-1f, 1f))
                        val stepCount = LetterDialArcVisibleCount - 1
                        val stepRad = 2f * usableHalf / stepCount
                        // 灵敏度：一个字母 ≈ 屏上一个字母位的横向宽度，比按弧长更跟手。
                        val stepPx = (wPx - itemPx) / stepCount
                        val centerYPx = itemPx + rPx

                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val wasExpanded = expanded
                        val startX = down.position.x
                        val pointerId = down.id
                        // 速度按手势内的位移/时间算，并做指数平滑：慢拖细、快拖粗。
                        var lastX = startX
                        var lastTime = down.uptimeMillis
                        dragSpeed = 0f
                        lastPointerEventAt = lastTime

                        fun accumulateDrag(x: Float, timeMillis: Long) {
                            val dx = x - lastX
                            val dt = (timeMillis - lastTime).coerceIn(4L, 50L)
                            val instant = abs(dx) / density / dt
                            dragSpeed = dragSpeed * 0.6f + instant * 0.4f
                            lastPointerEventAt = timeMillis
                            lastX = x
                            lastTime = timeMillis
                            pointerXPx = x
                            // 右滑 → 下标增大 → 朝 Z 滚。
                            dragManualLetters += dx / stepPx * dragGain()
                            commitDrag()
                        }

                        dragBasePos = stripPos
                        dragManualLetters = 0f
                        dragAutoLetters = 0f
                        pointerXPx = startX
                        dragging = true
                        try {
                            if (wasExpanded) {
                                val dist = sqrt(
                                    (down.position.x - wPx / 2f) * (down.position.x - wPx / 2f) +
                                        (down.position.y - centerYPx) * (down.position.y - centerYPx),
                                )
                                // 比字母更靠圆心的区域 = 收起。
                                val collapsible = dist < letterRPx - itemPx * 0.9f
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) break
                                    if (!moved &&
                                        abs(change.position.x - startX) > viewConfiguration.touchSlop
                                    ) {
                                        moved = true
                                    }
                                    if (moved) {
                                        accumulateDrag(change.position.x, change.uptimeMillis)
                                    }
                                    change.consume()
                                }
                                if (!moved) {
                                    if (collapsible) {
                                        expanded = false
                                    } else {
                                        val sinPhi = ((startX - wPx / 2f) / letterRPx).coerceIn(-1f, 1f)
                                        val target = (dragBasePos + asin(sinPhi) / stepRad)
                                            .roundToInt()
                                            .coerceIn(0, entries.lastIndex)
                                        jumpTo(target)
                                    }
                                }
                            } else {
                                // 收起态：等长按或横滑来展开；短按松手也展开。
                                var outcome = 0
                                val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null || !change.pressed) {
                                            outcome = 2 // 松手（点按）
                                            break
                                        }
                                        if (abs(change.position.x - startX) > viewConfiguration.touchSlop) {
                                            change.consume()
                                            outcome = 1 // 横滑
                                            break
                                        }
                                        change.consume()
                                    }
                                    outcome
                                }
                                // null = 长按超时。
                                val expandedByHold = finished == null
                                expanded = true
                                if (expandedByHold || outcome == 1) {
                                    // 保持按住，继续用这次手势拖动。
                                    var moved = outcome == 1
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (!change.pressed) break
                                        if (!moved &&
                                            abs(change.position.x - startX) > viewConfiguration.touchSlop
                                        ) {
                                            moved = true
                                        }
                                        if (moved) {
                                            accumulateDrag(change.position.x, change.uptimeMillis)
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        } finally {
                            dragging = false
                        }
                    }
                },
        )
    }
}

/** 收起态视觉：底栏上方**居中**一行字母，当前字母在正中、两侧渐淡。 */
@Composable
private fun CollapsedRow(
    entries: List<IndexBarEntry>,
    stripPos: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val itemWidth = LetterDialRowItemWidth
    val itemHeight = LetterDialRowHeight
    val visible = LetterDialRowVisibleCount
    val half = (visible - 1) / 2f
    val base = itemWidth * ((visible - 1) / 2f)
    val activeIndex = stripPos.roundToInt()

    // 外层铺满屏宽并居中，内层才是字母行：整行永远屏幕居中。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(itemWidth * visible)
                .height(itemHeight)
                .clipToBounds(),
        ) {
            entries.forEachIndexed { i, entry ->
                val rel = i - stripPos
                val alpha = ((half + 0.5f - abs(rel)) / 1.0f).coerceIn(0f, 1f)
                if (alpha <= 0.01f) return@forEachIndexed
                LetterGlyph(
                    entry = entry,
                    active = i == activeIndex,
                    color = if (i == activeIndex) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .offset(x = base + itemWidth * rel)
                        .size(itemWidth, itemHeight)
                        .alpha(alpha),
                )
            }
        }
    }
}

/**
 * 展开态视觉：一大段圆弧。端点 `(0, 底栏上线)`、`(W, 底栏上线)`，圆心在屏下很远。
 * 半径 `R = (W²/4 + H²) / (2H)`（H = 弧顶抬高），字母落在弧线内侧。
 */
@Composable
private fun ExpandedArc(
    entries: List<IndexBarEntry>,
    stripPos: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val itemWidth = LetterDialRowItemWidth
    val itemHeight = LetterDialRowHeight
    val apexRise = LetterDialApexRise
    val bandWidth = LetterDialArcBandWidth
    val letterInset = LetterDialLetterInset
    val visible = LetterDialArcVisibleCount
    val topPad = itemHeight
    val bottomPad = itemHeight / 2
    val canvasHeight = topPad + apexRise + bottomPad

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .clipToBounds(),
    ) {
        val w = maxWidth.value
        val h = apexRise.value
        val itemH = itemHeight.value
        val itemW = itemWidth.value
        val inset = letterInset.value
        val r = (w * w / 4f + h * h) / (2f * h)
        val letterRadius = r - inset
        val fullHalf = asin(((w / 2f) / r).coerceIn(-1f, 1f))
        val usableHalf = asin(((w / 2f - itemW / 2f) / letterRadius).coerceIn(-1f, 1f))
        val stepRad = 2f * usableHalf / (visible - 1)
        val centerY = topPad.value + r
        val half = (visible - 1) / 2f
        val activeIndex = stripPos.roundToInt()

        // 轨道 + 选中光晕。
        Canvas(Modifier.fillMaxSize()) {
            val path = Path()
            val segments = 64
            for (k in 0..segments) {
                val phi = -fullHalf + 2f * fullHalf * k / segments
                val x = (w / 2f + r * sin(phi)).dp.toPx()
                val y = (centerY - r * cos(phi)).dp.toPx()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val band = scheme.onSurface.copy(alpha = 0.10f)
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.20f to band,
                        0.80f to band,
                        1f to Color.Transparent,
                    ),
                    startX = 0f,
                    endX = size.width,
                ),
                style = Stroke(width = bandWidth.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = scheme.primary.copy(alpha = 0.12f),
                radius = itemH.dp.toPx() * 0.9f,
                center = Offset(
                    (w / 2f).dp.toPx(),
                    (topPad.value + inset).dp.toPx(),
                ),
            )
        }

        // 圆周字母：落在弧线内侧，两端渐隐。
        entries.forEachIndexed { i, entry ->
            val rel = i - stripPos
            val alpha = ((half - abs(rel)) / 0.8f).coerceIn(0f, 1f)
            if (alpha <= 0.01f) return@forEachIndexed
            val phi = rel * stepRad
            if (abs(phi) > usableHalf + stepRad * 0.5f) return@forEachIndexed
            val x = (w / 2f + letterRadius * sin(phi)).dp - itemWidth / 2
            val y = (centerY - letterRadius * cos(phi)).dp - itemHeight / 2
            // 靠两端的字母会低于弧线端、逼近底栏：超出容器就整颗跳过，不裁半截。
            if (y + itemHeight / 2 > canvasHeight) return@forEachIndexed
            LetterGlyph(
                entry = entry,
                active = i == activeIndex,
                color = if (i == activeIndex) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(itemWidth, itemHeight)
                    .alpha(alpha),
            )
        }

        // 弧顶指针：贴在轨道上、指向弧顶当前字母。
        Canvas(Modifier.fillMaxSize()) {
            val cx = (w / 2f).dp.toPx()
            val bandTop = topPad.toPx() - bandWidth.toPx() / 2
            val halfWidth = LetterDialPointerWidth.toPx() / 2
            val apexY = bandTop + 2.dp.toPx()
            val baseY = apexY - LetterDialPointerHeight.toPx()
            val path = Path().apply {
                moveTo(cx - halfWidth, baseY)
                lineTo(cx + halfWidth, baseY)
                lineTo(cx, apexY)
                close()
            }
            drawPath(path, scheme.primary)
        }
    }
}

@Composable
private fun LetterGlyph(
    entry: IndexBarEntry,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}
