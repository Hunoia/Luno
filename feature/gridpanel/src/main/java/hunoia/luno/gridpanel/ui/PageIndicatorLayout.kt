package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 页指示条/拾取浮层共用的尺寸（dp）。 */
internal val PageDotGap = 6.dp
internal val PageRealDotSize = 7.dp
internal val PageVirtualDotSize = 9.dp
internal val PageEllipsisSize = 9.dp

/** 指示条左右内边距。 */
internal val PageIndicatorPadding = 14.dp

/** 页指示条的一个槽位：页点，或两端省略号。 */
internal sealed interface PageDotSlot {
    /** [index] = 页索引；`index == pageCount` 时是「新建页」虚点。 */
    data class Page(val index: Int) : PageDotSlot

    data object Ellipsis : PageDotSlot
}

/**
 * 页点窗口：页数超过 [maxSlots] 时，以 [centerPage] 为中心截一段，两端用省略号占位。
 *
 * [centerPage] 必须是**拖拽开始前**的当前页（拖拽中保持不变）。若拿拖拽目标页当中心，
 * 窗口会跟着目标漂移，同一根手指位置每帧都会映射到新的页 —— 越滑越远，永远落不准。
 *
 * [includeVirtual] 时虚页点（新建页）永远保留在最后：页数多时也能直接拖去新建页，
 * 不会因为窗口没滚到末尾就丢掉这个入口。
 */
internal fun pageDotSlots(
    pageCount: Int,
    includeVirtual: Boolean,
    centerPage: Int,
    maxSlots: Int,
): List<PageDotSlot> {
    if (maxSlots <= 0) return emptyList()
    val pages = pageCount.coerceAtLeast(1)
    val total = pages + if (includeVirtual) 1 else 0
    if (total <= maxSlots) {
        return buildList {
            for (index in 0 until pages) add(PageDotSlot.Page(index))
            if (includeVirtual) add(PageDotSlot.Page(pages))
        }
    }
    val center = centerPage.coerceIn(0, pages - 1)
    // 太窄放不下两端省略号：只截真实页的一段，虚页仍保留。
    if (maxSlots < 5) {
        val realSlots = if (includeVirtual) (maxSlots - 1).coerceAtLeast(1) else maxSlots
        val start = (center - realSlots / 2).coerceIn(0, (pages - realSlots).coerceAtLeast(0))
        val end = (start + realSlots).coerceAtMost(pages)
        return buildList {
            for (index in start until end) add(PageDotSlot.Page(index))
            if (includeVirtual) add(PageDotSlot.Page(pages))
        }
    }
    // 两个省略号槽 + 虚页槽先占掉，剩下的给真实页。
    val realSlots = (maxSlots - 2 - if (includeVirtual) 1 else 0).coerceAtLeast(1)
    val start = (center - realSlots / 2).coerceIn(0, (pages - realSlots).coerceAtLeast(0))
    val end = (start + realSlots).coerceAtMost(pages)
    return buildList {
        if (start > 0) add(PageDotSlot.Ellipsis)
        for (index in start until end) add(PageDotSlot.Page(index))
        if (end < pages) add(PageDotSlot.Ellipsis)
        if (includeVirtual) add(PageDotSlot.Page(pages))
    }
}
/**
 * 每个槽位在指示条内的中心 x（px，原点 = 指示条左边缘）。
 *
 * 渲染（[GridPageIndicator]）与拖拽命中（GridPanelCard）都用这一份几何，
 * 保证「手指指到的点」就是「画出来的点」。
 */
internal fun pageDotCentersPx(
    slots: List<PageDotSlot>,
    pageCount: Int,
    cardWidthPx: Float,
    density: Float,
): List<Float> {
    if (slots.isEmpty()) return emptyList()
    val gap = PageDotGap.value * density
    val padding = PageIndicatorPadding.value * density
    val widths = slots.map { slot ->
        when (slot) {
            is PageDotSlot.Ellipsis -> PageEllipsisSize.value * density
            is PageDotSlot.Page ->
                if (slot.index >= pageCount) {
                    PageVirtualDotSize.value * density
                } else {
                    PageRealDotSize.value * density
                }
        }
    }
    val total = widths.sum() + gap * (slots.size - 1)
    var x = padding + (cardWidthPx - 2f * padding - total) / 2f
    return widths.map { width ->
        val center = x + width / 2f
        x += width + gap
        center
    }
}

/** 省略号：三个小圆点，不走文本 —— 大字体缩放时文本会被固定行高裁掉。 */
@Composable
internal fun PageEllipsisDots(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radius = 1.2.dp.toPx()
        val cy = size.height / 2f
        for (i in 0..2) {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(size.width * (0.25f + 0.25f * i), cy),
            )
        }
    }
}
