package hunoia.luno.capability.ocr

import android.icu.text.BreakIterator
import android.icu.util.ULocale

/**
 * 系统 ICU 断词实现：中文 / 日文走词典分词，韩文按词边界，拉丁走通用规则。
 *
 * 语言按文本实际字符判断，而不是用引擎请求的脚本标签：v6 统一模型下，
 * 日文/韩文也可能被标成中文，按标签选 locale 会拿错词典。
 */
object IcuWordBreaker : WordBreaker {

    override fun boundaries(text: String): List<Int> {
        if (text.isEmpty()) return listOf(0)
        val iterator = BreakIterator.getWordInstance(localeFor(text))
        iterator.setText(text)
        val result = ArrayList<Int>()
        var index = iterator.first()
        while (index != BreakIterator.DONE) {
            result += index
            index = iterator.next()
        }
        if (result.lastOrNull() != text.length) result += text.length
        return result
    }

    private fun localeFor(text: String): ULocale = when {
        text.any { it in '\u3040'..'\u30FF' } -> ULocale.JAPANESE
        text.any { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' } -> ULocale.KOREAN
        text.any { it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' } -> ULocale.SIMPLIFIED_CHINESE
        else -> ULocale.ROOT
    }
}
