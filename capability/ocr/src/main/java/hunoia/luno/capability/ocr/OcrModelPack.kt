package hunoia.luno.capability.ocr

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用户导入的 GPU 模型包。
 *
 * 包内可以是扁平结构，也可以带一层目录；只接受白名单文件，按 basename 落盘，
 * 拒绝 `..` / 绝对路径 / 重复文件，避免 Zip Slip 与覆盖。
 *
 * 白名单与校验规则见 [OcrModelStore]（`kind="gpu"`，逐文件 SHA-256）。
 */
object OcrModelPack {

    /** 白名单：det / 字典 / 清单固定名，rec 按 `ppocrv6_small_rec_<宽度>_fp32.tflite` 匹配（可多档），另允许 rec 差分补丁。 */
    private fun allowed(name: String): Boolean =
        name == OcrModelStore.DET_MODEL ||
            name == OcrModelStore.DICT ||
            name == OcrModelStore.MANIFEST ||
            OcrModelStore.isRecFile(name) ||
            RecModelPatch.isPatch(name)

    private const val MAX_ENTRY_BYTES = 200L * 1024 * 1024

    suspend fun importZip(context: Context, uri: Uri): Result<OcrModelInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val staging = File(
                    OcrModelStore.baseDir(context),
                    ".staging-${System.currentTimeMillis()}",
                ).apply { mkdirs() }
                try {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("无法读取所选文件")
                    input.use { raw ->
                        ZipInputStream(raw.buffered()).use { zip ->
                            var total = 0L
                            val seen = HashSet<String>()
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                if (entry.isDirectory) {
                                    zip.closeEntry()
                                    continue
                                }
                                val normalized = entry.name.replace('\\', '/')
                                require(!normalized.startsWith("/")) { "ZIP 含绝对路径：${entry.name}" }
                                require(normalized.split('/').none { it == ".." }) {
                                    "ZIP 含非法路径：${entry.name}"
                                }
                                val name = normalized.substringAfterLast('/')
                                if (!allowed(name)) {
                                    zip.closeEntry()
                                    continue
                                }
                                require(seen.add(name)) { "ZIP 含重复文件：$name" }
                                val target = File(staging, name)
                                target.outputStream().use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    var entryBytes = 0L
                                    while (true) {
                                        val n = zip.read(buffer)
                                        if (n <= 0) break
                                        entryBytes += n
                                        total += n
                                        require(total <= MAX_ENTRY_BYTES) { "ZIP 解压后过大" }
                                        out.write(buffer, 0, n)
                                    }
                                    require(entryBytes > 0) { "ZIP 内文件为空：$name" }
                                }
                                zip.closeEntry()
                            }
                        }
                    }
                    // 320/640 只差少量形状字节：包里可能只有一份完整 rec + 补丁，
                    // 先在本机还原出缺失档位，再按 manifest 做完整 SHA 校验。
                    RecModelPatch.applyAll(staging)
                    OcrModelStore.validateStaging(staging)
                    OcrModelStore.commit(context, staging)
                    OcrModelStore.readInfo(OcrModelStore.modelDir(context))
                } catch (failure: Throwable) {
                    staging.deleteRecursively()
                    throw failure
                }
            }
        }
}
