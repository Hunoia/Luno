package hunoia.luno.ui.home

import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.HomeDockLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 三球停靠栏几何（ui-guidelines §4.2）：
 * 一行三槽 = 一个展开胶囊 + 两颗 60dp 球 + 两个 6dp 间隙；
 * tab 宽按 `(dockWidth - 140) / 3` 压缩，351.6dp 屏约 59.9dp，404dp 起用满 76dp，宽屏整组限宽 600dp。
 */
class HomeDockLayoutTest {

    private val tabCount = 3
    private val maxTabWidth = 76.dp
    private val innerPadding = 4.dp
    private val ballSize = 60.dp
    private val gap = 6.dp

    private fun dockWidthFor(screenWidth: Float) =
        HomeDockLayout.dockWidth(screenWidth.dp - 32.dp)

    private fun tabWidthFor(screenWidth: Float) = HomeDockLayout.tabWidth(
        dockWidth = dockWidthFor(screenWidth),
        tabCount = tabCount,
        maxTabWidth = maxTabWidth,
        innerPadding = innerPadding,
    )

    @Test
    fun `宽屏整组限宽 600dp`() {
        assertEquals(600.dp, HomeDockLayout.dockWidth(600.dp))
        assertEquals(600.dp, HomeDockLayout.dockWidth(1024.dp))
        assertEquals(288.dp, HomeDockLayout.dockWidth(288.dp))
    }

    @Test
    fun `320dp 屏 tab 约 49_3dp 且整组占满`() {
        val dockWidth = dockWidthFor(320f)
        val tabWidth = tabWidthFor(320f)
        val capsuleWidth = HomeDockLayout.capsuleWidth(tabWidth, tabCount, innerPadding)

        assertEquals(288f, dockWidth.value, 0.01f)
        assertEquals(49.33f, tabWidth.value, 0.02f)
        assertEquals(156f, capsuleWidth.value, 0.02f)
        // 156 胶囊 + 2×(6 间隙 + 60 球) = 288，左右各 16dp 边距正好 320。
        assertEquals(dockWidth.value, HomeDockLayout.rowWidth(capsuleWidth).value, 0.02f)
        assertEquals((gap + ballSize) * 2, HomeDockLayout.rowWidth(capsuleWidth) - capsuleWidth)
    }

    @Test
    fun `本机 351_6dp tab 压到约 59_9dp`() {
        val dockWidth = dockWidthFor(351.6f)
        val tabWidth = tabWidthFor(351.6f)
        val capsuleWidth = HomeDockLayout.capsuleWidth(tabWidth, tabCount, innerPadding)

        assertEquals(319.6f, dockWidth.value, 0.01f)
        assertEquals(59.87f, tabWidth.value, 0.02f)
        assertEquals(dockWidth.value, HomeDockLayout.rowWidth(capsuleWidth).value, 0.05f)
    }

    @Test
    fun `360dp 屏 tab 约 62_7dp`() {
        assertEquals(62.67f, tabWidthFor(360f).value, 0.02f)
    }

    @Test
    fun `404dp 起 tab 用满 76dp`() {
        assertEquals(76.dp, tabWidthFor(411f))
        assertEquals(76.dp, tabWidthFor(1024f))
        assertEquals(236.dp, HomeDockLayout.capsuleWidth(76.dp, tabCount, innerPadding))
    }

    @Test
    fun `功能球隐藏时 tab 多分一颗球加一个间隙`() {
        // 主页 / 设置页没有功能球：collapsedSlots 从 2 降到 1。
        // 320dp：288 − (60+6) − 8 = 214 → 71.33。
        assertEquals(
            71.33f,
            HomeDockLayout.tabWidth(288.dp, tabCount, maxTabWidth, innerPadding, collapsedSlots = 1).value,
            0.02f,
        )
        // 360dp：254 / 3 = 84.67，封顶 76。
        assertEquals(
            76.dp,
            HomeDockLayout.tabWidth(328.dp, tabCount, maxTabWidth, innerPadding, collapsedSlots = 1),
        )
    }

    @Test
    fun `更窄的屏只压到可点下限`() {
        // 低于 316dp 时保持 48dp 下限，整组向左右 16dp 边距里溢出，不裁切。
        assertEquals(48.dp, HomeDockLayout.tabWidth(160.dp, tabCount, maxTabWidth, innerPadding))
    }
}
