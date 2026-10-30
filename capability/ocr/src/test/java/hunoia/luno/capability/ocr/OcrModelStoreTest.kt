package hunoia.luno.capability.ocr

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * GPU 模型目录 / 清单校验：只接受 `kind=gpu` 且逐文件 SHA-256 正确的包。
 */
class OcrModelStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun staging(
        kind: String = "gpu",
        corruptSha: String? = null,
    ): File {
        val dir = temp.newFolder()
        OcrModelStore.requiredFiles.forEach { name ->
            val file = File(dir, name)
            if (name.endsWith(".json")) {
                file.writeText("[\"blank\",\"A\"]")
            } else {
                file.writeBytes(ByteArray(32) { it.toByte() })
            }
        }
        val files = JSONObject()
        OcrModelStore.requiredFiles.forEach { name ->
            val sha = OcrModelStore.sha256(File(dir, name))
            files.put(name, if (name == corruptSha) "0".repeat(64) else sha)
        }
        File(dir, OcrModelStore.MANIFEST).writeText(
            JSONObject()
                .put("kind", kind)
                .put("runtime", "litert-gpu-opencl")
                .put("files", files)
                .toString(),
        )
        return dir
    }

    @Test
    fun acceptsValidGpuPack() {
        val manifest = OcrModelStore.validateStaging(staging())
        assertEquals("gpu", manifest.kind)
        assertEquals(OcrModelStore.requiredFiles.size, manifest.files.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonGpuPack() {
        OcrModelStore.validateStaging(staging(kind = "aot"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyDict() {
        val dir = staging()
        File(dir, OcrModelStore.DICT).writeText("[]")
        OcrModelStore.validateStaging(dir)
    }

    @Test
    fun rejectsCorruptedFile() {
        val failure = runCatching {
            OcrModelStore.validateStaging(staging(corruptSha = OcrModelStore.DET_MODEL))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        val message = (failure as? IllegalArgumentException)?.message.orEmpty()
        assertTrue(message.contains(OcrModelStore.DET_MODEL))
    }
}
