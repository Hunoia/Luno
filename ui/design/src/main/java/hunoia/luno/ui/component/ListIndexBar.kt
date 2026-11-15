package hunoia.luno.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.AppSectionHeaderIconSize
import hunoia.luno.ui.theme.LetterIndexBubbleGap
import hunoia.luno.ui.theme.LetterIndexBubbleSize
import hunoia.luno.ui.theme.LetterIndexHorizontalPadding
import hunoia.luno.ui.theme.LetterIndexIdealItemHeight
import hunoia.luno.ui.theme.LetterIndexItemMaxHeight
import hunoia.luno.ui.theme.LetterIndexItemVerticalPadding
import hunoia.luno.ui.theme.LetterIndexTouchWidth
import hunoia.luno.ui.theme.MotionPress
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 索引条的一个条目（字母 / ⭐ 之类的固定落点）。
 *
 * [key] 用来跟调用方的「当前条目」对齐；[label] 是纯文字条目（字母）的显示与读屏文案；
 * [icon] 非空时条目画图标，[label] 只做读屏文案（应用抽屉的 ⭐ 常用区）。
 */
data class IndexBarEntry(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
)

/** 索引条贴哪一侧：跟随触发点所在的半边，拇指不用跨手。 */
enum class IndexBarSide { Start, End }

/** 窗口最少 / 最多同时显示几个字母（奇数，当前字母在正中）。 */
private const val MinWindow = 3
private const val MaxWindow = 7

/**
 * 窗口高度最多占可用高度的比例：留出上下余量，窗口才能真正「跟手」上下移动。
 * 矮格子里余量不够时退回按整条高度均分（窗口贴满，退化成滚轮）。
 */
private const val WindowHeightFill = 0.7f

/**
 * 索引条的实测尺寸。字号与字宽来自真实测量，不用 dp 猜：
 * 大字体（fontScale 2.0）下条目必须继续长高，否则字会被上下裁掉。
 */
data class IndexBarMetrics(
    /** 放不下三个完整字母时整条不画（调用方也不再为它留内边距）。 */
    val visible: Boolean,
    /** 窗口条目数：全显时 = 条目总数，否则是 3..7 的奇数。 */
    val windowCount: Int,
    val itemHeight: Dp,
    val barWidth: Dp,
    val textStyle: TextStyle,
)

/**
 * 按可用高度与实测字高选窗口大小。两条策略：
 *
 * 1. **全显优先**：高度够把所有条目一次画完时（选择页全屏、全屏抽屉），窗口 = 全部条目，
 *    不用按住滑，一眼看到所有落点；
 * 2. 否则退回「字母更大」的跟手窗口：条目高 ≥ [LetterIndexIdealItemHeight] 时从 3 个
 *    往 [MaxWindow] 加，再高也只放大到 [LetterIndexItemMaxHeight]。
 *
 * 调用方要靠 [IndexBarMetrics.barWidth] 决定给内容补多少内边距，两边算出不同宽度会错位。
 */
