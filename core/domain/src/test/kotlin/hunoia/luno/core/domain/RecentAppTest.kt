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

    @Test
    fun `from block key round-trips both variants`() {
        assertEquals(RecentApp("com.tencent.mm", 0), RecentApp.fromBlockKey("com.tencent.mm"))
        assertEquals(RecentApp("com.tencent.mm", 999), RecentApp.fromBlockKey("com.tencent.mm#999"))
    }

    @Test
    fun `malformed key falls back to the main user`() {
        assertEquals(RecentApp("com.tencent.mm#"), RecentApp.fromBlockKey("com.tencent.mm#"))
        assertEquals(RecentApp("com.tencent.mm#abc"), RecentApp.fromBlockKey("com.tencent.mm#abc"))
        assertEquals(RecentApp("#999"), RecentApp.fromBlockKey("#999"))
    }
}
