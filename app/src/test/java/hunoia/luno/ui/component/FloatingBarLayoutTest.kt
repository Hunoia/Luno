package hunoia.luno.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 类型 chip 底栏几何（ui-guidelines §4.2）：与主页三球停靠栏共用 [FloatingBarLayout]。
 *
 * 胶囊可用宽 = 屏宽 − 2×`DockGutter` 16；右端搜索槽先扣 `DockTabHeight` 52；
 * 剩下按 chip 数均分并夹在 48..76dp，极窄屏按可用宽兜底压缩。
 */
class FloatingBarLayoutTest {

    private val gutter = 16.dp
    private val innerPadding = 4.dp
    private val searchSlot = 52.dp
    private val maxTabWidth = 76.dp
    private val minTabWidth = 48.dp

    private fun barWidthFor(screenWidth: Float) =
        FloatingBarLayout.barWidth((screenWidth - gutter.value * 2).dp)

    private fun chipWidthFor(screenWidth: Float, chipCount: Int): Dp {
        val barWidth = barWidthFor(screenWidth)
        val raw = (barWidth - searchSlot - innerPadding * 2) / chipCount
        return min(
            FloatingBarLayout.tabWidth(
                barWidth = barWidth,
                tabCount = chipCount,
                maxTabWidth = maxTabWidth,
                innerPadding = innerPadding,
                reservedWidth = searchSlot,
                minTabWidth = minTabWidth,
            ),
            raw,
        )
    }

    private fun capsuleWidthFor(screenWidth: Float, chipCount: Int): Dp =
        FloatingBarLayout.capsuleWidth(
            tabWidth = chipWidthFor(screenWidth, chipCount),
            tabCount = chipCount,
            innerPadding = innerPadding,
            trailingSlot = searchSlot,
        )

    @Test
    fun `本机 351_6dp 普通路径 3 chip 封顶 76dp、胶囊 288dp 居中`() {
        assertEquals(319.65f, barWidthFor(351.65f).value, 0.02f)
        assertEquals(76f, chipWidthFor(351.65f, 3).value, 0.02f)
        assertEquals(288f, capsuleWidthFor(351.65f, 3).value, 0.02f)
    }

    @Test
    fun `本机 351_6dp 面板路径 4 chip 约 64_9dp、胶囊铺满可用宽`() {
        assertEquals(64.91f, chipWidthFor(351.65f, 4).value, 0.03f)
        assertEquals(319.65f, capsuleWidthFor(351.65f, 4).value, 0.03f)
    }

    @Test
    fun `320dp 屏两条路径都铺满 288dp`() {
        assertEquals(76f, chipWidthFor(320f, 3).value, 0.02f)
        assertEquals(288f, capsuleWidthFor(320f, 3).value, 0.02f)
        assertEquals(57f, chipWidthFor(320f, 4).value, 0.02f)
        assertEquals(288f, capsuleWidthFor(320f, 4).value, 0.02f)
    }

    @Test
    fun `280dp 屏 4 chip 跌破 48dp 时按可用宽压缩、不溢出`() {
        val barWidth = barWidthFor(280f)
        val chipWidth = chipWidthFor(280f, 4)
        assertEquals(248f, barWidth.value, 0.02f)
        assertEquals(47f, chipWidth.value, 0.02f)
        assertEquals(barWidth.value, capsuleWidthFor(280f, 4).value, 0.02f)
    }

    @Test
    fun `宽屏整组限宽 600dp、胶囊按内容宽不再拉满`() {
        assertEquals(600f, barWidthFor(1024f).value, 0.02f)
        assertEquals(76f, chipWidthFor(1024f, 3).value, 0.02f)
        assertEquals(288f, capsuleWidthFor(1024f, 3).value, 0.02f)
    }
}
