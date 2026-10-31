package hunoia.luno.capability.ocr

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** PP-OCRv6 的 GPU 模型清单；只接受 GPU 包（原始 fp32 tflite），不再接受 AOT / JIT 包。 */
data class AotModelManifest(
    val kind: String,
    val soc: String?,
    val runtime: String?,
    val qairt: String?,
    val aiEdgeLitert: String?,
    val files: Map<String, String>,
)

/** 已导入模型的可展示信息（由 manifest + 实际目录大小推导）。 */
data class OcrModelInfo(
    val manifest: AotModelManifest,
    val sizeBytes: Long,
    val fileCount: Int,
    val importedAt: Long,
    /** rec 模型文件名（含输入宽度档位，如 …_rec_640_…）；可同时装多档，运行时按行宽自动选。 */
    val recFileNames: List<String> = emptyList(),
) {
    /** 从文件名解析的 rec 输入宽度（320 / 640 / 960），升序；解析不出为空。 */
    val recWidths: List<Int>
        get() = recFileNames.mapNotNull(OcrModelStore::recWidth).distinct().sorted()
}

sealed interface OcrModelStatus {
    data object Missing : OcrModelStatus
    data class Ready(val info: OcrModelInfo) : OcrModelStatus
    data class Invalid(val reason: String) : OcrModelStatus
}

/**
 * PP-OCRv6 GPU 模型目录。
 *
 * 模型只由用户导入；这里不发起网络请求。`.ready` 只在完整校验通过后写入，
 * `isReady()` 用它做快速判断，避免每次识别都对 30MB 文件重算哈希。
 */
object OcrModelStore {

    const val DIR_NAME = "ppocrv6_gpu"
    const val MANIFEST = "manifest.json"
    const val READY = ".ready"

    const val DET_MODEL = "ppocrv6_small_det_640_fp32.tflite"
    const val DICT = "characters.json"

    /**
     * rec 模型允许 320 / 640 / 960 三档输入宽度，文件名自带档位：
     * `ppocrv6_small_rec_<宽度>_fp32.tflite`。同一个包/目录里可以同时装多档：
     * 运行时按每行宽度选“刚好放得下”的那一档，正常行用 320、宽行自动升到 640/960，
     * 避免宽行无谓切段（见 `RecModelChoice`）。模型输入尺寸仍以运行时读出为准。
     */
    const val REC_PREFIX = "ppocrv6_small_rec_"
    val REC_NAME = Regex("""^${Regex.escape(REC_PREFIX)}\d+_fp32\.tflite$""")

    fun isRecFile(name: String): Boolean = REC_NAME.matches(name)

    /** 从 rec 文件名解析输入宽度；不是合法 rec 名时返回 null。 */
    fun recWidth(name: String): Int? =
        if (!isRecFile(name)) null
        else name.removePrefix(REC_PREFIX).substringBefore('_').toIntOrNull()

    /** 目录里的全部 rec 模型，按输入宽度升序；没有时为空。 */
    fun recFiles(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && isRecFile(it.name) }
            ?.sortedBy { recWidth(it.name) ?: Int.MAX_VALUE }
            .orEmpty()

    /** 一个完整 GPU 包应有的文件（按目录里实际存在的 rec 文件名）。 */
    fun requiredFiles(dir: File): List<String> {
        val recs = recFiles(dir).map { it.name }
        return listOf(DET_MODEL) + recs + DICT
    }

    fun baseDir(context: Context): File = File(context.filesDir, "ocr_models")

    fun modelDir(context: Context): File = File(baseDir(context), DIR_NAME)

    fun isReady(context: Context): Boolean {
        val dir = modelDir(context)
        if (!File(dir, READY).isFile) return false
        if (!File(dir, MANIFEST).isFile) return false
        // rec 缺失时 isReady 直接 false；多档共存是正常状态。
        if (recFiles(dir).isEmpty()) return false
        return requiredFiles(dir).all { name -> File(dir, name).let { f -> f.isFile && f.length() > 0 } }
    }

