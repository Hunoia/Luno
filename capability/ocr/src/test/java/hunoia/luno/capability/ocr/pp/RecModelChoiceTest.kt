package hunoia.luno.capability.ocr.pp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * rec 宽度自动选择：短行用窄模型、宽行升档、都放不下时用最宽模型切段。
 * 纯函数，不依赖 Android。
 */
class RecModelChoiceTest {

    private val h = PpOcrRecognizer.H
    private val all = listOf(320, 640, 960)

    private fun pick(width: Int, height: Int = h, widths: List<Int> = all) =
        RecModelChoice.pick(width, height, h, widths)

    @Test
    fun `短行用 320`() {
        assertEquals(320, pick(240))
        // 恰好 320：整行正好放得下，不需要升档。
        assertEquals(320, pick(320))
    }

    @Test
    fun `中等宽行升到 640`() {
        assertEquals(640, pick(500))
        assertEquals(640, pick(640))
    }

    @Test
    fun `很宽的行升到 960`() {
        assertEquals(960, pick(800))
        assertEquals(960, pick(960))
    }

    @Test
    fun `超过最宽模型时仍选最宽并由切段兜底`() {
        assertEquals(960, pick(2000))
    }

    @Test
    fun `只装一档时始终用它`() {
        assertEquals(320, pick(2000, widths = listOf(320)))
        assertEquals(640, pick(100, widths = listOf(640)))
    }

    @Test
    fun `只装 320 与 640 时不会选 960`() {
        assertEquals(320, pick(300, widths = listOf(320, 640)))
        assertEquals(640, pick(500, widths = listOf(320, 640)))
        assertEquals(640, pick(2000, widths = listOf(320, 640)))
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(pick(0))
        assertNull(pick(100, height = 0))
        assertNull(pick(100, widths = emptyList()))
    }

    @Test
    fun `选择考虑行高：同样的宽行在大字号下能用窄模型`() {
        // 1000px 宽、行高 100：缩到 48 高后自然宽 480 → 640；若行高 200 → 240 → 320。
        assertEquals(640, pick(1000, height = 100))
        assertEquals(320, pick(1000, height = 200))
    }
}
