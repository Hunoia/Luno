package hunoia.luno.capability.ocr.pp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 超宽行切段：模型输入宽度固定 320（高 48），宽高比超限的行必须切段而不能压扁。
 * 锁四条语义：每段不压扁、切段覆盖整行、相邻段有重叠、重叠区完整包含至少一个字高
 * （拼接去重的前提：重叠区里有完整字形，后段按前缀丢弃不丢字也不多字）。
 */
class PpOcrLineSegmentsTest {

    private fun assertNoSqueeze(height: Int, segments: List<LineSegment>) {
        for (s in segments) {
            val w = s.x1 - s.x0
            val resized = PpOcrRecognizer.H.toDouble() * w / height
            assertTrue(
                "段 $s resize 后宽 $resized 超过 ${PpOcrRecognizer.W}（会被压扁）",
                resized <= PpOcrRecognizer.W + 1e-6,
            )
        }
    }

    private fun assertCovered(width: Int, segments: List<LineSegment>) {
        assertEquals("首段应从 0 开始", 0, segments.first().x0)
        assertEquals("末段应到行尾", width, segments.last().x1)
        assertEquals("首段无重叠前缀", 0, segments.first().overlapSrc)
        for (i in 1 until segments.size) {
            assertTrue(
                "段 ${segments[i - 1]} 与 ${segments[i]} 之间有缝隙",
                segments[i].x0 <= segments[i - 1].x1,
            )
        }
    }

    private fun assertOverlapContainsAChar(height: Int, segments: List<LineSegment>) {
        for (i in 1 until segments.size) {
            val overlap = segments[i - 1].x1 - segments[i].x0
            assertTrue(
                "相邻段重叠=$overlap 应 ≥ 一个字高（$height），否则重叠区可能不含完整字形",
                overlap >= height,
            )
            assertEquals(
                "后段 overlapSrc 应等于实际重叠宽度",
                overlap,
                segments[i].overlapSrc,
            )
        }
    }

    @Test
    fun `窄行不切段`() {
        // 5 个 CJK 字：5*48 = 240 < 320
        val segments = PpOcrRecognizer.lineSegments(240, 48)
        assertEquals(listOf(LineSegment(0, 240, 0)), segments)
    }

    @Test
    fun `恰好达到宽高比上限的行不切段`() {
        // 320/48 ≈ 6.67 个字高：整行 resize 后刚好 320 宽
        val segments = PpOcrRecognizer.lineSegments(320, 48)
        assertEquals(listOf(LineSegment(0, 320, 0)), segments)
    }

    @Test
    fun `超宽行切段且不压扁`() {
        // 20 个 CJK 字：960 宽，必须切段
        val segments = PpOcrRecognizer.lineSegments(960, 48)
        assertTrue("应切出多段：$segments", segments.size > 1)
        assertNoSqueeze(48, segments)
        assertCovered(960, segments)
        assertOverlapContainsAChar(48, segments)
    }

    @Test
    fun `高行同样适用（大字号长行）`() {
        // det 框高 120、宽 1000 的截图大字行：单段最宽 = 320*120/48 = 800，需切段
        val segments = PpOcrRecognizer.lineSegments(1000, 120)
        assertTrue(segments.size > 1)
        assertNoSqueeze(120, segments)
        assertCovered(1000, segments)
        assertOverlapContainsAChar(120, segments)
    }

    @Test
    fun `极宽行也能全覆盖`() {
        // 40 个字：1920 宽
        val segments = PpOcrRecognizer.lineSegments(1920, 48)
        assertNoSqueeze(48, segments)
        assertCovered(1920, segments)
        assertOverlapContainsAChar(48, segments)
    }

    @Test
    fun `解码字符带输入宽度坐标`() {
        // 4 步 CTC：x = (step+0.5) * 320/4 = 40/120/200/280
        val chars = arrayOf("", "A", " ")
        val path = intArrayOf(1, 0, 1, 1)
        val logits = FloatArray(path.size * chars.size) { -5f }
        path.forEachIndexed { time, token -> logits[time * chars.size + token] = 5f }
        val decoded = PpOcrRecognizer.decodeSteps(logits, chars)
        assertEquals(2, decoded.size)
        assertEquals(40f, decoded[0].x, 1e-4f)
        assertEquals(200f, decoded[1].x, 1e-4f)
        assertEquals("AA", PpOcrRecognizer.decode(logits, chars))
    }
}
