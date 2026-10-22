package hunoia.luno.action.template

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 参数模板扫描器语义回归（原 scripts/ParamTemplatesCheck.java 的 JVM 单测迁移版）：
 * 直接调用生产代码 ParamTemplates.resolve / tokensIn，不再维护 Java 转写副本。
 * 背景：扫描器手写而非用正则（Android java.util.regex 底层 ICU 不接受 `\{` 转义），
 * 手写逻辑没有编译器兜底，本测试锁边界行为。
 */
class ParamTemplatesTest {

    private val v = mapOf(
        "pkg" to "com.x",
        "pkg.label" to "设置",
        "battery" to "76",
        "a" to "1",
        "b" to "2",
        "empty" to "",
    )

    private fun r(text: String, values: Map<String, String> = v) = ParamTemplates.resolve(text, values)

    @Test
    fun `plain text passes through`() {
        assertEquals("", r("", v))
        assertEquals("settings put", r("settings put", v))
    }

    @Test
    fun `known tokens are replaced`() {
        assertEquals("com.x", r("\${pkg}"))
        assertEquals("pre-76-post", r("pre-\${battery}-post"))
        assertEquals("12", r("\${a}\${b}")) // 相邻 token
        assertEquals("xcom.xy76z", r("x\${pkg}y\${battery}z"))
        assertEquals("设置", r("\${pkg.label}")) // 带点 token
        assertEquals("com.xsuffix", r("\${pkg}suffix"))
    }

    @Test
    fun `unknown or malformed tokens are preserved verbatim`() {
        assertEquals("\${trigger.app}", r("\${trigger.app}")) // 未知 token
        assertEquals("\$pkg", r("\$pkg")) // 无花括号
        assertEquals("\${pkg", r("\${pkg")) // 未闭合
        assertEquals("\${1abc}", r("\${1abc}")) // 非法起始字符
        assertEquals("\${}", r("\${}")) // 空 token 名
        assertEquals("com.x\${unknown}", r("\${pkg}\${unknown}")) // 已知+未知混合
        assertEquals("abc\$", r("abc\$")) // 结尾单个美元
        assertEquals("\$com.x", r("\$\${pkg}")) // 双美元
        assertEquals("\${com.x}", r("\${\${pkg}}")) // 嵌套病态输入：内层替换外层原样，不崩溃不递归
        assertEquals("am start \${HOME}", r("am start \${HOME}")) // shell 变量不受影响
        assertEquals("\${pkg}", r("\${pkg}", emptyMap())) // 空 map 原样
    }

    @Test
    fun `empty value replaces to empty string`() {
        assertEquals("a--b", r("a-\${empty}-b"))
    }

    @Test
    fun `replacement value is not re-expanded`() {
        assertEquals("[\${a}]", r("[\${pkg}]", mapOf("pkg" to "\${a}")))
    }

    @Test
    fun `tokensIn collects known and unknown names`() {
        assertEquals(setOf("pkg", "battery", "nope"), ParamTemplates.tokensIn("\${pkg} \${battery} \${nope}"))
    }
}
