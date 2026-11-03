package hunoia.luno.capability.ocr.pp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 超宽行切段：模型输入宽度固定（320 / 640 / 960 三档），宽高比超限的行必须切段而不能压扁。
 *
 * 锁五条语义：每段不压扁、切段覆盖整行、相邻段有重叠、边界处「上一段完整看到 / 本段完整
 * 看到」的丢弃窗口、以及相邻段保留区间首尾相接（多字/错字的根因）。
 */
class PpOcrLineSegmentsTest {

    /** 字形宽度上界（与实现一致：1.3 个字高）。 */
    private fun glyphWidth(height: Int) = height * 1.3

    private fun assertNoSqueeze(
        height: Int,
        segments: List<LineSegment>,
        modelWidth: Int = PpOcrRecognizer.W,
        modelHeight: Int = PpOcrRecognizer.H,
    ) {
        for (s in segments) {
            val w = s.x1 - s.x0
            val resized = modelHeight.toDouble() * w / height
            assertTrue(
                "段 $s resize 后宽 $resized 超过 $modelWidth（会被压扁）",
                resized <= modelWidth + 1e-6,
            )
        }
    }

    private fun assertCovered(width: Int, segments: List<LineSegment>) {
        assertEquals("首段应从 0 开始", 0, segments.first().x0)
        assertEquals("末段应到行尾", width, segments.last().x1)
        assertEquals("首段无重叠前缀", 0, segments.first().overlapSrc)
        assertEquals("首段无丢弃前缀", 0, segments.first().dropSrc)
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
                "相邻段重叠=$overlap 应 ≥ 2 个字高（$height），否则重叠区上下文/完整字形不足",
                overlap >= height * 2.0 - 1,
            )
            assertEquals(
                "后段 overlapSrc 应等于实际重叠宽度",
                overlap,
                segments[i].overlapSrc,
            )
        }
    }

    /**
     * 丢弃窗口的语义（边界处多字/错字的根因）：
     * - 前缀按**实际重叠**算：`dropSrc = 实际重叠 - 半个字形宽`。被丢弃的字右沿不越过重叠区
     *   ⇒ 上一段里完整；保留的字左沿不越过本段起点 ⇒ 本段里完整。
     * - 非末段尾部再丢半个字形宽：那半截字只有一部分在本段里，交给下一段完整识别。
     * - 相邻两段的保留区间必须**首尾相接**：上一段保留到 `段宽 - 半个字`，下一段恰好从
     *   该点开始保留，不重不漏。
     */
    private fun assertDropWindow(height: Int, segments: List<LineSegment>) {
        val half = (glyphWidth(height) / 2f).roundToInt()
        for (i in 1 until segments.size) {
            val seg = segments[i]
            assertEquals(
                "段 $seg 的前缀丢弃应按实际重叠 ${seg.overlapSrc} 算",
                (seg.overlapSrc - half).coerceAtLeast(0),
                seg.dropSrc,
            )
            assertTrue(
                "段 $seg dropSrc=${seg.dropSrc} 小于半个字形宽 $half：会把本段里被切掉半边笔画的字当成已出过",
                seg.dropSrc >= half - 1,
            )
        }
        for (i in 0 until segments.lastIndex) {
            val seg = segments[i]
            val next = segments[i + 1]
            assertEquals("非末段尾部应丢半个字形宽", half, seg.dropTailSrc)
            val keepEnd = (seg.x1 - seg.x0) - seg.dropTailSrc
            val nextKeepStart = (next.x0 - seg.x0) + next.dropSrc
            assertEquals(
                "段 $seg 与下一段 $next 的保留区间没有首尾相接（会多字或丢字）",
                keepEnd,
                nextKeepStart,
            )
            assertTrue("段 $seg 保留区间为空：$seg", keepEnd > seg.dropSrc)
        }
        assertEquals("末段没有下一段接手，尾部必须全保留", 0, segments.last().dropTailSrc)
    }

    private fun checkAll(width: Int, height: Int, modelWidth: Int = PpOcrRecognizer.W) {
        val segments = PpOcrRecognizer.lineSegments(width, height, modelWidth)
        assertTrue("应切出多段：$segments", segments.size > 1)
        assertNoSqueeze(height, segments, modelWidth)
        assertCovered(width, segments)
        assertOverlapContainsAChar(height, segments)
        assertDropWindow(height, segments)
    }

    @Test
    fun `任意行宽都满足保留区间首尾相接`() {
        // 均分步进后实际重叠常常大于计划值；这里扫一批宽度/高度/模型档位，钉死
        // “上一段保留到哪、下一段从哪开始”严格对齐，防止边界多字或丢字。
        for (height in listOf(10, 48, 120)) {
            for (width in listOf(833, 960, 1000, 1904, 2975, 5000)) {
                for (modelWidth in listOf(320, 640, 960)) {
                    val segments = PpOcrRecognizer.lineSegments(width, height, modelWidth)
                    if (segments.size <= 1) continue
                    assertNoSqueeze(height, segments, modelWidth)
                    assertCovered(width, segments)
                    assertOverlapContainsAChar(height, segments)
                    assertDropWindow(height, segments)
                }
            }
        }
    }

    @Test
    fun `窄行不切段`() {
        // 5 个 CJK 字：5*48 = 240 < 320
        val segments = PpOcrRecognizer.lineSegments(240, 48)
        assertEquals(listOf(LineSegment(0, 240, 0, 0)), segments)
    }

    @Test
    fun `恰好达到宽高比上限的行不切段`() {
        // 320/48 ≈ 6.67 个字高：整行 resize 后刚好 320 宽
        val segments = PpOcrRecognizer.lineSegments(320, 48)
        assertEquals(listOf(LineSegment(0, 320, 0, 0)), segments)
    }

    @Test
    fun `超宽行切段且不压扁`() {
        // 20 个 CJK 字：960 宽，必须切段
        checkAll(width = 960, height = 48)
    }

    @Test
    fun `高行同样适用（大字号长行）`() {
        // det 框高 120、宽 1000 的截图大字行：单段最宽 = 320*120/48 = 800，需切段
        checkAll(width = 1000, height = 120)
    }

    @Test
    fun `极宽行也能全覆盖`() {
        // 40 个字：1920 宽
        checkAll(width = 1920, height = 48)
    }

    @Test
    fun `宽 rec 模型切段更少`() {
        val narrow = PpOcrRecognizer.lineSegments(1920, 48, modelWidth = 320)
        val wide = PpOcrRecognizer.lineSegments(1920, 48, modelWidth = 960)
        assertTrue("960 档应显著少切：${wide.size} vs ${narrow.size}", wide.size < narrow.size)
        assertNoSqueeze(48, wide, modelWidth = 960)
        assertCovered(1920, wide)
        assertDropWindow(48, wide)
    }

    @Test
    fun `窄行在宽 rec 模型下不切段`() {
        // 20 个字：960 宽，在 960 档 input 下刚好一整段
        val segments = PpOcrRecognizer.lineSegments(960, 48, modelWidth = 960)
        assertEquals(listOf(LineSegment(0, 960, 0, 0)), segments)
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

    @Test
    fun `解码坐标随 rec 宽度缩放`() {
        // 960 档：4 步 CTC → x = (step+0.5) * 960/4 = 120/360
        val chars = arrayOf("", "A")
        val logits = FloatArray(4 * chars.size) { -5f }
        logits[0 * chars.size + 1] = 5f
        logits[2 * chars.size + 1] = 5f
        val decoded = PpOcrRecognizer.decodeSteps(logits, chars, width = 960)
        assertEquals(2, decoded.size)
        assertEquals(120f, decoded[0].x, 1e-4f)
        assertEquals(600f, decoded[1].x, 1e-4f)
    }
}
