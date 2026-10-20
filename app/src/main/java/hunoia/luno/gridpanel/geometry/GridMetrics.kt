package hunoia.luno.gridpanel.geometry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/** 卡片外边距（贴着屏幕边缘的那一圈）。 */
val CardMargin = 12.dp

/** 卡片圆角。 */
val CardCorner = 20.dp

/** 页眉高（标题 + 时间）。 */
val HeaderHeight = 46.dp

/** 多页时页指示点行高（固定预算，计入 cardH）。 */
val IndicatorHeight = 12.dp

/** 网格内容相对卡片的内边距（横向在 pager 外层加，纵向计入 cardH）。 */
val GridPadding = 10.dp

/** 单格与项的圆角。 */
val ItemCorner = 14.dp

/**
 * 格间距随列数收放。固定间距在两端都不对：2 列时格子很大、6dp 显得松散漂；
 * 6 列时 6dp 又让格子挤成一团（跨格项的内部 padding 会被吃掉）。
 */
fun gapFor(columns: Int): Dp = when {
    columns <= 3 -> 10.dp
    columns <= 4 -> 8.dp
    columns <= 5 -> 6.dp
    else -> 4.dp
}

/**
 * 网格的像素几何。所有派生量都在这里算一次，拖拽/缩放/渲染共用同一套坐标 ——
 * 以前 Root 和 Canvas 各算一遍 step/itemW，改一处忘一处就会差半格。
 *
 * [cellX] / [cellY] 内部**必须 floor**：整数截断对负数是向零取整，往左/往上拖时
 * 边界那一格会差一格（-0.5 被截成 0），手感上是一次跳变。
 */
@Immutable
data class GridMetrics(
    val columns: Int,
    /** 网格**总**行数 = 设置行数 × 2（可见高度按 visibleRows，见 rememberGridMetrics）。 */
    val rows: Int,
    val screenW: Float,
    val screenH: Float,
    val cellW: Float,
    val cellH: Float,
    val gap: Float,
    val gridPad: Float,
    val header: Float,
    val indicator: Float,
    val cardW: Float,
    val cardH: Float,
) {
    val stepW: Float = cellW + gap
    val stepH: Float = cellH + gap

    /** 网格内容区（不含卡片 padding）。 */
    val contentW: Float = cellW * columns + gap * (columns - 1)
    val contentH: Float = cellH * rows + gap * (rows - 1)

    /** span 格宽（支持小数 —— resize 期间连续跟手）。 */
    fun itemW(span: Float): Float = cellW * span + gap * (span - 1f)

    fun itemH(span: Float): Float = cellH * span + gap * (span - 1f)

    /** 内容坐标 x → 列号（floor，见类注释）。 */
    fun cellX(px: Float): Int = floor(px / stepW).toInt()

    fun cellY(px: Float): Int = floor(px / stepH).toInt()
}

@Composable
fun rememberGridMetrics(
    columns: Int,
    /** 设置里的行数 = **可见**行数；网格总行数是它的两倍（下半区滚动 reach）。 */
    rows: Int,
    pageCount: Int,
    cardWidthFraction: Float,
): GridMetrics {
    val density = LocalDensity.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组）。
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val gridPad = with(density) { GridPadding.toPx() }
    val header = with(density) { HeaderHeight.toPx() }
    val indicator = if (pageCount > 1) with(density) { IndicatorHeight.toPx() } else 0f
    val gap = with(density) { gapFor(columns).toPx() }

    return remember(
        columns,
        rows,
        pageCount,
        cardWidthFraction,
        screenW,
        screenH,
        gridPad,
        header,
        indicator,
        gap,
    ) {
        val cardW = screenW * cardWidthFraction
        val innerW = cardW - gridPad * 2
        val cellW = (innerW - gap * (columns - 1)) / columns
        val cellH = cellW
        val rowsGrid = rows * 2
        // 卡片可见高度按**设置**行数算（面板实际高度 = n 行，下半区靠滚动 reach）：
        // 高度夹到屏的 82% 是纵向滚动存在的理由。
        val gridH = cellH * rows + gap * (rows - 1)
        val cardH = (gridH + header + indicator).coerceAtMost(screenH * 0.82f) + gridPad * 2
        GridMetrics(
            columns = columns,
            rows = rowsGrid,
            screenW = screenW,
            screenH = screenH,
            cellW = cellW,
            cellH = cellH,
            gap = gap,
            gridPad = gridPad,
            header = header,
            indicator = indicator,
            cardW = cardW,
            cardH = cardH,
        )
    }
}
