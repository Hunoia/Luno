package hunoia.luno.capability.ocr

/**
 * 把 OCR 文本切成词，供结果页做“长按选词 / 按词扩选 / 分词视图”。
 *
 * 只处理纯文本，不依赖 Android UI；[WordBreaker] 负责真正的断词规则，
 * 默认实现见 [IcuWordBreaker]（系统 ICU 词典，中文 / 日文 / 韩文 / 拉丁）。
 *
 * 分词只影响选择与命中；复制、搜索、分享始终使用原文或用户选中的词，
 * 不会改写 OCR 结果。
 */
data class OcrWord(
    val text: String,
    /** 在所属行文本中的起始下标（含）。 */
    val start: Int,
    /** 在所属行文本中的结束下标（不含）。 */
    val end: Int,
)

/** 一行 OCR 文本及其词列表。 */
data class OcrLine(
    val text: String,
    val words: List<OcrWord>,
)

/** 可替换的断词规则：返回升序、包含 0 与文本长度的边界下标。 */
interface WordBreaker {
    fun boundaries(text: String): List<Int>
}

object OcrTokenizer {

    private data class Span(val start: Int, val end: Int)

    /** URL / 邮箱 / 电话：这些区间内不按 ICU 边界拆开。 */
    private val protectedPatterns = listOf(
        Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+"""),
        Regex("""(?i)\b[\w.+-]+@[\w.-]+\.\w{2,}\b"""),
        Regex("""\+?\d[\d\s()\-]{5,}\d"""),
    )

    private const val ProtectedTrailing = ".,;:!?，。；：！？、)]}》」』”’"

    /**
     * 按行分词。换行符不进入 [OcrLine.text]；每行词的下标都相对该行。
     */
    fun lines(text: String, breaker: WordBreaker = IcuWordBreaker): List<OcrLine> {
        if (text.isEmpty()) return listOf(OcrLine("", emptyList()))
        val result = ArrayList<OcrLine>()
        var start = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val line = text.substring(start, end).trimEnd('\r')
            result += line(line, breaker)
            if (newline < 0) break
            start = newline + 1
        }
        return result
    }

    private fun line(text: String, breaker: WordBreaker): OcrLine {
        if (text.isBlank()) return OcrLine(text, emptyList())

        // TreeSet 去重 + 排序；保护区间内部的边界先删掉，再把区间首尾补回来。
        val points = java.util.TreeSet<Int>()
        points += 0
        points += text.length
        for (boundary in breaker.boundaries(text)) {
            if (boundary in 0..text.length) points += boundary
        }
        for (span in protectedSpans(text)) {
            val inside = points.filter { it > span.start && it < span.end }
            points.removeAll(inside.toSet())
            points += span.start
            points += span.end
        }

        val boundaries = points.toList()
        val words = ArrayList<OcrWord>()
        for (i in 0 until boundaries.lastIndex) {
            var wordStart = boundaries[i]
            var wordEnd = boundaries[i + 1]
            while (wordStart < wordEnd && text[wordStart].isWhitespace()) wordStart++
            while (wordEnd > wordStart && text[wordEnd - 1].isWhitespace()) wordEnd--
            if (wordStart >= wordEnd) continue
            val token = text.substring(wordStart, wordEnd)
            if (token.none { it.isWordChar() }) continue
            words += OcrWord(token, wordStart, wordEnd)
        }

        // 整行都是标点或 emoji 时，至少保留整行可选中，避免结果页没有任何可选内容。
        if (words.isEmpty()) {
            val first = text.indexOfFirst { !it.isWhitespace() }
            val last = text.indexOfLast { !it.isWhitespace() }
            if (first in 0..last) {
                words += OcrWord(text.substring(first, last + 1), first, last + 1)
            }
        }
        return OcrLine(text, words)
    }

    private fun protectedSpans(text: String): List<Span> {
        val spans = ArrayList<Span>()
        for (pattern in protectedPatterns) {
            for (match in pattern.findAll(text)) {
                var start = match.range.first
                var end = match.range.last + 1
                while (end > start && text[end - 1] in ProtectedTrailing) end--
                while (start < end && text[start].isWhitespace()) start++
                if (end - start >= 2) spans += Span(start, end)
            }
        }
        if (spans.isEmpty()) return emptyList()

        // 合并重叠区间，避免 URL 里的数字又被电话正则重复保护。
        spans.sortBy { it.start }
        val merged = ArrayList<Span>()
        for (span in spans) {
            val last = merged.lastOrNull()
            if (last != null && span.start <= last.end) {
                merged[merged.lastIndex] = Span(last.start, maxOf(last.end, span.end))
            } else {
                merged += span
            }
        }
        return merged
    }

    private fun Char.isWordChar(): Boolean =
        isLetterOrDigit() ||
            this in '\u4E00'..'\u9FFF' ||
            this in '\u3400'..'\u4DBF' ||
            this in '\u3040'..'\u30FF' ||
            this in '\uAC00'..'\uD7AF' ||
            code in 0x2600..0x27BF ||
            code in 0x1F000..0x1FAFF ||
            isHighSurrogate() ||
            isLowSurrogate()
}
