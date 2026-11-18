package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一条记录内部读数计算的求值器；纯函数，逐条覆盖语法分支。 */
class TrendFormulaTest {

    private val readings = listOf(70.0, 68.5, 3.0)

    private fun eval(formula: String, slotCount: Int = readings.size): TrendFormulaResult =
        evaluateTrendFormula(readings, formula, slotCount)

    private fun ok(formula: String, expected: Double, slotCount: Int = readings.size) {
        val result = evaluateTrendFormula(readings, formula, slotCount)
        val value = result as? TrendFormulaResult.Ok
        assertEquals("formula `$formula`", expected, value?.value!!, 0.0001)
    }

    // ---------- 四则与优先级 ----------

    @Test
    fun `addition and subtraction`() {
        ok("#2-#1", -1.5)
        ok("#1-#2", 1.5)
        ok("#1+#2", 138.5)
    }

    @Test
    fun `multiplication and division bind tighter`() {
        ok("#1*2+1", 141.0)
        ok("(#1+1)*2", 142.0)
        ok("#1-#3*2", 64.0)
    }

    @Test
    fun `nested parentheses and whitespace`() {
        ok("((#1))", 70.0)
        ok("  #2 * 2 + #3 ", 140.0)
    }

    @Test
    fun `literals and decimals`() {
        ok("0.5+0.25", 0.75)
        ok("#1-69.5", 0.5)
    }

    @Test
    fun `unary minus and plus`() {
        ok("-#1", -70.0)
        ok("+#1", 70.0)
        ok("-(#1-#2)", -1.5)
    }

    // ---------- 占位符 ----------

    @Test
    fun `placeholder numbering starts at one`() {
        ok("#1", 70.0)
        ok("#3", 3.0)
        ok("10", 10.0)
    }

    @Test
    fun `placeholder beyond the slot count is a bad formula`() {
        val result = evaluateTrendFormula(readings, "#4-#1", slotCount = 3)
        assertEquals(
            TrendFormulaResult.Invalid(TrendFormulaIssue.BAD_PLACEHOLDER),
            result,
        )
        val zero = evaluateTrendFormula(readings, "#0", slotCount = 3)
        assertEquals(TrendFormulaResult.Invalid(TrendFormulaIssue.BAD_PLACEHOLDER), zero)
    }

    @Test
    fun `placeholder missing in this entry is not applicable`() {
        // 指标声明了 3 个槽位，但这条记录只填了 2 个前缀读数。
        val result = evaluateTrendFormula(listOf(70.0, 68.5), "#3-#1", slotCount = 3)
        assertEquals(TrendFormulaResult.NotApplicable, result)
    }

    // ---------- 算不出与写错 ----------

    @Test
    fun `division by zero is not applicable`() {
        assertEquals(TrendFormulaResult.NotApplicable, eval("#1/0"))
        assertEquals(
            TrendFormulaResult.NotApplicable,
            evaluateTrendFormula(listOf(70.0, 0.0), "#1/#2", slotCount = 2),
        )
    }

    @Test
    fun `empty formula is not applicable`() {
        assertEquals(TrendFormulaResult.NotApplicable, evaluateTrendFormula(readings, "   ", 3))
        assertEquals(TrendFormulaResult.NotApplicable, evaluateTrendFormula(readings, "", 3))
    }

    @Test
    fun `malformed formulas are syntax errors`() {
        val badFormulas = listOf(
            "#1+",
            "*2",
            "#1(#2)",
            "1..2",
            "2#",
            "#1#2",
            "#a",
            "#1-",
            "#",
        )
        badFormulas.forEach { formula ->
            val result = evaluateTrendFormula(readings, formula, 3)
            assertEquals(
                "expected SYNTAX for `$formula`",
                TrendFormulaResult.Invalid(TrendFormulaIssue.SYNTAX),
                result,
            )
        }
    }

    @Test
    fun `non finite results collapse to not applicable`() {
        // #1/#2 里 #2 = 68.5，正常算得出来；换一条 #2 = 0 的记录就走不可算。
        ok("#1/#2", 70.0 / 68.5)
        val huge = evaluateTrendFormula(listOf(1e308, 1e-300), "#1/#2", slotCount = 2)
        assertEquals(TrendFormulaResult.NotApplicable, huge)
    }

    // ---------- 保存前校验 ----------

    @Test
    fun `check formula validates without data`() {
        assertNull(checkTrendFormula("", 2))
        assertNull(checkTrendFormula("#2-#1", 2))
        assertNull(checkTrendFormula("(#1+#2)*3", 2))
        assertEquals(TrendFormulaIssue.SYNTAX, checkTrendFormula("#1+", 2))
        assertEquals(TrendFormulaIssue.SYNTAX, checkTrendFormula("abc", 2))
        assertEquals(TrendFormulaIssue.BAD_PLACEHOLDER, checkTrendFormula("#3", 2))
        assertNull(checkTrendFormula("#1/0", 2))
    }

    @Test
    fun `formula result is deterministic`() {
        val a = eval("#1*#2/#2")
        val b = eval("#1*#2/#2")
        assertTrue(a == b)
        assertEquals(readings[0], (a as TrendFormulaResult.Ok).value, 0.0001)
    }
}