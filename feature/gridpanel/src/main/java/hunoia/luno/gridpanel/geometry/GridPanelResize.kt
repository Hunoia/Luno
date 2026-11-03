package hunoia.luno.gridpanel.geometry

import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout

/** [planGridResize] 的结果：重排后的项 + 确实放不下、会被移除的项。 */
class GridResizePlan(
    val items: List<GridPanelItem>,
    val dropped: List<GridPanelItem>,
)

/**
 * 列数变化的落位预演（纯函数）：夹进网格 → 本页紧凑重排 → 仍放不下才进
 * [GridResizePlan.dropped]。
 *
 * 设置页弹窗预演与真实落盘共用这一份规则，所以「弹窗里说要移除哪些」就是实际会移除的。
 *
 * 两路都按页独立、按 (y, x) 排序后从左上角行优先落位：
 * - 占位集合绝不复用其它页的项，否则第 1 页及以后会把第 0 页当成障碍，
 *   表现为「上面全空却把项放到很下面」；
 * - 紧凑重排会消掉上方空洞，不再保留旧坐标。
 *
 * [expandRows] = true 时放不下就向下撑本页行数（面板内双指调列数，不静默丢项）；
 * false 时放进 [GridResizePlan.dropped]（设置页先弹确认）。
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
    val coerced = items.map { it.copy(pageIndex = it.pageIndex.coerceIn(0, pages - 1)) }
    val placedAll = mutableListOf<GridPanelItem>()
    val dropped = mutableListOf<GridPanelItem>()
    val visibleRows = gridPanelVisibleRows(
        screenWdp, screenHdp, cols, cardWidthFraction, cardHeightFraction,
    )

    for (p in 0 until pages) {
        val pageItems = coerced.filter { it.pageIndex == p }
        if (pageItems.isEmpty()) continue
        // 本页容量只由本页占用决定：跨页共享会把页面越推越下。
        var pageRows = maxOf(visibleRows, pageItems.maxOfOrNull { it.bottom } ?: 0)
            .coerceAtLeast(1)
        val placed = mutableListOf<GridPanelItem>()
        // 紧凑重排按 (y, x) 排序：原始越靠上的项越先落位，填满上方再往下。
        for (raw in pageItems.sortedWith(compareBy({ it.y }, { it.x }))) {
            // normalized 已把坐标和 span 夹进本页；下界 1×1 避免退化项。
            val candidate = raw.normalized(cols, pageRows)
            var slot = GridPanelItemLayout.firstFreeSlot(
                placed, cols, pageRows, candidate.spanX, candidate.spanY,
            )
            if (slot == null && expandRows) {
                // 向下撑一行 band：底部一定连续空着 candidate.spanY 行。
                pageRows += candidate.spanY
                slot = GridPanelItemLayout.firstFreeSlot(
                    placed, cols, pageRows, candidate.spanX, candidate.spanY,
                )
            }
            if (slot == null) {
                dropped += candidate
            } else {
                placed += candidate.copy(x = slot.x, y = slot.y)
            }
        }
        placedAll += placed
    }
    return GridResizePlan(placedAll, dropped)
}
