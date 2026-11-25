package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 彩虹色相环参数化与 HSV 转换（纯 JVM，无 Compose / android 依赖）。 */
class AlertPaletteRainbowTest {

    @Test
    fun `default rainbow ring has max palette colors`() {
        val ring = BuiltinAlertPalettes.Rainbow.rainbowRing()
        assertEquals(AlertLimits.MaxPaletteColors, ring.size)
        // 默认全饱和 / 全亮度：每个采样都无透明、非黑。
        ring.forEach { argb ->
            assertEquals(0xFF, (argb ushr 24) and 0xFF)
            assertTrue("expected vivid color but got $argb", argb != 0xFF000000.toInt())
        }
    }

    @Test
    fun `hue offset rotates the ring`() {
        val base = BuiltinAlertPalettes.Rainbow
        val shifted = base.copy(rainbowHueOffset = 60)
        val a = base.rainbowRing()
        val b = shifted.rainbowRing()
        // 偏移 60°：首色 hue 从 0 → 60，环上整体旋转，色表不同。
        assertNotEquals(a[0], b[0])
        // 旋转不改变色数。
        assertEquals(a.size, b.size)
    }

    @Test
    fun `saturation and value ranges shrink toward a tint`() {
        val palette = BuiltinAlertPalettes.Rainbow.copy(
            rainbowSatMin = 0,
            rainbowSatMax = 0,
            rainbowValMin = 100,
            rainbowValMax = 100,
        )
        val ring = palette.rainbowRing()
        // s=0 → 全灰（等通道），不可能是饱和的纯色相。
        ring.forEach { argb ->
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            assertEquals(r, g)
            assertEquals(g, b)
        }
    }

    @Test
    fun `reverse flips the ring order`() {
        val base = BuiltinAlertPalettes.Rainbow.rainbowRing()
        val reversed = BuiltinAlertPalettes.Rainbow.copy(rainbowReverse = true).rainbowRing()
        assertEquals(base.reversed(), reversed)
    }

    @Test
    fun `hsv round trips through argb`() {
        val cases = listOf(
            Triple(0f, 1f, 1f) to 0xFFFF0000.toInt(),
            Triple(120f, 1f, 1f) to 0xFF00FF00.toInt(),
            Triple(240f, 1f, 1f) to 0xFF0000FF.toInt(),
            Triple(0f, 0f, 1f) to 0xFFFFFFFF.toInt(),
        )
        cases.forEach { (hsv, argb) ->
            val roundTrip = argbToHsv(argb)
            assertEquals(hsv.first, roundTrip.first, 0.5f)
            assertEquals(hsv.second, roundTrip.second, 0.01f)
            assertEquals(hsv.third, roundTrip.third, 0.01f)
            // 往返生成的颜色通道一致（容忍 ±1 取整）。
            val back = hsvToArgb(roundTrip.first, roundTrip.second, roundTrip.third)
            assertTrue(channelDiff((argb ushr 16) and 0xFF, (back ushr 16) and 0xFF) <= 1)
            assertTrue(channelDiff((argb ushr 8) and 0xFF, (back ushr 8) and 0xFF) <= 1)
            assertTrue(channelDiff(argb and 0xFF, back and 0xFF) <= 1)
        }
    }

    private fun channelDiff(a: Int, b: Int): Int = kotlin.math.abs(a - b)
}
