package hunoia.luno.runtime.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneUrlMatchTest {

    @Test
    fun `wechat title bar host is recognized`() {
        assertEquals("www.xiaohongshu.com", SceneUrlMatch.hostOfLeaf("www.xiaohongshu.com"))
        assertEquals("example.com", SceneUrlMatch.hostOfLeaf("example.com/path/to?q=1"))
        assertEquals("example.com", SceneUrlMatch.hostOfLeaf("https://example.com/a/b"))
    }

    @Test
    fun `body text mentioning a domain is not an address bar`() {
        assertNull(SceneUrlMatch.hostOfLeaf("详见 github.com/hunoia"))
        assertNull(SceneUrlMatch.hostOfLeaf("小红书"))
        assertNull(SceneUrlMatch.hostOfLeaf(""))
        assertNull(SceneUrlMatch.hostOfLeaf("9.49.2"))
        assertNull(SceneUrlMatch.hostOfLeaf("v1.2.3"))
        assertNull(SceneUrlMatch.hostOfLeaf("user@example.com"))
    }

    @Test
    fun `same site compares by label boundary`() {
        assertTrue(SceneUrlMatch.sameSite("www.xiaohongshu.com", "www.xiaohongshu.com"))
        assertTrue(SceneUrlMatch.sameSite("xiaohongshu.com", "www.xiaohongshu.com"))
        assertTrue(SceneUrlMatch.sameSite("WWW.XiaoHongShu.com", "xiaohongshu.com"))
        assertTrue(SceneUrlMatch.sameSite("bilibili.com", "m.bilibili.com"))
    }

    @Test
    fun `prefix disguise and empty sides do not match`() {
        assertFalse(SceneUrlMatch.sameSite("bilibili.com.evil.com", "bilibili.com"))
        assertFalse(SceneUrlMatch.sameSite("notbilibili.com", "bilibili.com"))
        assertFalse(SceneUrlMatch.sameSite("", "bilibili.com"))
        assertFalse(SceneUrlMatch.sameSite(null, "bilibili.com"))
    }
}
