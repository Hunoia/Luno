package hunoia.luno.capability.ocr

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** rec 差分补丁：还原字节要精确，越界 / 文件名不匹配必须在写盘前拒绝。 */
class RecModelPatchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val baseName = "ppocrv6_small_rec_640_fp32.tflite"
    private val targetName = "ppocrv6_small_rec_320_fp32.tflite"

    private fun patch(
        dir: File,
        changes: List<Pair<Int, Int>>,
        target: String = targetName,
        size: Long = 64,
    ): File {
        val json = JSONObject()
            .put("version", 1)
            .put("base", baseName)
            .put("target", target)
            .put("size", size)
            .put("changes", JSONArray(changes.map { listOf(it.first, it.second) }))
        return File(dir, "$targetName.patch.json").apply { writeText(json.toString()) }
    }

    @Test
    fun `applyAll 还原目标并删除补丁`() {
        val dir = temp.newFolder()
        val base = ByteArray(64) { it.toByte() }
        File(dir, baseName).writeBytes(base)
        val expected = base.copyOf().also {
            it[3] = 0x7f
            it[10] = 0
        }
        val patchFile = patch(dir, listOf(3 to 0x7f, 10 to 0))
        RecModelPatch.applyAll(dir)

        assertArrayEquals(expected, File(dir, targetName).readBytes())
        assertArrayEquals(base, File(dir, baseName).readBytes())
        assertFalse("补丁应在还原后删除", patchFile.exists())
    }

    @Test
    fun `offset 越界时拒绝且不留目标文件`() {
        val dir = temp.newFolder()
        File(dir, baseName).writeBytes(ByteArray(64))
        patch(dir, listOf(64 to 1), size = 64)
        val failure = runCatching { RecModelPatch.applyAll(dir) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(File(dir, targetName).exists())
    }

    @Test
    fun `补丁文件名与 target 不一致时拒绝`() {
        val dir = temp.newFolder()
        File(dir, baseName).writeBytes(ByteArray(64))
        patch(dir, listOf(1 to 1), target = baseName, size = 64)
        val failure = runCatching { RecModelPatch.applyAll(dir) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(File(dir, targetName).exists())
    }

    @Test
    fun `非补丁文件名不会被识别`() {
        assertTrue(RecModelPatch.isPatch("$targetName.patch.json"))
        assertFalse(RecModelPatch.isPatch("$targetName.json"))
        assertFalse(RecModelPatch.isPatch("ppocrv6_small_rec_320_fp32.patch.json"))
    }
}