@Composable
fun rememberIndexBarMetrics(
    entries: List<IndexBarEntry>,
    availableHeight: Dp,
): IndexBarMetrics {
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val glyph = remember(entries, style, fontScale) {
        var width = 0
        var height = 0
        for (entry in entries) {
            // 图标条目不参与文字测量，按图标尺寸兜底（见下面的 minItemHeight）。
            if (entry.icon != null) continue
            val size = measurer.measure(AnnotatedString(entry.label), style).size
            if (size.width > width) width = size.width
            if (size.height > height) height = size.height
        }
        width to height
    }
    val hidden = IndexBarMetrics(
        visible = false,
        windowCount = MinWindow,
        itemHeight = 0.dp,
        barWidth = 0.dp,
        textStyle = style,
    )
    if (entries.isEmpty()) return hidden

    // 条目高必须同时容得下文字与图标，两者都不裁剪。
    val iconSize = AppSectionHeaderIconSize
    val minItemHeight = maxOf(
        with(density) { glyph.second.toDp() },
        iconSize,
    ) + LetterIndexItemVerticalPadding * 2

    // 全显优先：按整条高度均分，能全画下就不做跟手窗口。
    val fullItemHeight = availableHeight / entries.size
    if (fullItemHeight >= minItemHeight) {
        return metricsOf(
            count = entries.size,
            itemHeight = fullItemHeight.coerceAtMost(LetterIndexItemMaxHeight),
            glyph = glyph,
            iconSize = iconSize,
            style = style,
            density = density,
        )
    }

    if (availableHeight / MinWindow < minItemHeight) return hidden

    // 字母数按"理想条目高"从少往多试：优先字母更大，高度够才多显示几个。
    val maxWindow = minOf(MaxWindow, entries.size)
    var count = MinWindow
    while (count + 2 <= maxWindow &&
        availableHeight * WindowHeightFill / (count + 2) >= LetterIndexIdealItemHeight
    ) {
        count += 2
    }
    var itemHeight = (availableHeight * WindowHeightFill / count).coerceAtMost(LetterIndexItemMaxHeight)
    if (itemHeight < minItemHeight) {
        // 余量放不下：退回整条高度均分（窗口贴满，仍能完整显示字形）。
        itemHeight = (availableHeight / count).coerceAtMost(LetterIndexItemMaxHeight)
    }
    if (itemHeight < minItemHeight) return hidden
    return metricsOf(
        count = count,
        itemHeight = itemHeight,
        glyph = glyph,
        iconSize = iconSize,
        style = style,
        density = density,
    )
}

private fun metricsOf(
    count: Int,
    itemHeight: Dp,
    glyph: Pair<Int, Int>,
    iconSize: Dp,
    style: TextStyle,
    density: androidx.compose.ui.unit.Density,
): IndexBarMetrics {
    // 无底纯文字，但宽度必须容得下最宽的字母与图标，否则 W / M / ⭐ 会被横向裁边。
    val glyphWidth = with(density) { glyph.first.toDp() }
    val barWidth = maxOf(
        LetterIndexTouchWidth,
        maxOf(glyphWidth, iconSize) + LetterIndexHorizontalPadding * 2,
    )
    return IndexBarMetrics(
        visible = true,
        windowCount = count,
        itemHeight = itemHeight,
        barWidth = barWidth,
        textStyle = style,
    )
}

/**
 * 列表的索引条：**高度够就把所有条目一次画完**（全屏选择页常见），不够时只显示当前
 * 附近的 3–7 个条目，窗口跟手滑动，进出窗口的条目按距离渐隐；当前条目另有气泡
 * （手指压着的地方看不到字）。
 *
 * 映射仍是「整条高度比例取条目」：拖满全高走完首项→末项，因此快速跳转不需要长距离拖动。
 * 显示吸附到整条目——位置动画走 [MotionPress]，不会停在两个条目中间。
 *
 * 只接受实际存在的条目（由调用方传入），所以每次跳转都有落点。
 * 条目低于 36dp 属于 ui-guidelines §1.6 第 ③ 类紧凑豁免，前提是字形完整不裁剪
 * （下限按实测字高定），且列表本身仍可直接滚动。
 */
