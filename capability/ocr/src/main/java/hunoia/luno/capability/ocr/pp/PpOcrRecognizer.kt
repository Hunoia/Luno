package hunoia.luno.capability.ocr.pp

import android.content.Context
import java.io.Closeable
import java.io.File

/**
 * PP-OCRv5 文本识别（PPLCNetV3 + SVTR + CTC）on LiteRT（移植自 google-ai-edge/litert-samples）。
 *
 * line[1,3,48,320] -> logits[1,T,C]（CTC）。PP-OCRv5 按语言拆分识别模型：中文模型覆盖
 * 拉丁 / 西里尔 / CJK，韩文模型覆盖谚文；[dictFile] 必须与模型输出 class 数一致。
 *
 * 模型来自用户导入的 host AOT 包，优先 NPU、失败降级 CPU。
 */
class PpOcrRecognizer(
    private val context: Context,
    modelFile: File,
    dictFile: File,
) : Closeable {

    companion object {
        const val H = 48
        const val W = 320

        /** CTC 贪心解码：去重复字符、去 blank（index 0）。 */
        internal fun decode(logits: FloatArray, chars: Array<String>): String {
            val numClasses = chars.size
            val t = logits.size / numClasses
            val sb = StringBuilder()
            var prev = -1
            for (step in 0 until t) {
                var best = 0
                var bestV = logits[step * numClasses]
                for (c in 1 until numClasses) {
                    val v = logits[step * numClasses + c]
                    if (v > bestV) {
                        bestV = v
                        best = c
                    }
                }
                if (best != prev && best != 0) {
                    sb.append(chars[best]) // CTC 折叠：去重复 + 去 blank
                }
                prev = best
            }
            return sb.toString()
        }
    }

    private val rec = PpOcrModelRunner.fromFile(context, modelFile)

    /** CTC 布局：0=blank，其后 dict，末尾 space。 */
    private val chars: Array<String>

    init {
        val dict = dictFile.readLines().map { it.trimEnd('\r') }
        chars = (listOf("") + dict + listOf(" ")).toTypedArray()
        require(chars.size > 2) { "字典为空：${dictFile.name}" }
    }

    /** crop：H*W*3 行主序 [0,255]（已 resize+pad 的文本行）。返回识别文本。 */
    fun recognize(crop: FloatArray): String {
        val chw = FloatArray(3 * H * W)
        val hw = H * W
        for (i in 0 until hw) { // (img/255 - 0.5)/0.5
            chw[i] = (crop[i * 3] / 255f - 0.5f) / 0.5f
            chw[hw + i] = (crop[i * 3 + 1] / 255f - 0.5f) / 0.5f
            chw[2 * hw + i] = (crop[i * 3 + 2] / 255f - 0.5f) / 0.5f
        }
        rec.writeInput(0, chw)
        rec.run()
        val logits = rec.readOutput(0)
        // 字典与模型 class 数错配（如把 lat 字典配到 zh 模型）会静默解出乱码，
        // 这里直接失败，由 DelegatingOcrEngine 降级 ML Kit。
        require(logits.isNotEmpty() && logits.size % chars.size == 0) {
            "识别输出与字典不匹配：logits=${logits.size} chars=${chars.size}"
        }
        val steps = logits.size / chars.size
        require(steps in 1..200) { "识别输出步数异常：$steps" }
        return decode(logits, chars)
    }

    override fun close() {
        rec.close()
    }
}
