package hunoia.luno.capability.ocr

/**
 * PP-OCRv6 模型文件名与 rec 档位解析（唯一事实源）。
 *
 * 模型随 APK 打包（见 [BundledOcrModel]），本对象只剩命名规则与文件名解析；
 * 不再有目录扫描 / manifest / SHA-256 / `.ready` 等导入态。
 */
object OcrModelStore {

    const val DET_MODEL = "ppocrv6_small_det_640_fp32.tflite"
    const val DICT = "characters.json"

    /**
     * rec 模型文件名前缀；文件名自带输入宽度档位：
     * `ppocrv6_small_rec_<宽度>_fp32.tflite`（320 / 640 / 960）。
     */
    const val REC_PREFIX = "ppocrv6_small_rec_"
    val REC_NAME = Regex("""^${Regex.escape(REC_PREFIX)}\d+_fp32\.tflite$""")

    fun isRecFile(name: String): Boolean = REC_NAME.matches(name)

    /** 从 rec 文件名解析输入宽度；不是合法 rec 名时返回 null。 */
    fun recWidth(name: String): Int? =
        if (!isRecFile(name)) null
        else name.removePrefix(REC_PREFIX).substringBefore('_').toIntOrNull()

    /** rec 文件名（按输入宽度）。 */
    fun recFileName(width: Int): String = "${REC_PREFIX}${width}_fp32.tflite"
}
