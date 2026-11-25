package hunoia.luno.floatball.settings.alert

import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.argbToHsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 和谐色生成算法与彩虹 toSpec 的色表口径。 */
class PaletteGeneratorsTest {

    private val base = 0xFF2196F3.toInt() // 蓝，hue≈207°

    @Test
    fun `every algorithm produces exactly max palette colors`() {
        PaletteGenerateAlgo.entries.forEach { algo ->
            val result = generatePalette(base, algo, AlertLimits.MaxPaletteColors)
            assertEquals(AlertLimits.MaxPaletteColors, result.size)
            result.forEach { assertEquals(0xFF, (it ushr 24) and 0xFF) }
        }
    }

    @Test
    fun `analogous keeps base hue in the middle and spreads outward`() {
        val result = generatePalette(base, PaletteGenerateAlgo.Analogous, 6)
        val (hue, sat, value) = argbToHsv(base)
        val middle = argbToHsv(result[2])
        val last = argbToHsv(result[5])
        // 中间色贴近基色相；尾色向一侧偏开；饱和度 / 明度继承基色。
        assertEquals(hue, middle.first, 45f)
        assertTrue(last.first != middle.first)
        assertEquals(sat, middle.second, 0.01f)
        assertEquals(value, middle.third, 0.01f)
    }

    @Test
    fun `complementary alternates base and its complement`() {
        val result = generatePalette(base, PaletteGenerateAlgo.Complementary, 6)
        val (hue, _, _) = argbToHsv(base)
        val even = argbToHsv(result[0])
        val odd = argbToHsv(result[1])
        assertEquals(hue, even.first, 0.5f)
        assertEquals((hue + 180f) % 360f, odd.first, 0.5f)
        // 交替：0/2/4 同基色相，1/3/5 同补色相。
        assertEquals(argbToHsv(result[2]).first, even.first, 0.5f)
        assertEquals(argbToHsv(result[3]).first, odd.first, 0.5f)
    }

    @Test
    fun `triadic steps one hundred twenty degrees`() {
        val result = generatePalette(base, PaletteGenerateAlgo.Triadic, 6)
        val (hue, _, _) = argbToHsv(base)
        assertEquals(hue, argbToHsv(result[0]).first, 0.5f)
        assertEquals((hue + 120f) % 360f, argbToHsv(result[1]).first, 0.5f)
        assertEquals((hue + 240f) % 360f, argbToHsv(result[2]).first, 0.5f)
    }

    @Test
    fun `shades descend from light to dark at same hue`() {
        val result = generatePalette(base, PaletteGenerateAlgo.Shades, 6)
        val (hue, _, _) = argbToHsv(base)
        val values = result.map { argbToHsv(it).third }
        result.forEachIndexed { i, argb ->
            assertEquals(hue, argbToHsv(argb).first, 0.5f)
            if (i > 0) assertTrue("shades must strictly descend", values[i] < values[i - 1])
        }
    }

    @Test
    fun `count is clamped to palette bounds`() {
        assertEquals(1, generatePalette(base, PaletteGenerateAlgo.Shades, 0).size)
        assertEquals(AlertLimits.MaxPaletteColors, generatePalette(base, PaletteGenerateAlgo.Shades, 99).size)
    }
}
