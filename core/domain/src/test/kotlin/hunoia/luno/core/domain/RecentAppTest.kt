package hunoia.luno.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 最近应用条目：黑名单键在主用户与分身之间必须可区分，且兼容旧的裸包名配置。 */
class RecentAppTest {

    @Test
    fun `main user key stays a bare package name`() {
        assertEquals("com.tencent.mm", RecentApp("com.tencent.mm").blockKey)
        assertEquals("com.tencent.mm", RecentApp.blockKey("com.tencent.mm", 0))
    }

    @Test
    fun `clone key appends the user id`() {
        assertEquals("com.tencent.mm#999", RecentApp("com.tencent.mm", 999).blockKey)
        assertEquals("com.tencent.mm#999", RecentApp.blockKey("com.tencent.mm", 999))
    }
}