@Composable
fun IndexBar(
    entries: List<IndexBarEntry>,
    metrics: IndexBarMetrics,
    /** 当前可见条目（⭐ 或字母），用于未按压时窗口居中在哪一项。 */
    activeKey: String?,
    side: IndexBarSide,
    onJump: (IndexBarEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty() || !metrics.visible) return
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val currentJump by rememberUpdatedState(onJump)
    // -1 = 未按压：窗口回到当前条目。
    var pressedIndex by remember(entries) { mutableIntStateOf(-1) }
    // 按压点（px）。跟手滑动时窗口贴着指尖，不用条目下标反推位置。
    var fingerYPx by remember { mutableFloatStateOf(Float.NaN) }

    val idleIndex = remember(entries, activeKey) {
        entries.indexOfFirst { it.key == activeKey }.takeIf { it >= 0 } ?: 0
    }
    val targetIndex = if (pressedIndex >= 0) pressedIndex else idleIndex
    val animatedPos by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = tween(MotionPress),
        label = "indexBarPos",
    )
    val currentIndex = animatedPos.roundToInt()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(metrics.barWidth),
    ) {
        val barHeight = maxHeight
        val itemHeight = metrics.itemHeight
        val windowHeight = itemHeight * metrics.windowCount
        val halfWindow = windowHeight / 2
        // 锚点 = 窗口中心（指尖位置）。夹住窗口，别让首尾条目被条边界裁掉。
        val anchorRaw = if (pressedIndex >= 0 && !fingerYPx.isNaN()) {
            with(density) { fingerYPx.toDp() }
        } else {
            barHeight / 2
        }
        val anchorMin = halfWindow
        val anchorMax = (barHeight - halfWindow).coerceAtLeast(anchorMin)
        val anchorY = anchorRaw.coerceIn(anchorMin, anchorMax)
        // 窗口边缘 0.5 个条目宽为渐隐带：进出窗口的条目连续淡入淡出，不会突然蹦出来。
        val half = (metrics.windowCount - 1) / 2f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(entries, itemHeight) {
                    val barHeightPx = size.height.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 整条高度均分给所有条目：按比例取条目，拖满全高即走完首项→末项。
                        fun pick(y: Float): Int {
                            val fraction = (y / barHeightPx).coerceIn(0f, 0.9999f)
                            return (fraction * entries.size).toInt().coerceIn(0, entries.lastIndex)
                        }
                        try {
                            var index = pick(down.position.y)
                            pressedIndex = index
                            fingerYPx = down.position.y
                            currentJump(entries[index])
                            down.consume()
                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                // 先跟手：锚点用原始指尖位置，显示位置另有吸附动画。
                                fingerYPx = change.position.y
                                val next = pick(change.position.y)
                                if (next != index) {
                                    index = next
                                    pressedIndex = next
                                    currentJump(entries[next])
                                }
                                change.consume()
                            }
                        } finally {
                            // 手势被取消（切编辑态 / 父级抢事件）也要收气泡，不留残影。
                            pressedIndex = -1
                            fingerYPx = Float.NaN
                        }
                    }
                },
        ) {
            entries.forEachIndexed { i, entry ->
                val rel = i - animatedPos
                val alpha = ((half + 0.5f - abs(rel)) / 0.5f).coerceIn(0f, 1f)
                if (alpha <= 0.01f) return@forEachIndexed
                val active = i == currentIndex
                Box(
                    modifier = Modifier
                        .offset(y = anchorY + itemHeight * rel - itemHeight / 2)
                        .fillMaxWidth()
                        .height(itemHeight)
                        // 读屏用户没法按住扫，给每个条目一条点击动作：语义节点不影响拖拽。
                        .semantics {
                            role = Role.Button
                            contentDescription = entry.label
                            onClick { currentJump(entry); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    IndexBarItemContent(
                        entry = entry,
                        active = active,
                        style = metrics.textStyle,
                        modifier = Modifier.alpha(alpha),
                    )
                }
            }
        }
        if (pressedIndex >= 0) {
            // 手指压住的条目在窗口里也看不到，气泡单独再报一次。
            // 气泡必须画在**裁切视口之外**：它朝列表内侧溢出，画在里面会被 clipToBounds 整颗吃掉。
            // 位置贴原始指尖（窗口为了不被裁会内收，气泡才是"你在这里"）。
            val bubbleY = (with(density) { fingerYPx.toDp() } - LetterIndexBubbleSize / 2)
                .coerceIn(0.dp, (barHeight - LetterIndexBubbleSize).coerceAtLeast(0.dp))
            Surface(
                shape = CircleShape,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                modifier = Modifier
                    .offset(
                        // 气泡永远朝列表内侧溢出：贴边滑杆在单元格被裁圆角，朝外会切掉半张。
                        x = if (side == IndexBarSide.End) {
                            -LetterIndexBubbleSize - LetterIndexBubbleGap
                        } else {
                            LetterIndexBubbleGap
                        },
                        y = bubbleY,
                    )
                    .size(LetterIndexBubbleSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IndexBarItemContent(
                        entry = entries[pressedIndex],
                        active = true,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexBarItemContent(
    entry: IndexBarEntry,
    active: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    if (entry.icon != null) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = if (active) scheme.primary else scheme.onSurfaceVariant,
            modifier = modifier.size(AppSectionHeaderIconSize),
        )
    } else {
        Text(
            text = entry.label,
            style = style,
            maxLines = 1,
            modifier = modifier,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}
