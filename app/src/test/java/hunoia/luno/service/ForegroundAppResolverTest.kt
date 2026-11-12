package hunoia.luno.service

import hunoia.luno.service.ForegroundAppResolver.AppWindow
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「当前应用」严格解析：最上层可见 TYPE_APPLICATION，桌面/无窗口视为无目标。 */
class ForegroundAppResolverTest {

    private val launcher = setOf("com.android.launcher")

    private fun resolve(vararg windows: AppWindow): String =
        ForegroundAppResolver.resolve(windows.toList()) { it in launcher }

    @Test
    fun `top application window wins`() {
        assertEquals(
            "com.wechat",
            resolve(
                AppWindow("com.android.launcher", layer = 0),
                AppWindow("com.wechat", layer = 2),
            ),
        )
    }

    @Test
    fun `luno activity in foreground is a valid target`() {
        assertEquals(
            "hunoia.luno",
            resolve(
                AppWindow("com.wechat", layer = 1),
                AppWindow("hunoia.luno", layer = 5),
            ),
        )
    }

    @Test
    fun `panel over another app resolves the app below`() {
        // 面板本身是 overlay 类型，不会进入 appWindows；这里只模拟下层应用窗口。
        assertEquals(
            "com.wechat",
            resolve(
                AppWindow("com.android.launcher", layer = 0),
                AppWindow("com.wechat", layer = 3),
            ),
        )
    }

    @Test
    fun `panel over luno own activity resolves luno`() {
        assertEquals("hunoia.luno", resolve(AppWindow("hunoia.luno", layer = 4)))
    }

    @Test
    fun `launcher is not a target`() {
        assertEquals("", resolve(AppWindow("com.android.launcher", layer = 1)))
    }

    @Test
    fun `no application window is not a target`() {
        assertEquals("", resolve())
    }

    @Test
    fun `windows without a root package are skipped`() {
        assertEquals(
            "com.wechat",
            resolve(
                AppWindow("com.wechat", layer = 1),
                AppWindow(null, layer = 9),
            ),
        )
    }
}
