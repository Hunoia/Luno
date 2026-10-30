package hunoia.luno.capability.ocr.pp

import android.content.Context
import java.io.Closeable
import java.io.File
import kotlin.math.ceil

/**
 * PP-OCRv6 文本识别（PP-HGNetV2/SVTR + CTC）on LiteRT GPU delegate。
 *
 * line[1,3,48,320] -> logits[1,T,C]（CTC）。v6 用统一的 50 语言字符表（characters.json，
 * 含 blank 在 index 0），单模型覆盖中文 / 日文 / 韩文 / 拉丁等；[dictFile] 为 JSON 字符串
 * 数组，长度必须与模型输出 class 数一致。
 */
/**
 * 单个解码出的字符：[text] 为字典字符，[step] 为 CTC 时间步，[x] 是映射回输入宽度的
 * 字符中心（x ≈ (step+0.5) × W / T；切段拼接去重按 [x] 判断位置）。
 */
data class CtcChar(val step: Int, val text: String, val x: Float)

/**
 * 一个切段：源图 x 范围 `[x0, x1)`，[overlapSrc] 是与上一段的重叠宽度（源图像素；
 * 首段为 0）。拼接时上一段已覆盖这段的开头 [overlapSrc] 像素，识别后要把落在这个
 * 前缀里的字符去掉，否则重叠区里的字会出现两次（多字）。
 */
internal data class LineSegment(val x0: Int, val x1: Int, val overlapSrc: Int)

class PpOcrRecognizer(
    private val context: Context,
    modelFile: File,
    dictFile: File,
) : Closeable {

    companion object {
        const val H = 48
        const val W = 320

        /**
         * CTC 贪心解码（带时间步）：去重复字符、去 blank（index 0）。
         *
         * [step] 线性映射回输入宽度：x ≈ (step + 0.5) × W / T，切段拼接时靠它
         * 判断字符落在段内什么位置（见 [PpOcrEngine] 的重叠区去重）。
         */
        internal fun decodeSteps(logits: FloatArray, chars: Array<String>): List<CtcChar> {
            val numClasses = chars.size
            val t = logits.size / numClasses
            val out = ArrayList<CtcChar>()
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
                    val x = (step + 0.5f) * W / t
                    out.add(CtcChar(step, chars[best], x)) // CTC 折叠：去重复 + 去 blank
                }
                prev = best
            }
            return out
        }

        /** CTC 贪心解码：去重复字符、去 blank（index 0）。 */
        internal fun decode(logits: FloatArray, chars: Array<String>): String =
            decodeSteps(logits, chars).joinToString("") { it.text }

        /**
         * 超宽行的切段方案：模型输入宽度固定 [W]，宽高比超过 W/H 的行若整体压扁到
         * [W] 宽会成批错字，必须按原图切段、每段独立识别后拼接。
         *
         * 返回源图 x 上的切段（升序、覆盖 `[0, width)`）：每段 resize 到 [H] 高后
         * 宽度 ≤ [W]（不压扁）；相邻段重叠约一个字高，保证重叠区完整包含一个字形 ——
         * 拼接时把后段前缀里的字符去掉即可去重，也不会丢字。
         *
         * 纯函数，JVM 单测锁定「不压扁 / 全覆盖 / 相邻重叠」三条语义。
         */
        internal fun lineSegments(width: Int, height: Int): List<LineSegment> {
            require(width >= 1 && height >= 1) { "非法行尺寸：${width}x${height}" }
            // 源图上的一段最宽可 resize 到 W 宽（不压扁）
            val maxSegW = W * height / H
            if (width <= maxSegW) return listOf(LineSegment(0, width, 0))
            // 重叠取一个字高（CJK 字宽 ≈ 字高）：重叠区里含一个完整字形，
            // 后段按前缀丢弃即可去重，切缝也不会吃掉半个字。
            val overlap = height.coerceAtMost(maxSegW / 2)
            // 先定段数（按“每段有效步进 = 段宽 - 重叠”能覆盖全行），再均分步进。
            val k = ceil(width / (maxSegW - overlap).toDouble()).toInt().coerceAtLeast(2)
            val segW = (ceil(width / k.toDouble()).toInt() + overlap).coerceAtMost(maxSegW)
            return (0 until k).map { i ->
                val start = i * (width - segW) / (k - 1)
                val prevStart = if (i == 0) 0 else (i - 1) * (width - segW) / (k - 1)
                // 实际重叠 = 上一段末尾与本段开头之差（整数除法舍入后可能 > overlap）
                val actualOverlap = if (i == 0) 0 else prevStart + segW - start
                LineSegment(start, minOf(start + segW, width), actualOverlap)
            }
        }
    }

    private val rec = PpOcrModelRunner.fromFile(context, modelFile)

    /** CTC 布局：0=blank，其后为 characters.json 的其余字符。 */
    private val chars: Array<String>

    init {
        val json = org.json.JSONArray(dictFile.readText())
        chars = Array(json.length()) { json.getString(it) }
        require(chars.size > 2) { "字典为空：${dictFile.name}" }
    }

    /** crop：H*W*3 行主序 [0,255]（已 resize+pad 的文本行）。返回识别文本。 */
    fun recognize(crop: FloatArray): String = decodeSteps(runCtc(crop), chars).joinToString("") { it.text }

    /** 预处理 + 推理，返回原始 logits（recognize / recognizeDetailed 共用）。 */
    private fun runCtc(crop: FloatArray): FloatArray {
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
        return logits
    }

    /** crop：H*W*3 行主序 [0,255]。返回解码字符及其 CTC 时间步（切段拼接去重用）。 */
    fun recognizeDetailed(crop: FloatArray): List<CtcChar> = decodeSteps(runCtc(crop), chars)

    override fun close() {
        rec.close()
    }
}
