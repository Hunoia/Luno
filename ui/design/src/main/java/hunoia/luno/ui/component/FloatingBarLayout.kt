package hunoia.luno.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.DockMaxWidth
import hunoia.luno.ui.theme.DockMinTabWidth

/**
 * 底栏族的共用纯几何（ui-guidelines §4.2）。
 *
 * 主页三球停靠栏（[HomeDockLayout]）与类型 chip 底栏都从这一份算术出宽度，避免两边
 * 各写一套后漂移。只依赖 `DockDimension.kt` 的纯尺寸 token；`BottomBarTabWidth` /
 * `TileInnerPadding` 由调用方传入，避免这里触发 `Dimension.kt` 的类初始化
 * （见 DockDimension.kt 的说明）。
 *
 * [barWidth] 是**整组可用宽**（已扣掉左右 gutter 与横向安全区），[reservedWidth] 是同一行里
 * 不属于 tab 的固定占位（三球停靠栏的收起球 + 间隙、chip 底栏右端的搜索槽）。
 */
object FloatingBarLayout {

    /** 宽屏限宽：平板 / 横屏不把底栏拉到屏幕两端。 */
    fun barWidth(availableWidth: Dp): Dp =
        if (availableWidth > DockMaxWidth) DockMaxWidth else availableWidth

    /**
     * tab 等分：先扣掉固定占位与胶囊内边距，再按 tab 数均分，并夹在
     * `[minTabWidth, maxTabWidth]` 之间。
     *
     * 调用方如果可能窄到连 [minTabWidth] 都放不下（例如带搜索槽的 chip 底栏），
     * 用 `minOf(返回值, 未夹取的等分值)` 兜底，宁可压缩也不让胶囊溢出可用宽。
     */
    fun tabWidth(
        barWidth: Dp,
        tabCount: Int,
        maxTabWidth: Dp,
        innerPadding: Dp,
        reservedWidth: Dp = 0.dp,
        minTabWidth: Dp = DockMinTabWidth,
    ): Dp {
        if (tabCount <= 0) return maxTabWidth
        val available = barWidth - reservedWidth - innerPadding * 2
        return (available / tabCount).coerceIn(minTabWidth, maxTabWidth)
    }

    /** 胶囊宽 = 等宽 tab × 数量 + 右端固定槽 + 左右内边距；宽屏不满时按内容宽居中。 */
    fun capsuleWidth(
        tabWidth: Dp,
        tabCount: Int,
        innerPadding: Dp,
        trailingSlot: Dp = 0.dp,
    ): Dp = tabWidth * tabCount + trailingSlot + innerPadding * 2
}
