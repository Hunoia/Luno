package hunoia.luno.capability.ocr

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * AOT 模型目录 / 清单校验：只接受 `kind=aot`、`soc=SM8750` 且逐文件 SHA-256 正确的包。
 */
class OcrModelStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun staging(
        kind: String = "aot",
        soc: String = "SM8750",
        corruptSha: String? = null,
    ): File {
        val dir = temp.newFolder()
        OcrModelStore.requiredFiles.forEach { name ->
            val file = File(dir, name)
            if (name.endsWith(".txt")) {
                file.writeText("第\n一\n")
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
                .put("soc", soc)
                .put("runtime", "litert-npu-qualcomm")
                .put("qairt", "2.51.0")
                .put("files", files)
                .toString(),
        )
        return dir
    }

    @Test
    fun acceptsValidAotPack() {
        val manifest = OcrModelStore.validateStaging(staging())
        assertEquals("aot", manifest.kind)
        assertEquals("SM8750", manifest.soc)
        assertEquals(OcrModelStore.requiredFiles.size, manifest.files.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonAotPack() {
        OcrModelStore.validateStaging(staging(kind = "jit"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherSoc() {
        OcrModelStore.validateStaging(staging(soc = "SM8650"))
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
