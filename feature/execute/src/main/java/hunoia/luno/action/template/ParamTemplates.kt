package hunoia.luno.action.template

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 动作参数模板：执行前把文本参数里的 `${token}` 换成运行时值，存储保留原文。
 * 未识别的 token 原样保留（shell.custom 自带 `${VAR}`）；手写扫描不用正则——
 * Android 的 java.util.regex 底层是 ICU，不接受 `\{` 这类转义。
 * 表达式 `${lhs OP rhs}` 白名单：OP 为 `==` `!=` `>=` `<=` `>` `<`，操作数为
 * 变量/数字/单引号字符串；两边都是数字走数值比较，否则只支持 `==`/`!=`，失败原样保留。
 */
object ParamTemplates {

    fun tokensIn(params: JsonObject): Set<String> {
        val used = LinkedHashSet<String>()
        collectTokens(params, used)
        return used
    }

    fun tokensIn(text: String): Set<String> {
        val used = LinkedHashSet<String>()
        collectTokens(text, used)
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
                val expr = expressionAt(text, i)
                if (expr == null) {
                    i++
                    continue
                }
                val replacement = evaluate(expr.first, values)
                if (replacement == null) {
                    i++
                    continue
                }
                val range = expr.second
                val sb = builder ?: StringBuilder(text.length + 16).also { builder = it }
                sb.append(text, copied, range.first).append(replacement)
                copied = range.last + 1
                i = range.last + 1
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
                val expr = expressionAt(text, i)
                if (expr == null) {
                    i++
                    continue
                }
                collectExpressionTokens(expr.first, out)
                i = expr.second.last + 1
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

    /** 表达式扫描：`${` 后找第一个 `}`（不嵌套），body 交给 [parseExpression]；失败返回 null，不吞输入。 */
    private fun expressionAt(text: String, dollar: Int): Pair<Expr, IntRange>? {
        if (text.getOrNull(dollar + 1) != '{') return null
        var end = dollar + 2
        while (end < text.length && text[end] != '}') end++
        if (end >= text.length) return null
        val body = text.substring(dollar + 2, end)
        val expr = parseExpression(body) ?: return null
        return expr to (dollar..end)
    }

    private fun collectExpressionTokens(expr: Expr, out: MutableSet<String>) {
        (expr.left as? Operand.Name)?.let { out += it.name }
        (expr.right as? Operand.Name)?.let { out += it.name }
    }

    /** 求值失败（未知变量、语法非法）返回 null，调用方原样保留原文。 */
    private fun evaluate(expr: Expr, values: Map<String, String>): String? {
        val left = expr.left.resolve(values) ?: return null
        val right = expr.right?.resolve(values) ?: return left
        val op = expr.op ?: return left
        val leftNum = left.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()
        return if (leftNum != null && rightNum != null) {
            when (op) {
                "==" -> (leftNum == rightNum)
                "!=" -> (leftNum != rightNum)
                ">" -> (leftNum > rightNum)
                "<" -> (leftNum < rightNum)
                ">=" -> (leftNum >= rightNum)
                "<=" -> (leftNum <= rightNum)
                else -> return null
            }.toString()
        } else {
            when (op) {
                "==" -> (left == right)
                "!=" -> (left != right)
                else -> return null
            }.toString()
        }
    }

    private sealed interface Operand {
        data class Name(val name: String) : Operand
        data class Literal(val text: String) : Operand

        fun resolve(values: Map<String, String>): String? = when (this) {
            is Name -> values[name]
            is Literal -> text
        }
    }

    private data class Expr(val left: Operand, val op: String?, val right: Operand?)

    private fun parseExpression(body: String): Expr? {
        val parser = ExprParser(body)
        return parser.parse()
    }

    /** 单比较表达式手写解析：operand (op operand)?，空白容忍，无正则。 */
    private class ExprParser(private val text: String) {
        private var pos = 0

        fun parse(): Expr? {
            skipSpaces()
            val left = parseOperand() ?: return null
            skipSpaces()
            if (pos >= text.length) return Expr(left, null, null)
            val op = parseOp() ?: return null
            skipSpaces()
            val right = parseOperand() ?: return null
            skipSpaces()
            if (pos != text.length) return null
            return Expr(left, op, right)
        }

        private fun parseOperand(): Operand? {
            if (pos >= text.length) return null
            val c = text[pos]
            if (c == '\'') {
                val end = text.indexOf('\'', pos + 1)
                if (end < 0) return null
                val literal = text.substring(pos + 1, end)
                pos = end + 1
                return Operand.Literal(literal)
            }
            if (isNameStart(c)) {
                val start = pos++
                while (pos < text.length && isNamePart(text[pos])) pos++
                return Operand.Name(text.substring(start, pos))
            }
            if (c == '-' || c.isDigit()) {
                val start = pos++
                var dot = false
                while (pos < text.length) {
                    val d = text[pos]
                    if (d.isDigit()) {
                        pos++
                    } else if (d == '.' && !dot) {
                        dot = true
                        pos++
                    } else {
                        break
                    }
                }
                val num = text.substring(start, pos)
                if (num == "-" || num == "." || num == "-.") return null
                return Operand.Literal(num)
            }
            return null
        }

        private fun parseOp(): String? {
            for (op in arrayOf("==", "!=", ">=", "<=", ">", "<")) {
                if (text.startsWith(op, pos)) {
                    pos += op.length
                    return op
                }
            }
            return null
        }

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
