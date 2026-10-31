package hunoia.luno.ui.ocr

import hunoia.luno.capability.ocr.OcrLine
import hunoia.luno.capability.ocr.OcrWord
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果页多选：下标口径（全局词下标 ↔ 行内字符区间）与拼接顺序必须唯一。
 * 这套函数同时供高亮绘制、点击命中和“复制/搜索/分享”使用，分叉就会出现
 * “高亮的是这几个词、复制出来却是别的”。
 */
class OcrSelectionTest {

    private fun line(vararg words: Pair<String, IntRange>): OcrLine {
        val text = StringBuilder()
        val list = words.map { (token, range) ->
            if (text.length < range.first) text.append(" ".repeat(range.first - text.length))
            text.append(token)
            OcrWord(token, range.first, range.last + 1)
        }
        return OcrLine(text.toString(), list)
    }

    @Test
    fun `lineStarts 跨行累计，空行不占下标`() {
        val lines = listOf(
            line("ab" to 0..1, "cd" to 3..4),
            OcrLine("   ", emptyList()),
            line("ef" to 0..1),
        )
        val starts = lineStartsOf(lines)
        assertEquals(listOf(0, 2, 2), starts.toList())
    }

    @Test
    fun `toggle 加选与取消`() {
        assertEquals(setOf(3), toggle(emptySet(), 3))
        assertEquals(emptySet<Int>(), toggle(setOf(3), 3))
        assertEquals(setOf(1, 2), toggle(setOf(1), 2))
    }

    @Test
    fun `wordRange 与拖动方向无关`() {
        assertEquals(2..5, wordRange(5, 2))
        assertEquals(2..5, wordRange(2, 5))
    }

    @Test
    fun `selectedRanges 只取选中词并合并相邻区间`() {
        // 文本 "abcdef"，词：[0,2) [2,4) [5,6)
        val l = line("ab" to 0..1, "cd" to 2..3, "f" to 5..5)
        assertEquals(emptyList<Pair<Int, Int>>(), selectedRanges(l, 0, emptySet()))
        // 前两个词相邻 → 合并成一段；第三个词单独一段。
        assertEquals(listOf(0 to 4, 5 to 6), selectedRanges(l, 0, setOf(0, 1, 2)))
        // 只选第二个词：全局下标偏移要生效，行内区间仍是该词的。
        assertEquals(listOf(2 to 4), selectedRanges(l, 0, setOf(1)))
    }

    @Test
    fun `selectedRanges 用全局下标区分不同行`() {
        val l1 = line("ab" to 0..1)
        val l2 = line("cd" to 0..1)
        val starts = lineStartsOf(listOf(l1, l2))
        assertEquals(listOf(0 to 2), selectedRanges(l1, starts[0], setOf(0)))
        assertEquals(emptyList<Pair<Int, Int>>(), selectedRanges(l2, starts[1], setOf(0)))
        assertEquals(listOf(0 to 2), selectedRanges(l2, starts[1], setOf(1)))
    }

    @Test
    fun `joinSelected 行内连续直接拼接，行间换行`() {
        val l1 = line("你好" to 0..1, "世界" to 2..3)
        val l2 = line("再见" to 0..1)
        val lines = listOf(l1, l2)
        val starts = lineStartsOf(lines)

        assertEquals(
            "你好世界\n再见",
            joinSelected(lines, starts, setOf(0, 1, 2)),
        )
        // 只选第二行：不产生前导换行。
        assertEquals("再见", joinSelected(lines, starts, setOf(2)))
        // 未选中任何词时返回空串，由调用方回退全文。
        assertEquals("", joinSelected(lines, starts, emptySet()))
    }

    @Test
    fun `joinSelected 不连续选词补空格，不生出假词`() {
        val l = line("ab" to 0..1, "cd" to 3..4)
        assertEquals("ab cd", joinSelected(listOf(l), lineStartsOf(listOf(l)), setOf(0, 1)))
    }

    @Test
    fun `joinSelected 按原文顺序输出，与点击先后无关`() {        val lines = listOf(line("ab" to 0..1), line("cd" to 0..1))
        val starts = lineStartsOf(lines)
        val forward = joinSelected(lines, starts, setOf(0, 1))
        val reversedClickOrder = joinSelected(lines, starts, setOf(1, 0))
        assertEquals(forward, reversedClickOrder)
        assertTrue(forward.indexOf("ab") < forward.indexOf("cd"))
    }

    @Test
    fun `hitChipNear 缝隙里取最近 chip，超出半径返回 null`() {
        val bounds = mapOf(
            0 to Rect(0f, 0f, 10f, 10f),
            1 to Rect(20f, 0f, 30f, 10f),
        )
        // 缝隙正中：两侧等距，取先登记的那个即可，但不能是 null。
        assertEquals(0, hitChipNear(bounds, Offset(14f, 5f)))
        assertEquals(1, hitChipNear(bounds, Offset(18f, 5f)))
        // 距离超出上限（默认 72px）才放弃。
        assertEquals(null, hitChipNear(bounds, Offset(200f, 5f)))
        assertEquals(null, hitChipNear(emptyMap(), Offset(5f, 5f)))
    }

    @Test
    fun `hitChip 只在 chip 命中区内返回，缝隙返回 null`() {
        val bounds = mapOf(
            0 to Rect(0f, 0f, 10f, 10f),
            1 to Rect(20f, 0f, 30f, 10f),
        )
        assertEquals(0, hitChip(bounds, Offset(5f, 5f)))
        assertEquals(1, hitChip(bounds, Offset(25f, 5f)))
        // chip 之间的缝隙：不命中的话调用方保留上一次命中，不会跳回空选。
        assertEquals(null, hitChip(bounds, Offset(15f, 5f)))
        assertEquals(null, hitChip(emptyMap(), Offset(5f, 5f)))
    }
}
