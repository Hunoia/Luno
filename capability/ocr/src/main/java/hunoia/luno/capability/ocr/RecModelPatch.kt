package hunoia.luno.capability.ocr

import java.io.File
import java.io.RandomAccessFile
import org.json.JSONObject

/**
 * rec 模型差分补丁：320 / 640 两档是同一组权重，只差输入宽度相关的少量形状元数据
 * （实测 21MB 文件仅 256 字节不同）。模型包里只带一份完整 rec + 一个
 * `ppocrv6_small_rec_<宽度>_fp32.tflite.patch.json`，导入时还原另一档，
 * 避免在 zip 里重复塞 21MB。
 *
 * 补丁只做字节替换，不改文件长度；还原后的文件仍要过 [OcrModelStore.validateStaging]
 * 的逐文件 SHA-256 校验，补丁损坏 / offset 越界都会让整个导入失败，不会留下半成品。
 */
object RecModelPatch {

    const val SUFFIX = ".patch.json"

    private const val VERSION = 1

    /** 补丁条目上限：正常只有几百字节，设上限防恶意包把导入拖死。 */
    private const val MAX_CHANGES = 65_536

    /** 文件名是否是 rec 补丁（target 必须是合法 rec 名）。 */
    fun isPatch(name: String): Boolean =
        name.endsWith(SUFFIX) && OcrModelStore.isRecFile(targetOf(name))

    /** 补丁对应的目标 rec 文件名。 */
    fun targetOf(patchName: String): String = patchName.removeSuffix(SUFFIX)

    /** 还原 [dir] 下所有补丁并删除补丁文件；任何一个失败都抛异常，由导入层整体回滚。 */
    fun applyAll(dir: File) {
        dir.listFiles()
            ?.filter { it.isFile && isPatch(it.name) }
            ?.sortedBy { it.name }
            ?.forEach { patch ->
                apply(dir, patch)
                patch.delete()
            }
    }

    /** 从 base 复制出 target，再按补丁逐字节改写。 */
    fun apply(dir: File, patchFile: File) {
        require(isPatch(patchFile.name)) { "不是 rec 补丁：${patchFile.name}" }
        val json = JSONObject(patchFile.readText())
        require(json.optInt("version") == VERSION) { "补丁版本不支持：${json.optInt("version")}" }
        val baseName = json.optString("base")
        val targetName = json.optString("target")
        require(OcrModelStore.isRecFile(baseName)) { "补丁 base 非法：$baseName" }
        require(OcrModelStore.isRecFile(targetName)) { "补丁 target 非法：$targetName" }
        require(targetName == targetOf(patchFile.name)) { "补丁文件名与 target 不一致：$targetName" }

        val base = File(dir, baseName)
        require(base.isFile && base.length() > 0) { "补丁缺少 base：$baseName" }
        require(json.optLong("size") == base.length()) { "补丁 size 与 base 不一致" }
        val changes = json.optJSONArray("changes") ?: error("补丁缺少 changes")
        require(changes.length() in 0..MAX_CHANGES) { "补丁 changes 数量非法：${changes.length()}" }

        val target = File(dir, targetName)
        require(!target.exists()) { "补丁 target 已存在：$targetName" }
        base.copyTo(target, overwrite = false)
        try {
            RandomAccessFile(target, "rw").use { raf ->
                var last = -1L
                for (i in 0 until changes.length()) {
                    val pair = changes.optJSONArray(i) ?: error("补丁 changes[$i] 非法")
                    require(pair.length() == 2) { "补丁 changes[$i] 非法" }
                    val offset = pair.getLong(0)
                    val value = pair.getInt(1)
                    require(offset in 0L until base.length()) { "补丁 offset 越界：$offset" }
                    require(offset > last) { "补丁 offset 必须严格递增：$offset" }
                    require(value in 0..255) { "补丁 value 越界：$value" }
                    raf.seek(offset)
                    raf.write(value)
                    last = offset
                }
                raf.setLength(base.length())
            }
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }
}