    fun status(context: Context): OcrModelStatus {
        val dir = modelDir(context)
        if (!dir.isDirectory) return OcrModelStatus.Missing
        if (!isReady(context)) return OcrModelStatus.Invalid("模型文件不完整或缺少 .ready")
        return runCatching {
            OcrModelStatus.Ready(readInfo(dir))
        }.getOrElse {
            OcrModelStatus.Invalid(it.message ?: "模型清单无效")
        }
    }

    fun readInfo(dir: File): OcrModelInfo {
        val manifest = parseManifest(File(dir, MANIFEST))
        val recs = recFiles(dir)
        val files = requiredFiles(dir).map { File(dir, it) }
        return OcrModelInfo(
            manifest = manifest,
            sizeBytes = files.sumOf { it.length() },
            fileCount = files.size,
            importedAt = File(dir, READY).lastModified(),
            recFileNames = recs.map { it.name },
        )
    }

    fun parseManifest(file: File): AotModelManifest {
        val json = JSONObject(file.readText())
        val kind = json.optString("kind").trim()
        val soc = json.optString("soc").trim()
        require(kind == "gpu") { "不是 GPU 模型包（kind=$kind）" }
        val filesJson = json.optJSONObject("files") ?: error("manifest 缺少 files")
        val files = buildMap {
            filesJson.keys().forEach { name -> put(name, filesJson.optString(name).trim()) }
        }
        return AotModelManifest(
            kind = kind,
            soc = soc.ifBlank { null },
            runtime = json.optString("runtime").ifBlank { null },
            qairt = json.optString("qairt").ifBlank { null },
            aiEdgeLitert = json.optString("aiEdgeLitert").ifBlank { null },
            files = files,
        )
    }

    /** 校验 staging 目录：清单、白名单文件、逐文件 SHA-256、字典非空。 */
    fun validateStaging(dir: File): AotModelManifest {
        val manifestFile = File(dir, MANIFEST)
        require(manifestFile.isFile) { "缺少 manifest.json" }
        val manifest = parseManifest(manifestFile)
        val recs = recFiles(dir)
        require(recs.isNotEmpty()) {
            "缺少 rec 模型（${REC_PREFIX}<宽度>_fp32.tflite，可同时放 320/640/960）"
        }
        for (name in requiredFiles(dir)) {
            val file = File(dir, name)
            require(file.isFile && file.length() > 0) { "缺少或为空的模型文件：$name" }
            val expected = manifest.files[name]
            require(!expected.isNullOrBlank()) { "manifest 未声明 $name 的 SHA-256" }
            val actual = sha256(file)
            require(actual.equals(expected, ignoreCase = true)) {
                "$name SHA-256 不匹配（期望 $expected，实际 $actual）"
            }
        }
        require(File(dir, DICT).let { f -> f.isFile && JSONArray(f.readText()).length() > 0 }) {
            "characters.json 为空或不是 JSON 数组"
        }
        return manifest
    }

    /** 原子提交：旧目录先挪走，staging 改名为正式目录，再删旧目录。 */
    fun commit(context: Context, stagingDir: File) {
        val target = modelDir(context)
        val parent = target.parentFile ?: error("模型目录无效")
        parent.mkdirs()
        val backup = File(parent, "$DIR_NAME.old-${System.currentTimeMillis()}")
        if (target.exists() && !target.renameTo(backup)) {
            error("无法替换旧模型目录")
        }
        if (!stagingDir.renameTo(target)) {
            // 回滚，避免用户导入失败后连旧模型也没了。
            if (backup.exists()) backup.renameTo(target)
            error("无法提交模型目录")
        }
        File(target, READY).writeText(
            buildString {
                appendLine("imported=${System.currentTimeMillis()}")
                appendLine("kind=gpu")
            },
        )
        backup.deleteRecursively()
    }

    fun delete(context: Context): Boolean = modelDir(context).deleteRecursively()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
