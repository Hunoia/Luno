package hunoia.luno.capability.ocr

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** PP-OCRv5 的 AOT 模型清单；只接受 host AOT 包，不再接受原始 JIT 包。 */
data class AotModelManifest(
    val kind: String,
    val soc: String,
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
)

sealed interface OcrModelStatus {
    data object Missing : OcrModelStatus
    data class Ready(val info: OcrModelInfo) : OcrModelStatus
    data class Invalid(val reason: String) : OcrModelStatus
}

/**
 * PP-OCRv5 AOT 模型目录。
 *
 * 模型只由用户导入；这里不发起网络请求。`.ready` 只在完整校验通过后写入，
 * `isReady()` 用它做快速判断，避免每次识别都对 30MB 文件重算哈希。
 */
object OcrModelStore {

    const val DIR_NAME = "ppocrv5_sm8750_aot"
    const val MANIFEST = "manifest.json"
    const val READY = ".ready"

    const val DET_MODEL = "pp_ocrv5_det_mobile.tflite"
    const val ZH_REC_MODEL = "pp_ocrv5_zh_rec.tflite"
    const val KO_REC_MODEL = "pp_ocrv5_ko_rec.tflite"
    const val ZH_DICT = "zh_dict.txt"
    const val KO_DICT = "ko_dict.txt"

    val requiredFiles = listOf(
        DET_MODEL,
        ZH_REC_MODEL,
        KO_REC_MODEL,
        ZH_DICT,
        KO_DICT,
    )

    fun baseDir(context: Context): File = File(context.filesDir, "ocr_models")

    fun modelDir(context: Context): File = File(baseDir(context), DIR_NAME)

    fun isReady(context: Context): Boolean {
        val dir = modelDir(context)
        if (!File(dir, READY).isFile) return false
        if (!File(dir, MANIFEST).isFile) return false
        return requiredFiles.all { File(dir, it).let { f -> f.isFile && f.length() > 0 } }
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
        val files = requiredFiles.map { File(dir, it) }
        return OcrModelInfo(
            manifest = manifest,
            sizeBytes = files.sumOf { it.length() },
            fileCount = files.size,
            importedAt = File(dir, READY).lastModified(),
        )
    }

    fun parseManifest(file: File): AotModelManifest {
        val json = JSONObject(file.readText())
        val kind = json.optString("kind").trim()
        val soc = json.optString("soc").trim()
        require(kind == "aot") { "不是 AOT 模型包（kind=$kind）" }
        require(soc.equals("SM8750", ignoreCase = true)) { "模型包不是 SM8750 编译的（soc=$soc）" }
        val filesJson = json.optJSONObject("files") ?: error("manifest 缺少 files")
        val files = buildMap {
            filesJson.keys().forEach { name -> put(name, filesJson.optString(name).trim()) }
        }
        return AotModelManifest(
            kind = kind,
            soc = soc,
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
        for (name in requiredFiles) {
            val file = File(dir, name)
            require(file.isFile && file.length() > 0) { "缺少或为空的模型文件：$name" }
            val expected = manifest.files[name]
            require(!expected.isNullOrBlank()) { "manifest 未声明 $name 的 SHA-256" }
            val actual = sha256(file)
            require(actual.equals(expected, ignoreCase = true)) {
                "$name SHA-256 不匹配（期望 $expected，实际 $actual）"
            }
        }
        require(File(dir, ZH_DICT).readLines().isNotEmpty()) { "zh_dict.txt 为空" }
        require(File(dir, KO_DICT).readLines().isNotEmpty()) { "ko_dict.txt 为空" }
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
                appendLine("soc=${parseManifest(File(target, MANIFEST)).soc}")
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
