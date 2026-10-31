package hunoia.luno.capability.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTokenizerTest {

    private class FakeBreaker(private val split: (String) -> List<Int>) : WordBreaker {
        override fun boundaries(text: String): List<Int> = split(text)
    }

    /** 每两个字切一刀，模拟中文双字词；空格也当边界。 */
    private val pairBreaker = FakeBreaker { text ->
        val points = ArrayList<Int>()
        points += 0
        var i = 0
        while (i < text.length) {
            if (text[i] == ' ') {
                points += i
                points += i + 1
                i++
            } else {
                i += 2
                points += i.coerceAtMost(text.length)
            }
        }
        points += text.length
        points
    }

    @Test
    fun `splits lines and keeps line-local offsets`() {
        val lines = OcrTokenizer.lines("今天天气\n很好", pairBreaker)
        assertEquals(2, lines.size)
        assertEquals("今天天气", lines[0].text)
        assertEquals(listOf("今天", "天气"), lines[0].words.map { it.text })
        assertEquals(0, lines[0].words[0].start)
        assertEquals(2, lines[0].words[0].end)
        assertEquals(2, lines[0].words[1].start)
        assertEquals(4, lines[0].words[1].end)
        assertEquals("很好", lines[1].text)
    }

    @Test
    fun `skips whitespace and punctuation-only tokens`() {
        val breaker = FakeBreaker { listOf(0, 1, 2, 3, 4) }
        val line = OcrTokenizer.lines("， 中", breaker).single()
        assertEquals(listOf("中"), line.words.map { it.text })
        assertEquals(2, line.words.single().start)
    }

    @Test
    fun `keeps url and email as one word`() {
        val breaker = FakeBreaker { text ->
            // 故意在每个字符处都切一刀，验证保护区间会把内部边界删掉。
            (0..text.length).toList()
        }
        val text = "看 https://a.com/x?q=1 和 a@b.com"
        val line = OcrTokenizer.lines(text, breaker).single()
        assertTrue(line.words.any { it.text == "https://a.com/x?q=1" })
        assertTrue(line.words.any { it.text == "a@b.com" })
    }

    @Test
    fun `falls back to whole line when only punctuation`() {
        val breaker = FakeBreaker { listOf(0, 2, 4) }
        val line = OcrTokenizer.lines("。。。", breaker).single()
        assertEquals("。。。", line.words.single().text)
    }

    @Test
    fun `empty text has one empty line`() {
        val lines = OcrTokenizer.lines("", pairBreaker)
        assertEquals(1, lines.size)
        assertTrue(lines.single().words.isEmpty())
    }
}
