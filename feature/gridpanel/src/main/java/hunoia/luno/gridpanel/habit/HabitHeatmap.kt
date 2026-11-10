package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * 热力格（Compose）：底层所有有效日一格淡色，上层按完成度 1–5 档叠习惯色
 * （未来 / 超保留期留空）。
 *
 * 内置热力图部件与习惯编辑页共用，避免两处各画一份导致格径 / 分档漂移
 * （几何与分档见 [HabitHeatmapGeometry]）。桌面 Glance 部件因只能画 Bitmap，
 * 复用同一套几何 / 分档但保留自己的绘制实现。
 */
@Composable
internal fun HabitHeatmapGrid(
    geometry: HabitHeatmapGeometry,
    today: Long,
    checks: Set<Long>,
    counts: Map<Long, Int>,
    target: Int,
    habitColor: Color,
    baseColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(
            geometry.step * geometry.cols,
            geometry.step * HabitHeatmapRows,
        ),
    ) {
        val stepPx = geometry.step.toPx()
        val cellPx = (stepPx * 0.72f).coerceAtLeast(1f)
        val radius = CornerRadius(cellPx * 0.22f)
        val inset = (stepPx - cellPx) / 2f
        val cellSize = Size(cellPx, cellPx)
        val firstDay = habitHeatmapFirstDay(today, geometry.cols)
        val minDay = today - (HabitHeatmapMaxDays - 1)
        for (col in 0 until geometry.cols) {
            for (row in 0 until HabitHeatmapRows) {
                val day = firstDay + col * HabitHeatmapRows + row
                if (day > today || day < minDay) continue
                val topLeft = Offset(col * stepPx + inset, row * stepPx + inset)
                drawRoundRect(
                    color = baseColor,
                    topLeft = topLeft,
                    size = cellSize,
                    cornerRadius = radius,
                )
                val level = habitHeatmapLevel(day, checks, counts, target)
                if (level > 0) {
                    drawRoundRect(
                        color = habitColor.copy(alpha = HabitHeatmapLevelAlpha[level - 1] / 255f),
                        topLeft = topLeft,
                        size = cellSize,
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}
