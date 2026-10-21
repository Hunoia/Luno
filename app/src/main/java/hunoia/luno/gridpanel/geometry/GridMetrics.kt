package hunoia.luno.gridpanel.geometry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import hunoia.luno.config.model.DynamicRowExtra
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.ui.theme.ShapeExtraLarge

val CardMargin = 12.dp

/** 面板圆角与快速启动器面板（Card shape = ExtraLargeShape）对齐。 */
val CardCorner = ShapeExtraLarge

/** 多页时页指示点的行高（固定预算，计入 cardH）。 */
val IndicatorHeight = 12.dp

/** 网格内容相对卡片的内边距（横向在 pager 外层加，纵向计入 cardH）。 */
val GridPadding = 10.dp

val ItemCorner = 14.dp

/**
 * 格间距随列数收放：固定间距在两端都不对，2 列时显松散、6 列时挤成一团。
 */
fun gapFor(columns: Int): Dp = when {
    columns <= 3 -> 10.dp
    columns <= 4 -> 8.dp
    columns <= 5 -> 6.dp
    else -> 4.dp
}

/**
 * 卡片高度比例 → 可见行数。渲染与落盘层必须共用这一套公式；
 * 格子是方格（尺寸由列数与卡宽决定），高度只决定能看到几行。
 */
fun gridPanelVisibleRows(
    screenWdp: Float,
    screenHdp: Float,
    columns: Int,
    cardWidthFraction: Float,
    cardHeightFraction: Float,
    pageCount: Int,
): Int {
    val gap = gapFor(columns).value
    val widthFraction = cardWidthFraction.coerceIn(
        GridPanelDefaults.MinCardWidthFraction,
        GridPanelDefaults.MaxCardWidthFraction,
    )
    val heightFraction = cardHeightFraction.coerceIn(
        GridPanelDefaults.MinCardHeightFraction,
        GridPanelDefaults.MaxCardHeightFraction,
    )
    val cardW = screenWdp * widthFraction
    val cellW = (cardW - 2f * GridPadding.value - gap * (columns - 1)) / columns.coerceAtLeast(1)
    val cellH = cellW
    val indicator = if (pageCount > 1) IndicatorHeight.value else 0f
    val visibleH = screenHdp * heightFraction - 2f * GridPadding.value - indicator
    val stepH = cellH + gap
    return ((visibleH + gap) / stepH).toInt().coerceAtLeast(1)
}

/**
 * 网格的像素几何：所有派生量都在这里算一次，拖拽/缩放/渲染共用同一套坐标。
 * [cellX] / [cellY] 内部必须 floor：整数截断对负数是向零取整，往左/往上拖时
 * 边界那一格会差一格（-0.5 被截成 0），手感上是一次跳变。
 */
@Immutable
data class GridMetrics(
    val columns: Int,
    /** 网格实际行数（可摆放范围，≥ 卡片可见行数）。 */
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

    fun cellX(px: Float): Int = floor(px / stepW).toInt()

    fun cellY(px: Float): Int = floor(px / stepH).toInt()
}

@Composable
fun rememberGridMetrics(
    columns: Int,
    cardWidthFraction: Float,
    cardHeightFraction: Float,
    pageCount: Int,
    /** 本页已占用的最大行（bottom）；实际行数 = max(可见行数, 占用) + DynamicRowExtra。 */
    occupiedRows: Int,
): GridMetrics {
    val density = LocalDensity.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组）。
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val gridPad = with(density) { GridPadding.toPx() }
    val header = 0f // 无页眉预算
    val indicator = if (pageCount > 1) with(density) { IndicatorHeight.toPx() } else 0f
    val gap = with(density) { gapFor(columns).toPx() }
    val screenWdp = configuration.screenWidthDp.toFloat()
    val screenHdp = configuration.screenHeightDp.toFloat()
    val visibleRows = gridPanelVisibleRows(
        screenWdp, screenHdp, columns, cardWidthFraction, cardHeightFraction, pageCount,
    )
    val rows = maxOf(visibleRows, occupiedRows.coerceAtLeast(0)) + DynamicRowExtra

    return remember(
        columns,
        rows,
        pageCount,
        cardWidthFraction,
        cardHeightFraction,
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
        // 卡片高度由比例决定（夹 92% 屏高），可见行数随高度反推；
        // 实际行数更多时下半区靠 verticalScroll reach。
        val cardH = (screenH * cardHeightFraction).coerceAtMost(screenH * 0.92f)
            .coerceAtLeast(gridPad * 2 + indicator + cellH + gap)
        GridMetrics(
            columns = columns,
            rows = rows,
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
