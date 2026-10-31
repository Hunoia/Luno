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
 * rec 模型允许 320 / 640 / 960 多档共存，运行时按行宽自动选。
 */
class OcrModelStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val rec320 = "ppocrv6_small_rec_320_fp32.tflite"

    private fun rec(width: Int) = "ppocrv6_small_rec_${width}_fp32.tflite"

    private fun staging(
        kind: String = "gpu",
        corruptSha: String? = null,
        recs: List<String> = listOf(rec320),
    ): File {
        val dir = temp.newFolder()
        val names = listOf(OcrModelStore.DET_MODEL, *recs.toTypedArray(), OcrModelStore.DICT)
        names.forEach { name ->
            val file = File(dir, name)
            if (name.endsWith(".json")) {
                file.writeText("[\"blank\",\"A\"]")
            } else {
                file.writeBytes(ByteArray(32) { it.toByte() })
            }
        }
        val files = JSONObject()
        names.forEach { name ->
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
        val dir = staging()
        val manifest = OcrModelStore.validateStaging(dir)
        assertEquals("gpu", manifest.kind)
        assertEquals(3, manifest.files.size)
        assertEquals(listOf(OcrModelStore.DET_MODEL, rec320, OcrModelStore.DICT), OcrModelStore.requiredFiles(dir))
    }

    @Test
    fun acceptsRecWidthVariants() {
        for (width in listOf(640, 960)) {
            val name = rec(width)
            val dir = staging(recs = listOf(name))
            OcrModelStore.validateStaging(dir)
            assertEquals(listOf(name), OcrModelStore.recFiles(dir).map { it.name })
            val info = OcrModelStore.readInfo(dir)
            assertEquals(listOf(width), info.recWidths)
        }
    }

    @Test
    fun acceptsMultipleRecWidthsInOnePack() {
        val dir = staging(recs = listOf(rec(640), rec(320), rec(960)))
        OcrModelStore.validateStaging(dir)
        assertEquals(listOf(rec(320), rec(640), rec(960)), OcrModelStore.recFiles(dir).map { it.name })
        val info = OcrModelStore.readInfo(dir)
        assertEquals(listOf(320, 640, 960), info.recWidths)
        assertEquals(
            listOf(OcrModelStore.DET_MODEL, rec(320), rec(640), rec(960), OcrModelStore.DICT),
            OcrModelStore.requiredFiles(dir),
        )
    }

    @Test
    fun rejectsPackWithoutRec() {
        val failure = runCatching { OcrModelStore.validateStaging(staging(recs = emptyList())) }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
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
