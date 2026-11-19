package hunoia.luno.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「用浏览器打开」的选路：优先显式指定，其次系统默认，最后候选第一个；始终排除 Luno 自己。
 *
 * 这条逻辑不碰 PackageManager，所以可以纯 JVM 钉死——它决定「浏览器打开」到底是真打开浏览器，
 * 还是退化回选择应用页。
 */
class BrowserChoiceTest {

    private val self = "hunoia.luno"

    @Test
    fun `preferred browser wins when still installed`() {
        assertEquals(
            "com.miui.browser",
            pickBrowserPackage(
                candidates = listOf("com.android.chrome", "com.miui.browser"),
                preferred = "com.miui.browser",
                defaultPackage = "com.android.chrome",
                selfPackage = self,
            ),
        )
    }

    @Test
    fun `uninstalled preferred falls back to the system default`() {
        assertEquals(
            "com.android.chrome",
            pickBrowserPackage(
                candidates = listOf("com.android.chrome", "com.miui.browser"),
                preferred = "com.gone.browser",
                defaultPackage = "com.android.chrome",
                selfPackage = self,
            ),
        )
    }

    @Test
    fun `luno itself is never picked even when it is the default browser`() {
        assertEquals(
            "com.android.chrome",
            pickBrowserPackage(
                candidates = listOf(self, "com.android.chrome"),
                preferred = self,
                defaultPackage = self,
                selfPackage = self,
            ),
        )
    }

    @Test
    fun `no candidate means fall back to the app picker`() {
        assertNull(pickBrowserPackage(listOf(self), preferred = "", defaultPackage = self, selfPackage = self))
        assertNull(pickBrowserPackage(emptyList(), preferred = "", defaultPackage = null, selfPackage = self))
    }
}
