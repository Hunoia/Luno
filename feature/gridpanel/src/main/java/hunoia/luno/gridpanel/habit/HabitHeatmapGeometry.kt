package hunoia.luno.gridpanel.habit

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.HabitHistoryRetentionDays
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 习惯热力图几何与完成度分级：内置热力图部件（`HabitHeatmapCell`）与桌面 Glance
 * 部件（`HeatmapWidget`）共用，避免两端各写一份公式导致列数 / 格径 / 分档漂移
 * （architecture §7.5）。
 *
 * 输入是**去掉标题行后的网格区**（标题高度由调用方扣除）；输出列数与格径。
 * 一列 7 天（GitHub 风格），最后一列是本周，[today] 落在真实星期行，未来格子留空。
 */
data class HabitHeatmapGeometry(
    val cols: Int,
    val step: Dp,
)

/** 一列 7 天（GitHub 风格），最多 13 列 = 91 天。 */
const val HabitHeatmapRows = 7
const val HabitHeatmapMaxCols = 13

/** 热力图最多展示的天数：与打卡保留期一致。 */
const val HabitHeatmapMaxDays = HabitHistoryRetentionDays

/** 5 档完成度 → 上层色块的 alpha（0..255，tint 后即为习惯色深浅）。 */
val HabitHeatmapLevelAlpha = intArrayOf(56, 102, 148, 194, 255)

/** 热力图几何：7 行固定，列数 / 格径随可用区自适应。 */
fun habitHeatmapGeometry(width: Dp, gridHeight: Dp): HabitHeatmapGeometry {
    val step = (gridHeight / HabitHeatmapRows).coerceIn(4.dp, 18.dp)
    val cols = (width / step).toInt().coerceIn(1, HabitHeatmapMaxCols)
    return HabitHeatmapGeometry(cols = cols, step = step)
}

/** 第一格（左上角）的日期：最后一列 = 本周，[today] 落在真实星期行。 */
fun habitHeatmapFirstDay(today: Long, cols: Int): Long {
    val todayDow = LocalDate.ofEpochDay(today).dayOfWeek.value - 1
    return today - todayDow - (cols - 1) * HabitHeatmapRows
}

/** 完成度 → 1–5 档；0 = 当天没有进度（只剩底层淡色）。 */
fun habitHeatmapLevel(
    day: Long,
    checks: Set<Long>,
    counts: Map<Long, Int>,
    target: Int,
): Int {
    val safeTarget = target.coerceAtLeast(1)
    val count = when {
        day in checks -> safeTarget
        else -> counts[day] ?: 0
    }
    if (count <= 0) return 0
    if (safeTarget == 1) return 5
    return ((count.toFloat() / safeTarget) * 5f).roundToInt().coerceIn(1, 5)
}
