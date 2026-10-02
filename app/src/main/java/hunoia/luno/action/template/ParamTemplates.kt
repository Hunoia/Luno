package hunoia.luno.action.template

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 动作参数模板：执行前把文本参数里的 `${token}` 换成运行时值。
 * 存储始终保留原文，替换只发生在派发链路上。
 * 未识别的 token 原样保留，避免破坏 shell.custom 这类自带 `${VAR}` 的命令。
 *
 * 语法 `${` + `[A-Za-z_][A-Za-z0-9_.]*` + `}`，手写单趟扫描而不用正则：
 * Android 的 java.util.regex 底层是 ICU，不接受 `\{` 这类转义写法。
 */
object ParamTemplates {

    fun tokensIn(params: JsonObject): Set<String> {
        val used = LinkedHashSet<String>()
        collectTokens(params, used)
        return used
    }

    fun resolve(text: String, values: Map<String, String>): String {
        if (values.isEmpty() || '$' !in text) return text
        var builder: StringBuilder? = null
        var copied = 0
        var i = 0
        while (i < text.length) {
            if (text[i] != '$') {
                i++
                continue
            }
            val hit = tokenAt(text, i)
            if (hit == null) {
                i++
                continue
            }
            val name = hit.first
            val range = hit.second
            if (values.containsKey(name)) {
                val sb = builder ?: StringBuilder(text.length + 16).also { builder = it }
                sb.append(text, copied, range.first).append(values[name].orEmpty())
                copied = range.last + 1
            }
            i = range.last + 1
        }
        val out = builder ?: return text
        out.append(text, copied, text.length)
        return out.toString()
    }

    fun resolveParams(params: JsonObject, values: Map<String, String>): JsonObject =
        JsonObject(params.mapValues { (_, element) -> resolveElement(element, values) })

    private fun collectTokens(element: JsonElement, out: MutableSet<String>) {
        when (element) {
            is JsonPrimitive -> if (element.isString) collectTokens(element.content, out)
            is JsonArray -> element.forEach { collectTokens(it, out) }
            is JsonObject -> element.values.forEach { collectTokens(it, out) }
            else -> Unit
        }
    }

    private fun collectTokens(text: String, out: MutableSet<String>) {
        if ('$' !in text) return
        var i = 0
        while (i < text.length) {
            if (text[i] != '$') {
                i++
                continue
            }
            val hit = tokenAt(text, i)
            if (hit == null) {
                i++
                continue
            }
            out += hit.first
            i = hit.second.last + 1
        }
    }

    private fun resolveElement(element: JsonElement, values: Map<String, String>): JsonElement =
        when {
            element is JsonNull -> element
            element is JsonPrimitive && element.isString -> JsonPrimitive(resolve(element.content, values))
            element is JsonArray -> JsonArray(element.map { resolveElement(it, values) })
            element is JsonObject -> JsonObject(element.mapValues { (_, v) -> resolveElement(v, values) })
            else -> element
        }

    /** 解析 `${name}`，返回 token 名与它在 text 中的区间；不是合法 token 时返回 null。 */
    private fun tokenAt(text: String, dollar: Int): Pair<String, IntRange>? {
        if (text.getOrNull(dollar + 1) != '{') return null
        val nameStart = dollar + 2
        if (nameStart >= text.length || !isNameStart(text[nameStart])) return null
        var end = nameStart + 1
        while (end < text.length && isNamePart(text[end])) end++
        if (end >= text.length || text[end] != '}') return null
        return text.substring(nameStart, end) to (dollar..end)
    }

    private fun isNameStart(c: Char): Boolean = c == '_' || c in 'a'..'z' || c in 'A'..'Z'

    private fun isNamePart(c: Char): Boolean = isNameStart(c) || c == '.' || c in '0'..'9'
}
