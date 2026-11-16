package hunoia.luno.ui.component

import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

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

/**
 * 索引条主轴方向。
 *
 * - [Vertical]：竖向（默认），条目沿 y 排列，右侧/左侧贴边（应用抽屉）。
 * - [Horizontal]：横向，条目沿 x 排列、贴底（列表底部一行，胶囊左侧）；高度固定一条，
 *   metrics 由自身可用宽度自算（[IndexBar.metrics] 传 null）。
 */
enum class IndexBarOrientation { Vertical, Horizontal }

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
 *    条带不滚动、整块居中排开，一眼看到所有落点；
 * 2. 否则退回「字母更大」的跟手窗口：条目高 ≥ [LetterIndexIdealItemHeight] 时从 3 个
 *    往 [maxWindow] 加，再高也只放大到 [LetterIndexItemMaxHeight]。
 *
 * [maxWindow] 限制最多同时显示的条目数（默认 [MaxWindow]）；紧凑型滑杆（贴底、限高）
 * 传更小值，不影响默认调用方（应用抽屉等保持 7）。
 *
 * 调用方要靠 [IndexBarMetrics.barWidth] 决定给内容补多少内边距，两边算出不同宽度会错位。
 */
@Composable
fun rememberIndexBarMetrics(
    entries: List<IndexBarEntry>,
    availableHeight: Dp,
    maxWindow: Int = MaxWindow,
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
    val windowLimit = minOf(maxWindow, entries.size)
    var count = MinWindow
    while (count + 2 <= windowLimit &&
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
 * 附近的 3–7 个条目，进出窗口的条目按距离渐隐；按压时另有气泡报出正中那一项
 * （手指压着的地方看不到字）。
 *
 * **窗口模式下选中位永远在条中心**：条目绕着固定的 [IndexBar] 中心滚，滚动的是拼音，
 * 选中位不跟手跑；全显模式下条目整块排开，选中位就落在条目自己身上。
 * - 点按（未过 touch slop）：**点哪个条目选哪个**——窗口模式按条目间距反推手指处的下标，
 *   全显模式按整条高度比例落位；点得越快越准，不用先拖到眼前。
 * - 拖动（过 slop 之后）：相对拖动，按下不跳，位移换算成条目位移——下滑条带向上（往 Z）、
 *   上滑往 A，与「把条带往下拉」的直觉相反，是因为选中位固定在中心、移动的是条带本身。
 *   窗口模式整条高 = 首项→末项（小窗口也能一条高扫完整段）；全显时条目铺满整条，
 *   按一个条目高 = 一个字母 1:1 跟手，高亮就跟在手指下。
 *
 * 显示吸附到整条目——位置动画走 [MotionPress]，不会停在两个条目中间。
 *
 * 只接受实际存在的条目（由调用方传入），所以每次跳转都有落点。
 * 条目低于 36dp 属于 ui-guidelines §1.6 第 ③ 类紧凑豁免，前提是字形完整不裁剪
 * （下限按实测字高定），且列表本身仍可直接滚动。
 */
@Composable
fun IndexBar(
    entries: List<IndexBarEntry>,
    /** 竖向由调用方传（外部按 [IndexBarMetrics.barWidth] 给内容补内边距）；横向可空，内部按可用宽度自算。 */
    metrics: IndexBarMetrics? = null,
    /** 当前可见条目（⭐ 或字母），用于未按压时窗口居中在哪一项。 */
    activeKey: String?,
    side: IndexBarSide,
    onJump: (IndexBarEntry) -> Unit,
    modifier: Modifier = Modifier,
    orientation: IndexBarOrientation = IndexBarOrientation.Vertical,
    /** 横向最多同时显示的条目数（竖向由调用方在 metrics 里定，此参数不生效）。 */
    maxWindow: Int = MaxWindow,
) {
    val horizontal = orientation == IndexBarOrientation.Horizontal
    val scheme = MaterialTheme.colorScheme
    val currentJump by rememberUpdatedState(onJump)
    var pressed by remember(entries) { mutableStateOf(false) }

    val idleIndex = remember(entries, activeKey) {
        entries.indexOfFirst { it.key == activeKey }.takeIf { it >= 0 } ?: 0
    }
    // 条带位置（条目下标，可带小数）。按压期间由手势直接驱动，松手再吸附回当前条目。
    var stripPos by remember(entries) { mutableFloatStateOf(idleIndex.toFloat()) }
    LaunchedEffect(idleIndex, pressed) {
        // 按压期间手势拿着位置，别让回弹动画跟手指抢（动画重启晚一帧也别把它写回去）。
        if (pressed) return@LaunchedEffect
        animate(stripPos, idleIndex.toFloat(), animationSpec = tween(MotionPress)) { value, _ ->
            if (!pressed) stripPos = value
        }
    }
    val currentIndex = stripPos.roundToInt().coerceIn(0, entries.lastIndex)
    // 跨字母轻振：只在按压拖动期间触发，松手回弹不震。
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(entries, pressed) {
        if (!pressed) return@LaunchedEffect
        snapshotFlow { stripPos.roundToInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    BoxWithConstraints(
        modifier = modifier
            .then(
                if (horizontal) {
                    // 横向：宽度自适应（weight），高度固定一条。
                    Modifier.fillMaxWidth().height(LetterIndexIdealItemHeight)
                } else {
                    Modifier.fillMaxHeight().width(metrics?.barWidth ?: 0.dp)
                },
            ),
    ) {
        // 横向宽度来自 weight 动态布局：metrics 用自身 maxWidth 自算；竖向仍由调用方传。
        val actualMetrics = metrics ?: rememberIndexBarMetrics(entries, maxWidth, maxWindow)
        if (entries.isEmpty() || !actualMetrics.visible) return@BoxWithConstraints
        val barLength = if (horizontal) maxWidth else maxHeight
        val itemLength = actualMetrics.itemHeight
        // 全显：窗口 = 全部条目，条带不滚，整块居中排开——真能「一眼看到所有落点」。
        val fullDisplay = actualMetrics.windowCount == entries.size
        val stripStart = ((barLength - itemLength * entries.size) / 2).coerceAtLeast(0.dp)
        // 窗口：锚点固定在条中心。滑动的是拼音，选中位置永远居中。
        val center = barLength / 2
        // 窗口边缘 0.5 个条目宽为渐隐带：进出窗口的条目连续淡入淡出，不会突然蹦出来。
        val half = (actualMetrics.windowCount - 1) / 2f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(entries, itemLength, horizontal) {
                    val barLengthPx = if (horizontal) size.width.toFloat() else size.height.toFloat()
                    val itemLengthPx = itemLength.toPx()
                    val count = entries.size
                    // 拖动灵敏度：窗口模式整条长 = 首项→末项（小窗口也能一条扫完）；
                    // 全显时条目铺满整条，按一个条目长 = 一个字母 1:1 跟手，高亮不会比手指快。
                    val entriesPerPx = if (fullDisplay) 1f / itemLengthPx else count / barLengthPx
                    val slop = viewConfiguration.touchSlop
                    // 点按落位：手指指着哪个条目就选哪个，不能按整条长比例硬套——
                    // 窗口模式屏上只排了 3–7 个条目，按整条长比例会把「点 C」才算到 N。
                    // 全显模式条目铺满整条，按比例取下标正好就是手指下那一个。
                    fun pickAt(pos: Float, base: Float): Int = if (fullDisplay) {
                        val fraction = (pos / barLengthPx).coerceIn(0f, 0.9999f)
                        (fraction * count).toInt().coerceIn(0, entries.lastIndex)
                    } else {
                        val rel = (pos - barLengthPx / 2f) / itemLengthPx
                        (base + rel).roundToInt().coerceIn(0, entries.lastIndex)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        // 起手位置就是当前落位：没过 slop 当点按（松手才跳），过了 slop 从这一点相对拖动。
                        val startPos = stripPos
                        val startCoord = if (horizontal) down.position.x else down.position.y
                        var index = startPos.roundToInt().coerceIn(0, entries.lastIndex)
                        pressed = true
                        try {
                            var dragged = false
                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                val coord = if (horizontal) change.position.x else change.position.y
                                if (!dragged && abs(coord - startCoord) > slop) dragged = true
                                if (dragged) {
                                    // 位移换算成条目位移：顺主轴增大 → 下标增大 → 条带向反方向滚。
                                    val pos = (startPos + (coord - startCoord) * entriesPerPx)
                                        .coerceIn(0f, entries.lastIndex.toFloat())
                                    stripPos = pos
                                    val next = pos.roundToInt()
                                    if (next != index) {
                                        index = next
                                        currentJump(entries[next])
                                    }
                                }
                                change.consume()
                            }
                            if (!dragged) {
                                val target = pickAt(startCoord, startPos)
                                stripPos = target.toFloat()
                                if (target != index) currentJump(entries[target])
                            }
                        } finally {
                            // 手势被取消（切编辑态 / 父级抢事件）也要收气泡，不留残影。
                            pressed = false
                        }
                    }
                },
        ) {
            entries.forEachIndexed { i, entry ->
                val rel = i - stripPos
                val alpha = if (fullDisplay) 1f else ((half + 0.5f - abs(rel)) / 0.5f).coerceIn(0f, 1f)
                if (alpha <= 0.01f) return@forEachIndexed
                val active = i == currentIndex
                val pos = if (fullDisplay) {
                    stripStart + itemLength * i
                } else {
                    center + itemLength * rel - itemLength / 2
                }
                Box(
                    modifier = Modifier
                        .offset(
                            x = if (horizontal) pos else 0.dp,
                            y = if (horizontal) 0.dp else pos,
                        )
                        .then(
                            if (horizontal) {
                                Modifier.width(itemLength).fillMaxHeight()
                            } else {
                                Modifier.fillMaxWidth().height(itemLength)
                            },
                        )
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
                        style = actualMetrics.textStyle,
                        modifier = Modifier.alpha(alpha),
                    )
                }
            }
        }
        if (pressed) {
            // 气泡报的是**正中那一项**（全显模式下是当前高亮项），不是指尖位置：
            // 相对拖动下指尖跟选中项已经没有固定关系，跟着指尖跑只会对不上字。
            // 气泡必须画在**裁切视口之外**：它朝列表内侧溢出，画在里面会被 clipToBounds 整颗吃掉。
            val bubbleCenter = if (fullDisplay) {
                stripStart + itemLength * (stripPos + 0.5f)
            } else {
                center
            }
            val bubblePos = (bubbleCenter - LetterIndexBubbleSize / 2)
                .coerceIn(0.dp, (barLength - LetterIndexBubbleSize).coerceAtLeast(0.dp))
            Surface(
                shape = CircleShape,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                modifier = Modifier
                    .offset(
                        // 气泡永远朝列表内侧溢出：贴边滑杆在单元格被裁圆角，朝外会切掉半张。
                        // 两侧必须镜像到**杆外侧**：Start 写成 +gap 会让 40dp 的气泡压在 28dp
                        // 的杆上、正好顶在手指底下（左侧「贴手」、右侧正常的根因）。
                        x = if (horizontal) {
                            bubblePos
                        } else if (side == IndexBarSide.End) {
                            -LetterIndexBubbleSize - LetterIndexBubbleGap
                        } else {
                            actualMetrics.barWidth + LetterIndexBubbleGap
                        },
                        y = if (horizontal) {
                            // 横向贴底：气泡朝列表内侧（Start=上）溢出，不遮底栏。
                            if (side == IndexBarSide.End) {
                                LetterIndexIdealItemHeight + LetterIndexBubbleGap
                            } else {
                                -LetterIndexBubbleSize - LetterIndexBubbleGap
                            }
                        } else {
                            bubblePos
                        },
                    )
                    .size(LetterIndexBubbleSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IndexBarItemContent(
                        entry = entries[currentIndex],
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
