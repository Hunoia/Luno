package hunoia.luno.ui.component.row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ContainerShape
import hunoia.luno.ui.theme.SegmentedGap

/** 行内自绘背景时的横向内边距：与 M3 `ListItem` 一致，行组件与组内条目共用。 */
internal val RowInnerPadding = 16.dp

/** 大卡分割线：1dp、`outlineVariant` 60%，与行内分割线同一口径。 */
private val DividedLineWidth = 1.dp
private const val DividedLineAlpha = 0.6f

/**
 * 组内条目的统一容器：**分段风格**是独立圆角块（`shape` + `surfaceBright` 底色），
 * **大卡分割线风格**改成通透矩形，由 `LunoGroup` 的卡片托底，自己只补顶部缩进分割线。
 *
 * 所有「放在 `LunoGroup` 里、自带底色的条目」都必须走这个容器，不要在页面里各写一遍
 * `clip(shape) + background(surfaceBright)`：那只实现了分段风格，大卡风格下会退化成
 * 「卡里套卡」（ui-guidelines §4.1）。内容自己带左右内边距，容器只负责外形与底色。
 */
@Composable
fun LunoGroupItem(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceBright,
    dividerStartInset: Dp = RowInnerPadding,
    content: @Composable () -> Unit,
) {
    val inDividedCard = LocalInDividedCard.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(Modifier.groupItemTopLine(shape, dividerStartInset)),
        shape = if (inDividedCard) RectangleShape else shape,
        color = if (inDividedCard) Color.Transparent else color,
    ) {
        content()
    }
}

/**
 * 已自绘底色的组内条目（行组件、可展开卡）用的外形修饰：分段风格裁 `shape`，
 * 大卡风格不裁（由外层卡片统一裁切）。
 */
@Composable
fun Modifier.groupItemClip(shape: Shape): Modifier =
    if (LocalInDividedCard.current) this else clip(shape)

/**
 * 组内条目间距：大卡风格由分割线分隔，间距归零；分段风格用 `spacing`（默认 [SegmentedGap]）。
 *
 * 页面不要自己判 `ListStyle`，集中在这里，改风格口径只改一处。
 */
@Composable
fun groupItemSpacing(spacing: Dp = SegmentedGap): Dp =
    if (LocalInDividedCard.current) 0.dp else spacing

/**
 * 条目顶部的大卡分割线：组首 / 单项不画，非大卡风格不加。
 *
 * `startInset` 是分割线左端起点（有 leading 图标时越过图标，对齐文字列）。
 */
@Composable
fun Modifier.groupItemTopLine(
    shape: Shape,
    startInset: Dp = RowInnerPadding,
): Modifier {
    if (!LocalInDividedCard.current || !showsDividedCardTopLine(shape)) return this
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DividedLineAlpha)
    return drawBehind {
        val y = DividedLineWidth.toPx() / 2f
        drawLine(
            color = color,
            start = Offset(startInset.toPx(), y),
            end = Offset(size.width - RowInnerPadding.toPx(), y),
            strokeWidth = DividedLineWidth.toPx(),
        )
    }
}

/**
 * 大卡分割线风格下，条目顶部是否应画分割线。
 *
 * 首选读形状自带的分组位置（[ContainerShape.index]，由 `segmentedShape` /
 * `selectedSegmentedShape` 透传）：`index > 0` 说明它上面还有条目，选中段切角、
 * 只有一项的组都能判对。
 *
 * 页面自组合的 `containerShape(...)` 没有位置信息，退回按顶角判断：顶角是
 * [ConnectionRadius] 说明它上面接着别的条目。都不满足就不画（宁可少一条线，
 * 也不在组首画线）。
 */
internal fun showsDividedCardTopLine(shape: Shape): Boolean {
    val container = shape as? ContainerShape ?: return false
    container.index?.let { return it > 0 }
    return container.topStart < ContainerRadius || container.topEnd < ContainerRadius
}
