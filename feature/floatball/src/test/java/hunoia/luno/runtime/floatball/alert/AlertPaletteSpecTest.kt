package hunoia.luno.runtime.floatball.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import hunoia.luno.config.model.rainbowRing

/**
 * 多色取色的口径：周长 / 角度采样必须**环形插值**（相邻两色过渡、末色接回首色），
 * 不是分桶——分桶在屏缘上就是 5 段硬色带（ui-guidelines §2.4「沿边缘渐变」）。
 */
class AlertPaletteSpecTest {

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun `single color palette always returns the only color`() {
        assertEquals(red, sampleArgb(intArrayOf(red), 0f))
        assertEquals(red, sampleArgb(intArrayOf(red), 0.31f))
        assertEquals(red, sampleArgb(intArrayOf(red), 0.99f))
    }

    @Test
    fun `empty palette falls back to transparent`() {
        assertEquals(0, sampleArgb(intArrayOf(), 0.5f))
    }

    @Test
    fun `sampling interpolates between adjacent colors`() {
        val colors = intArrayOf(red, green)
        // 2 色环：t = 0 / 0.5 正好落在两个色的顶点上；t = 0.25 是两色中点。
        assertEquals(red, sampleArgb(colors, 0f))
        assertEquals(green, sampleArgb(colors, 0.5f))
        assertEquals(0xFF808000.toInt(), sampleArgb(colors, 0.25f))
        assertEquals(red, sampleArgb(colors, 1f))
    }

    @Test
    fun `sampling wraps from the last color back to the first`() {
        val colors = intArrayOf(red, green, blue)
        // t = 0.75 → x = 2.25 → 蓝 75% / 红 25% 的过渡（环形，不是停在蓝）。
        assertEquals(0xFF4000BF.toInt(), sampleArgb(colors, 0.75f))
    }

    @Test
    fun `sampling normalizes negative and out of range positions`() {
        val colors = intArrayOf(red, green)
        assertEquals(sampleArgb(colors, 0.25f), sampleArgb(colors, -0.75f))
        assertEquals(sampleArgb(colors, 0.25f), sampleArgb(colors, 5.25f))
    }

    @Test
    fun `lerp walks each argb channel linearly`() {
        assertEquals(red, lerpArgb(red, green, 0f))
        assertEquals(green, lerpArgb(red, green, 1f))
        // 越界比例夹回 0..1，不产生溢出色。
        assertEquals(red, lerpArgb(red, green, -2f))
        assertEquals(green, lerpArgb(red, green, 3f))
    }

    @Test
    fun `rainbow spec carries the parameterized ring`() {
        val source = hunoia.luno.config.model.BuiltinAlertPalettes.Rainbow.copy(
            rainbowHueOffset = 90,
            rainbowReverse = true,
        )
        val spec = source.toSpec()
        assertTrue(spec.isRainbow)
        assertEquals(hunoia.luno.config.model.AlertLimits.MaxPaletteColors, spec.colors.size)
        // 环由参数生成，与预览共用同一份色表。
        assertEquals(source.rainbowRing(), spec.colors)
    }

    @Test
    fun `non rainbow spec truncates to palette max`() {
        val colors = List(hunoia.luno.config.model.AlertLimits.MaxPaletteColors + 2) { red }
        val spec = hunoia.luno.config.model.AlertPalette(
            id = "t",
            name = "t",
            colors = colors,
        ).toSpec()
        assertEquals(hunoia.luno.config.model.AlertLimits.MaxPaletteColors, spec.colors.size)
        assertTrue(!spec.isRainbow)
    }
}
