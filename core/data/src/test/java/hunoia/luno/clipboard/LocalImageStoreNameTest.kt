package hunoia.luno.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片副本文件名的内容哈希口径：同一张图（落盘字节全等）必须映射到同一文件名，
 * 否则 `ClipboardHistory.record` 的图片去重键（"i:文件名"）永远不命中，同一张图
 * 复制两次会出现两条记录（见 `clipboardImageName`）。
 */
class LocalImageStoreNameTest {

    @Test
    fun `same bytes produce same name`() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(clipboardImageName(a), clipboardImageName(b))
    }

    @Test
    fun `different bytes produce different name`() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 6)
        assertNotEquals(clipboardImageName(a), clipboardImageName(b))
    }

    @Test
    fun `name is jpg hex of sha256`() {
        val name = clipboardImageName(byteArrayOf(1, 2, 3))
        assertTrue(name.endsWith(".jpg"))
        // 64 位十六进制（SHA-256）+ ".jpg"
        assertEquals(64 + 4, name.length)
        assertTrue(name.dropLast(4).all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `known vector is stable`() {
        // SHA-256("abc") 的十六进制，固定向量：保证哈希实现不被无意改动。
        val digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(digest + ".jpg", clipboardImageName("abc".toByteArray()))
    }
}
