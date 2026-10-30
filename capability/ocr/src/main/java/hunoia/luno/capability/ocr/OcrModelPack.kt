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

    private val allowed = (OcrModelStore.requiredFiles + OcrModelStore.MANIFEST).toSet()
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
                                if (name !in allowed) {
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
