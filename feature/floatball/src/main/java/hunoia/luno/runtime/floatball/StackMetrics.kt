package hunoia.luno.runtime.floatball

/**
 * 通知胶囊「滚动栈」的几何：视口最多 [visibleRows] 颗胶囊，内容 = 队列全部条目。
 *
 * 每颗胶囊独立成块，之间留 [gapPx] 的透明间距；窗口高、可滚动距离、触摸命中
 * （[rowAt]）都从这一份计算出来。以前行高直接取球高、条数硬编码 3，窗口与命中
 * 各算一遍，改一处就会错位。
 *
 * 约定：`offsetPx` 是内容的竖向位移，0 = 顶部（最新一条），向下滚为负。
 */
internal data class StackMetrics(
    val rowHeightPx: Int,
    val itemCount: Int,
    val visibleRows: Int,
    val gapPx: Int = 0,
) {
    val visibleCount: Int get() = itemCount.coerceIn(0, visibleRows.coerceAtLeast(1))

    private val safeGapPx: Int get() = gapPx.coerceAtLeast(0)

    /** 相邻两颗胶囊起点之间的距离；命中计算按它前进。 */
    val pitchPx: Int get() = rowHeightPx + safeGapPx

    val contentHeightPx: Int get() = rowsHeight(itemCount.coerceAtLeast(0))

    val viewportHeightPx: Int get() = rowsHeight(visibleCount)

    /** 可滚动距离；内容不溢出时为 0。 */
    val maxScrollPx: Int get() = (contentHeightPx - viewportHeightPx).coerceAtLeast(0)

    /** 把拖动 / 惯性投影夹进合法区间。 */
    fun clamp(offset: Float): Float = offset.coerceIn(-maxScrollPx.toFloat(), 0f)

    /**
     * 窗口内的 y → 条目下标（堆叠模式下点按与横滑都按行路由）。
     *
     * 落在两颗胶囊之间的间距里时归上方那颗：间距只有几 dp，不留触摸死区。
     */
    fun rowAt(y: Float, offset: Float): Int {
        if (itemCount <= 0 || rowHeightPx <= 0) return 0
        val pitch = pitchPx.coerceAtLeast(1)
        return ((y - offset) / pitch).toInt().coerceIn(0, itemCount - 1)
    }

    /** [rows] 颗胶囊 + 之间 (rows-1) 段间距的总高。 */
    private fun rowsHeight(rows: Int): Int {
        if (rows <= 0) return 0
        return rows * rowHeightPx + (rows - 1) * safeGapPx
    }
}
