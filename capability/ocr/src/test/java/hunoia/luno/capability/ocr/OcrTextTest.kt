package hunoia.luno.capability.ocr

import hunoia.luno.config.model.OcrScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextTest {

    private fun block(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        script: OcrScript = OcrScript.Chinese,
    ) = OcrBlock(text, left, top, right, bottom, script)

    @Test
    fun `merge keeps the longer Japanese reading of the same box`() {
        val result = OcrText.merge(
            listOf(
                block("東京", 0, 0, 200, 40, OcrScript.Chinese),
                block("東京へ行こう", 2, 2, 198, 42, OcrScript.Japanese),
            ),
        )
        assertEquals(1, result.blocks.size)
        assertEquals("東京へ行こう", result.blocks.single().text)
        assertEquals(OcrScript.Japanese, result.blocks.single().script)
    }

    @Test
    fun `merge treats a mostly nested box as the same text`() {
        val result = OcrText.merge(
            listOf(
                block("短句", 10, 10, 210, 60),
                block("短句加长版", 30, 20, 170, 45),
            ),
        )
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun `merge drops split pieces covered by a wider block of the same line`() {
        val result = OcrText.merge(
            listOf(
                block("周四 萧山区", 10, 100, 300, 140),
                block("周四", 10, 102, 100, 138, OcrScript.Japanese),
                block("萧山区", 150, 101, 298, 139, OcrScript.Japanese),
            ),
        )
        assertEquals(listOf("周四 萧山区"), result.blocks.map { it.text })
    }

    @Test
    fun `merge keeps side by side text on the same line`() {
        val result = OcrText.merge(
            listOf(
                block("周四", 10, 100, 110, 140),
                block("萧山区", 150, 100, 300, 140),
            ),
        )
        assertEquals(2, result.blocks.size)
    }

    @Test
    fun `merge keeps consecutive lines`() {
        val result = OcrText.merge(
            listOf(
                block("第一行", 10, 100, 300, 140),
                block("第二行", 10, 160, 300, 200),
            ),
        )
        assertEquals(2, result.blocks.size)
    }

    @Test
    fun `merge keeps separate boxes and sorts by reading order`() {
        val result = OcrText.merge(
            listOf(
                block("second", 10, 100, 110, 140),
                block("first", 10, 10, 110, 50),
            ),
        )
        assertEquals(listOf("first", "second"), result.blocks.map { it.text })
    }

    @Test
    fun `merge drops blank blocks`() {
        val result = OcrText.merge(listOf(block("   ", 0, 0, 10, 10), block("ok", 0, 20, 10, 30)))
        assertEquals(listOf("ok"), result.blocks.map { it.text })
    }

    @Test
    fun `blockAt prefers the innermost containing box`() {
        val outer = block("outer line", 0, 0, 400, 80)
        val inner = block("inner", 100, 20, 200, 50)
        val hit = OcrText.blockAt(listOf(outer, inner), 150, 30, tolerancePx = 0, maxDistancePx = 0)
        assertEquals(inner, hit)
    }

    @Test
    fun `blockAt respects tolerance`() {
        val text = block("word", 100, 100, 200, 140)
        assertNull(OcrText.blockAt(listOf(text), 90, 100, tolerancePx = 0, maxDistancePx = 0))
        assertEquals(
            text,
            OcrText.blockAt(listOf(text), 90, 100, tolerancePx = 12, maxDistancePx = 0),
        )
    }

    @Test
    fun `blockAt falls back to the nearest box within distance`() {
        val text = block("word", 100, 100, 200, 140)
        assertEquals(
            text,
            OcrText.blockAt(listOf(text), 250, 120, tolerancePx = 0, maxDistancePx = 60),
        )
        assertNull(OcrText.blockAt(listOf(text), 250, 120, tolerancePx = 0, maxDistancePx = 20))
    }

    @Test
    fun `blocksIn keeps only intersecting boxes`() {
        val inside = block("inside", 120, 120, 180, 160)
        val touching = block("edge", 200, 120, 260, 160)
        val outside = block("outside", 400, 400, 500, 440)
        val result = OcrText.blocksIn(listOf(inside, touching, outside), 100, 100, 200, 200)
        assertEquals(listOf(inside), result)
    }

    @Test
    fun `join groups same line and orders lines top to bottom`() {
        val text = OcrText.join(
            listOf(
                block("world", 120, 12, 220, 48),
                block("second line", 10, 100, 200, 140),
                block("hello", 10, 10, 110, 50),
            ),
        )
        assertEquals("hello world\nsecond line", text)
    }

    @Test
    fun `join returns empty string for no blocks`() {
        assertTrue(OcrText.join(emptyList()).isEmpty())
    }
}
