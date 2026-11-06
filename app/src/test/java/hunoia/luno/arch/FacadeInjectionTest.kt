package hunoia.luno.arch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门面注入门禁（architecture §8.4）。
 *
 * 页面与状态持有者只读 `LocalFacades`，不得直接引用 `:app` 的组装根 `AppGraph`；
 * 例外只有 Activity 这类入口（它们负责把实现注入 CompositionLocal）。
 */
class FacadeInjectionTest {

    private val uiRoot = "app/src/main/java/hunoia/luno/ui/"

    /** 允许引用 AppGraph 的入口文件（注入点与生命周期回调）。 */
    private val entryPoints = setOf("MainActivity", "ActionDeepLinkActivity")

    private val appGraphRef = Regex("""\bAppGraph\b""")

    private val uiFiles get() = productionFiles.filter { it.repoPath().startsWith(uiRoot) }

    @Test
    fun `ui 不直接引用组装根`() {
        val offenders = uiFiles
            .filterNot { it.name in entryPoints }
            .filter { appGraphRef.containsMatchIn(it.codeText()) }
            .map { it.repoPath() }

        assertTrue(
            "以下 UI 文件仍在直连组装根 AppGraph；请改用 LocalFacades.current（architecture §8.4）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `门面注入点唯一且存在`() {
        val providers = productionFiles
            .filter { "LocalFacades provides" in it.codeText() }
            .map { it.name }
            .toSet()

        assertEquals(
            "LocalFacades 必须恰好有一个注入点（当前：$providers）",
            setOf("MainActivity"),
            providers,
        )
    }
}
