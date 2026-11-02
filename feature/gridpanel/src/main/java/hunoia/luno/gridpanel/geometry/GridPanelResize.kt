package hunoia.luno.gridpanel.geometry

import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import kotlin.math.roundToInt

/** [planGridResize] 的结果：重排后的项 + 确实放不下、会被移除的项。 */
class GridResizePlan(
    val items: List<GridPanelItem>,
    val dropped: List<GridPanelItem>,
)

/**
 * 列数变化的落位预演（纯函数）：夹进网格 → 放不下按比例缩小 → 本页找空位 →
 * 仍放不下才进 [GridResizePlan.dropped]。
 *
 * 设置页弹窗预演与真实落盘共用这一份规则，所以「弹窗里说要移除哪些」就是实际会移除的。
 *
 * [expandRows] = true 时改用「向下长高」而不是丢弃：面板内双指调列数没有确认弹窗，
 * 缩小列数把项挤掉会变成静默丢失，宁可让面板多出几行可滚动。
 */
fun planGridResize(
    items: List<GridPanelItem>,
    columns: Int,
    cardWidthFraction: Float,
    cardHeightFraction: Float,
    screenWdp: Float,
    screenHdp: Float,
    expandRows: Boolean = false,
): GridResizePlan {
    val cols = columns.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
    // 页数派生：按现有项的最大页归属重排，不在这里增删页。
    val pages = (items.maxOfOrNull { it.pageIndex } ?: 0) + 1
    val placed = mutableListOf<GridPanelItem>()
    val dropped = mutableListOf<GridPanelItem>()
    // 实际行数：max(可见行数, 现有占用)，放不下的项再往上撑，无上限。
    var effectiveRows = maxOf(
        gridPanelVisibleRows(screenWdp, screenHdp, cols, cardWidthFraction, cardHeightFraction),
        items.maxOfOrNull { it.bottom } ?: 0,
    )
    val coerced = items.map { it.copy(pageIndex = it.pageIndex.coerceIn(0, pages - 1)) }
    for (p in 0 until pages) {
        for (raw in coerced.filter { it.pageIndex == p }) {
            // 先夹到网格内，放不下的按比例缩小：宁可变小也要留住内容。下界 1×1
            // 避免退化项（planReorder 对退化项行为未定义，见 GridPanelItem.normalized）。
            val clamped = raw.normalized(cols, effectiveRows)
            val candidate = if (clamped.spanX <= cols && clamped.spanY <= effectiveRows) {
                clamped
            } else {
                val scale = minOf(
                    cols.toFloat() / clamped.spanX,
                    effectiveRows.toFloat() / clamped.spanY,
                ).coerceAtMost(1f)
                val shrunk = clamped.copy(
                    spanX = (clamped.spanX * scale).roundToInt().coerceIn(1, cols),
                    spanY = (clamped.spanY * scale).roundToInt().coerceIn(1, effectiveRows),
                )
                // 缩小后若纵向仍然撑破本页，抬高实际行数保它（无上限）
                if (shrunk.y + shrunk.spanY > effectiveRows) {
                    effectiveRows = shrunk.y + shrunk.spanY
                }
                shrunk.normalized(cols, effectiveRows)
            }
            when {
                GridPanelItemLayout.isFree(
                    placed, candidate.x, candidate.y, candidate.spanX, candidate.spanY,
                ) -> placed += candidate
                else -> {
                    var slot = GridPanelItemLayout.firstFreeSlot(
                        placed, cols, effectiveRows, candidate.spanX, candidate.spanY,
                    )
                    if (slot == null && expandRows) {
                        // 多长一行再找：行数无上限，永远能放下，不会静默丢项。
                        effectiveRows += candidate.spanY
                        slot = GridPanelItemLayout.firstFreeSlot(
                            placed, cols, effectiveRows, candidate.spanX, candidate.spanY,
                        )
                    }
                    if (slot != null) {
                        placed += candidate.copy(x = slot.x, y = slot.y)
                    } else {
                        dropped += candidate
                    }
                }
            }
        }
    }
    return GridResizePlan(placed, dropped)
}
