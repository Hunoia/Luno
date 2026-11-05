package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门面注入门禁（architecture §8.4）。
 *
 * 页面与状态持有者只读 `LocalFacades`，不得直接引用 `:app` 的组装根 `AppGraph`；
 * 例外只有 Activity 这类入口（它们负责把实现注入 CompositionLocal）。
 */
class FacadeInjectionTest {

    private val uiRoot = File("src/main/java/hunoia/luno/ui")

    /** 允许引用 AppGraph 的入口文件（注入点与生命周期回调）。 */
    private val entryPoints = setOf(
        "MainActivity.kt",
        "ActionDeepLinkActivity.kt",
    )

    @Test
    fun `ui 不直接引用组装根`() {
        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in entryPoints }
            .filter { file ->
                val code = file.readLines()
                    .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                    .joinToString("\n")
                "AppGraph" in code
            }
            .map { it.relativeTo(uiRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "以下 UI 文件仍在直连组装根 AppGraph；请改用 LocalFacades.current（architecture §8.4）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `门面注入点唯一且存在`() {
        val providers = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "LocalFacades provides" in it.readText() }
            .map { it.name }
            .toSet()

        assertTrue(
            "LocalFacades 必须恰好有一个注入点（当前：$providers）",
            providers == setOf("MainActivity.kt"),
        )
    }
}
