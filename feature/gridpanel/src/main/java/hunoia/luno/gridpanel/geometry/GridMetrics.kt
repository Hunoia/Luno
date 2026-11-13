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
 * 卡片圆角（px）= 屏幕圆角 − 左右边距，视觉上与屏幕圆角连续；夹到 [MinCardCorner] 下限。
 */
@Composable
fun rememberCardCornerPx(sideMarginDp: Float): Float {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density, sideMarginDp) {
        val screen = screenCornerRadiusPx(context)
        val min = with(density) { MinCardCorner.toPx() }
        (screen - with(density) { sideMarginDp.dp.toPx() }).coerceAtLeast(min)
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
 * 卡高连续化后的行数阈值：换算出的余数小于该值就不再多算一行，
 * 避免一点点余量凭空多出一整行空位。
 */
private const val RowRemainderThreshold = 0.25f

/**
 * 连续卡高 → 可摆放行数。
 *
 * 行数不再反过来决定卡高：[cardH] 是用户拖出来的连续高度，这里只回答「能摆几行」。
 * 用 `(available + gap) / (cellH + gap)` 让最后一行也把格间距算进去，避免末行被 gap 挤出可视区。
 * 余数小于 [RowRemainderThreshold] 时按整行算（+0），否则向上补一行。
 */
fun rowsForCardHeight(
    cardH: Float,
    cellH: Float,
    gap: Float,
    gridPad: Float,
    indicator: Float,
): Int {
    val available = cardH - gridPad * 2f - indicator
    if (available <= 0f) return 1
    val step = (cellH + gap).coerceAtLeast(1f)
    val exact = (available + gap) / step
    val whole = floor(exact).toInt()
    val fraction = exact - floor(exact)
    return (whole + if (fraction >= RowRemainderThreshold) 1 else 0).coerceAtLeast(1)
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
    // 顶部页指示点行 + 底部等高预留恒存在：否则单页面板进编辑态指示点出现时卡高跳变。
    val indicator = 2f * IndicatorHeight.value
    return rowsForCardHeight(
        cardH = screenHdp * heightFraction,
        cellH = cellH,
        gap = gap,
        gridPad = GridPadding.value,
        indicator = indicator,
    )
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

    /**
     * 拖拽落格：按「左上格的中心」判定，并夹到合法范围。
     *
     * 不用整项中心：多格项的整项中心在左/上边界依赖拖拽位移额外夹取（否则
     * 落不到第 0 列）；左上格与整数 span 严格对齐，直接夹格就够。也不用左上角：
     * 左上格中心有半格迟滞，不会贴着格界来回跳。
     */
    fun dropCellX(left: Float, span: Int): Int =
        cellX(left + cellW / 2f).coerceIn(0, (columns - span).coerceAtLeast(0))

    fun dropCellY(top: Float, span: Int): Int =
        cellY(top + cellH / 2f).coerceIn(0, (rows - span).coerceAtLeast(0))
}

/**
 * 卡片高度（px）：连续跟手，不再量化到整行。
 * [cardW] 为卡片宽度 px；[gap]/[gridPad]/[indicator] 均为 px。
 * 下界是「至少装得下一行格子」；渲染（[rememberGridMetrics]）与面板缩放
 * （[resolvePanelResize]）共用同一公式，缩放锚点才能和渲染尺寸严格一致。
 */
fun gridPanelCardHeight(
    columns: Int,
    cardW: Float,
    cardHeightFraction: Float,
    screenH: Float,
    gridPad: Float,
    indicator: Float,
    gap: Float,
    /**
     * 可用高度上限 px（默认不限）：底栏开启时 = 屏高 − 系统栏安全区 − 底栏预留。
     * 只在比例算出来放不下时生效 —— 常规面板（如 0.62）观感不变，
     * 被夹住的只有本来就顶到屏幕边的极端配置。
     */
    maxCardHeightPx: Float = Float.POSITIVE_INFINITY,
): Float {
    val innerW = cardW - gridPad * 2
    val cellW = (innerW - gap * (columns - 1)) / columns.coerceAtLeast(1)
    val minH = gridPad * 2 + indicator + cellW
    // 上限比一行还矮时仍保住一行：宁可盖到底栏，也不出 0 高卡片。
    val cap = maxOf(maxCardHeightPx, minH)
    return (screenH * cardHeightFraction).coerceIn(minH, cap)
}

/**
 * 调列数后保持视觉卡高：卡高连续后反推退化为直接除法，列数变化只影响行数派生，
 * 不会顺带改变面板高度。
 */
fun keepCardHeightFraction(currentCardH: Float, screenH: Float): Float =
    (currentCardH / screenH).coerceIn(
        GridPanelDefaults.MinCardHeightFraction,
        GridPanelDefaults.MaxCardHeightFraction,
    )

@Composable
fun rememberGridMetrics(
    columns: Int,
    cardWidthFraction: Float,
    cardHeightFraction: Float,
    /** 本页已占用的最大行（bottom）；实际行数 = max(可见行数, 占用) + [extraRows]。 */
    occupiedRows: Int,
    /** 行数尾额外空行：编辑态传 [hunoia.luno.config.model.EditRowExtra]，运行态 0。 */
    extraRows: Int = DynamicRowExtra,
    /**
     * 底部不可用高度 px：系统栏安全区 + 面板底栏预留（底栏关闭时只含前者）。
     * 卡高上限 = 屏高 − 该值，直接当“额外的底部边距”用（architecture §7.2）。
     */
    reservedBottomPx: Float = 0f,
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

    return remember(
        columns,
        occupiedRows,
        extraRows,
        cardWidthFraction,
        cardHeightFraction,
        screenW,
        screenH,
        gridPad,
        indicator,
        gap,
        reservedBottomPx,
    ) {
        val cardW = screenW * cardWidthFraction
        val innerW = cardW - gridPad * 2
        val cellW = (innerW - gap * (columns - 1)) / columns
        val cellH = cellW
        // 卡高连续跟手（不再量化到整行），行数只作为可摆放范围派生。
        val cardH = gridPanelCardHeight(
            columns = columns,
            cardW = cardW,
            cardHeightFraction = cardHeightFraction,
            screenH = screenH,
            gridPad = gridPad,
            indicator = indicator,
            gap = gap,
            maxCardHeightPx = screenH - reservedBottomPx,
        )
        val visibleRows = rowsForCardHeight(cardH, cellH, gap, gridPad, indicator)
        val rows = maxOf(visibleRows, occupiedRows.coerceAtLeast(0)) + extraRows
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
