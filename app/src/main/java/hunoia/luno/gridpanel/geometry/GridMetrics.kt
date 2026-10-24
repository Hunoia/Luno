package hunoia.luno.gridpanel.geometry

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt
import hunoia.luno.config.model.DynamicRowExtra
import hunoia.luno.config.model.GridPanelDefaults

/** 圆角下限：边距拉满后卡片圆角不能小到变成直角。 */
val MinCardCorner = 8.dp

/**
 * 屏幕圆角半径（px）。Android 无公开 API：先读 ROM overlay `rounded_corner_radius`，
 * 读不到按「134px @ 546dpi」折算（密度也读不到就用 546）。
 */
fun screenCornerRadiusPx(context: Context): Float {
    val res = context.resources
    val id = res.getIdentifier("rounded_corner_radius", "dimen", "android")
    if (id > 0) {
        val v = runCatching { res.getDimension(id) }.getOrDefault(0f)
        if (v > 0f) return v
    }
    val dpi = runCatching { res.displayMetrics.densityDpi }.getOrDefault(0)
    return 134f * (if (dpi > 0) dpi else 546) / 546f
}

/**
 * 卡片圆角（px）= 屏幕圆角 − 离屏边距，视觉上与屏幕圆角连续；夹到 [MinCardCorner] 下限。
 */
@Composable
fun rememberCardCornerPx(screenMarginDp: Float): Float {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density, screenMarginDp) {
        val screen = screenCornerRadiusPx(context)
        val min = with(density) { MinCardCorner.toPx() }
        (screen - with(density) { screenMarginDp.dp.toPx() }).coerceAtLeast(min)
    }
}

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
): Int {
    val gap = gapFor(columns).value
    val widthFraction = cardWidthFraction.coerceIn(
        // 下限取横屏那份（更小）：竖屏存的值不可能小于竖屏下限，横屏值在竖屏调用方
        // 会被外层 clamp 回去，这里只影响格宽估算，取宽者无妨。
        GridPanelDefaults.MinCardWidthFractionLandscape,
        GridPanelDefaults.MaxCardWidthFraction,
    )
    val heightFraction = cardHeightFraction.coerceIn(
        GridPanelDefaults.MinCardHeightFraction,
        GridPanelDefaults.MaxCardHeightFraction,
    )
    val cardW = screenWdp * widthFraction
    val cellW = (cardW - 2f * GridPadding.value - gap * (columns - 1)) / columns.coerceAtLeast(1)
    val cellH = cellW
    // 顶部页指示点行 + 底部等高预留恒存在：否则单页面板进编辑态指示点出现时卡高跳变。
    val indicator = 2f * IndicatorHeight.value
    val visibleH = screenHdp * heightFraction - 2f * GridPadding.value - indicator
    val stepH = cellH + gap
    // round 而非 floor：卡高已贴齐行数，floor 会系统性偏矮一行。
    return ((visibleH + gap) / stepH).roundToInt().coerceAtLeast(1)
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
    /** 本页已占用的最大行（bottom）；实际行数 = max(可见行数, 占用) + [extraRows]。 */
    occupiedRows: Int,
    /** 行数尾额外空行：编辑态传 [hunoia.luno.config.model.EditRowExtra]，运行态 0。 */
    extraRows: Int = DynamicRowExtra,
): GridMetrics {
    val density = LocalDensity.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组）。
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val gridPad = with(density) { GridPadding.toPx() }
    val indicator = with(density) { (IndicatorHeight * 2).toPx() } // 顶部/底部各指示点行高，对称预留
    val gap = with(density) { gapFor(columns).toPx() }
    val screenWdp = configuration.screenWidthDp.toFloat()
    val screenHdp = configuration.screenHeightDp.toFloat()
    val visibleRows = gridPanelVisibleRows(
        screenWdp, screenHdp, columns, cardWidthFraction, cardHeightFraction,
    )
    val rows = maxOf(visibleRows, occupiedRows.coerceAtLeast(0)) + extraRows

    return remember(
        columns,
        rows,
        cardWidthFraction,
        cardHeightFraction,
        screenW,
        screenH,
        gridPad,
        indicator,
        gap,
    ) {
        val cardW = screenW * cardWidthFraction
        val innerW = cardW - gridPad * 2
        val cellW = (innerW - gap * (columns - 1)) / columns
        val cellH = cellW
        // 卡片底边贴齐最后一行格子：不留高度取整余量（余量曾堆在底部显空）。
        // 实际行数更多时下半区靠 verticalScroll reach。
        val cardH = (
            gridPad * 2 + indicator +
                visibleRows * cellH + (visibleRows - 1).coerceAtLeast(0) * gap
            ).coerceAtLeast(gridPad * 2 + indicator + cellH)
        GridMetrics(
            columns = columns,
            rows = rows,
            screenW = screenW,
            screenH = screenH,
            cellW = cellW,
            cellH = cellH,
            gap = gap,
            gridPad = gridPad,
            indicator = indicator,
            cardW = cardW,
            cardH = cardH,
        )
    }
}
