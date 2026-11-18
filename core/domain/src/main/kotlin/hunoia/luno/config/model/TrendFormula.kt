package hunoia.luno.config.model

/**
 * 一条趋势记录**内部**的读数计算（如「晚重 − 晨重」）。
 *
 * 只作用于同一条记录的几个读数之间；跨记录的变化由 [TrendStats] 的「较上次 / 较 7 天前」给出，
 * 两者不混用。公式是纯函数，不做任何 IO，可在 JVM 单测里直接验。
 *
 * 语法：数字、`#1`..`#n`（第 n 个读数）、`+ - * /`、括号与空白；`#n` 从 1 开始编号。
 * 用递归下降解析，不用 `eval` / 反射 / `ScriptEngine`。
 */
sealed interface TrendFormulaResult {
    /** 算出结果。 */
    data class Ok(val value: Double) : TrendFormulaResult

    /**
     * 公式合法，但这条记录算不出：引用的读数槽位在这条记录里不存在，或除零。
     * UI 显示占位态，不报错。
     */
    data object NotApplicable : TrendFormulaResult

    /** 公式本身写错（语法或占位符编号越界），编辑页不允许保存。 */
    data class Invalid(val issue: TrendFormulaIssue) : TrendFormulaResult
}

/** 公式非法的两种原因；文案由 UI 侧给，这里只传原因。 */
enum class TrendFormulaIssue { SYNTAX, BAD_PLACEHOLDER }

/**
 * 代入 [readings] 求值 [formula]。
 *
 * [slotCount] 是指标声明的读数槽位数（通常 [TrendMetric.valueCount]）：占位符编号超过它属于
 * **公式写错**（[TrendFormulaIssue.BAD_PLACEHOLDER]），而不是「这条记录缺数据」。
 * 公式为空 = 不做计算，返回 [TrendFormulaResult.NotApplicable]。
 */
fun evaluateTrendFormula(
    readings: List<Double>,
    formula: String,
    slotCount: Int = readings.size,
): TrendFormulaResult {
    val text = formula.trim()
    if (text.isEmpty()) return TrendFormulaResult.NotApplicable
    return try {
        val value = TrendFormulaParser(text, readings, slotCount).run()
        if (value.isFinite()) TrendFormulaResult.Ok(value) else TrendFormulaResult.NotApplicable
    } catch (e: TrendFormulaSyntax) {
        TrendFormulaResult.Invalid(TrendFormulaIssue.SYNTAX)
    } catch (e: TrendFormulaBadPlaceholder) {
        TrendFormulaResult.Invalid(TrendFormulaIssue.BAD_PLACEHOLDER)
    } catch (e: TrendFormulaNotApplicable) {
        TrendFormulaResult.NotApplicable
    }
}

/** 只校验语法与占位符编号（不代入数据），供指标编辑页保存前即时反馈；合法返回 null。 */
fun checkTrendFormula(formula: String, slotCount: Int): TrendFormulaIssue? {
    val text = formula.trim()
    if (text.isEmpty()) return null
    return try {
        TrendFormulaParser(text, emptyList(), slotCount, validateOnly = true).run()
        null
    } catch (e: TrendFormulaSyntax) {
        TrendFormulaIssue.SYNTAX
    } catch (e: TrendFormulaBadPlaceholder) {
        TrendFormulaIssue.BAD_PLACEHOLDER
    } catch (e: TrendFormulaNotApplicable) {
        null
    }
}

private class TrendFormulaSyntax : Exception()
private class TrendFormulaBadPlaceholder : Exception()
private class TrendFormulaNotApplicable : Exception()

/** 递归下降解析器：expression → term → factor → primary。 */
private class TrendFormulaParser(
    private val src: String,
    private val readings: List<Double>,
    private val slotCount: Int,
    /** 只校验语法 / 占位符编号（[checkTrendFormula]）时没有真实数据可代入。 */
    private val validateOnly: Boolean = false,
) {
    private var pos = 0

    fun run(): Double {
        val value = parseExpression()
        skipSpace()
        if (pos < src.length) throw TrendFormulaSyntax()
        return value
    }

    private fun parseExpression(): Double {
        var left = parseTerm()
        while (true) {
            val c = peek()
            when (c) {
                '+' -> { pos++; left = left + parseTerm() }
                '-' -> { pos++; left = left - parseTerm() }
                else -> return left
            }
        }
    }

    private fun parseTerm(): Double {
        var left = parseFactor()
        while (true) {
            val c = peek()
            when (c) {
                '*' -> { pos++; left = left * parseFactor() }
                '/' -> {
                    pos++
                    val right = parseFactor()
                    if (right == 0.0) throw TrendFormulaNotApplicable()
                    left = left / right
                }

                else -> return left
            }
        }
    }

    private fun parseFactor(): Double {
        val c = peek()
        return when (c) {
            '-' -> { pos++; -parseFactor() }
            '+' -> { pos++; parseFactor() }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Double {
        val c = peek() ?: throw TrendFormulaSyntax()
        return when (c) {
            '(' -> {
                pos++
                val inner = parseExpression()
                if (peek() != ')') throw TrendFormulaSyntax()
                pos++
                inner
            }

            '#' -> placeholder()
            else -> if (c.isDigit()) number() else throw TrendFormulaSyntax()
        }
    }

    private fun placeholder(): Double {
        pos++
        val start = pos
        while (pos < src.length && src[pos].isDigit()) pos++
        val digits = src.substring(start, pos)
        if (digits.isEmpty()) throw TrendFormulaSyntax()
        val index = digits.toIntOrNull() ?: throw TrendFormulaSyntax()
        if (index < 1 || index > slotCount) throw TrendFormulaBadPlaceholder()
        if (!validateOnly && index > readings.size) throw TrendFormulaNotApplicable()
        // 校验模式没有数据可代入，返回 0 占位：只关心语法能不能走通。
        return if (validateOnly) 0.0 else readings[index - 1]
    }

    private fun number(): Double {
        val start = pos
        while (pos < src.length && src[pos].isDigit()) pos++
        if (pos < src.length && src[pos] == '.') {
            pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        return src.substring(start, pos).toDoubleOrNull() ?: throw TrendFormulaSyntax()
    }

    /** 吃掉空白并返回当前字符（已到末尾返回 null）。 */
    private fun peek(): Char? {
        skipSpace()
        return src.getOrNull(pos)
    }

    private fun skipSpace() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }
}