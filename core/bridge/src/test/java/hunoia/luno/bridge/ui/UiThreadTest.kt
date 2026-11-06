package hunoia.luno.bridge.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UiThreadTest {

    @Test
    fun `非主线程通过断言`() {
        requireNotMainThread(isMain = false, tag = "test")
    }

    @Test
    fun `主线程断言失败并带上定位标记`() {
        try {
            requireNotMainThread(isMain = true, tag = "test.blocking")
            fail("主线程执行阻塞工作必须失败")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("test.blocking"))
        }
    }
}
