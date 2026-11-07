package hunoia.luno.gridpanel.habit

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 习惯点阵几何：内置历史部件（`HabitHistoryCell`）与桌面 Glance 部件共用，
 * 避免两端各写一份公式导致列数 / 点径 / 间距漂移。
 *
 * 输入是**去掉卡片内边距后的可用区**（10dp 内边距由调用方扣除）；输出列 / 行、
 * 单元格尺寸与日期字号。点径还要结合日期文本的实际高度（sp → dp 依赖 fontScale），
 * 由各宿主转换后交给 [dotSize]。
 */
data class HabitGridGeometry(
    val cols: Int,
    val rows: Int,
    val cellWidth: Dp,
    val cellHeight: Dp,
    val dateSizeSp: Float,
)

/** 标题行高 / 标题与点阵间距（内置部件与桌面部件共用）。 */
val HabitTitleHeight = 20.dp
val HabitTitleGap = 6.dp

private val MinCellWidth = 22.dp
private val MinCellHeight = 30.dp

/** 圆点占格宽比例：留白比旧 0.75 更小，点阵看起来更紧凑。 */
private const val DotWidthFactor = 0.85f

/** 日期文本与圆点间距 / 防裁安全边距。 */
private val DotTextGap = 1.dp
private val DotSafety = 1.dp

/** 单元格之间不插间距：点阵靠 weight 均分，圆点自身留白由 [DotWidthFactor] 控制。 */
fun habitGridGeometry(
    innerWidth: Dp,
    innerHeight: Dp,
    titleHeight: Dp = HabitTitleHeight,
): HabitGridGeometry {
    val gridHeight = (innerHeight - titleHeight - HabitTitleGap).coerceAtLeast(20.dp)
    val cols = (innerWidth / MinCellWidth).toInt().coerceIn(3, 8)
    val rows = (gridHeight / MinCellHeight).toInt().coerceIn(1, 4)
    val cellWidth = (innerWidth / cols).coerceAtLeast(12.dp)
    val cellHeight = (gridHeight / rows).coerceAtLeast(16.dp)
    val dateSizeSp = (cellWidth.value * 0.28f).coerceIn(6f, 9f)
    return HabitGridGeometry(
        cols = cols,
        rows = rows,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        dateSizeSp = dateSizeSp,
    )
}

/**
 * 圆点直径：宽度按 [DotWidthFactor] 留边，高度扣除日期文本后取小，任意尺寸不裁剪。
 * 旧实现把点径上限锁在 18dp，大尺寸部件会留下大片空白；这里放宽到 32dp。
 */
fun HabitGridGeometry.dotSize(dateTextHeight: Dp): Dp {
    val maxDotByWidth = (cellWidth * DotWidthFactor).coerceIn(6.dp, 32.dp)
    val maxDotByHeight = (cellHeight - dateTextHeight - DotTextGap - DotSafety).coerceAtLeast(4.dp)
    return minOf(maxDotByWidth, maxDotByHeight)
}
