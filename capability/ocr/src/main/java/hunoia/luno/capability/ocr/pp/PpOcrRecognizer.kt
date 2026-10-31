package hunoia.luno.capability.ocr.pp

import android.content.Context
import java.io.Closeable
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * PP-OCRv6 文本识别（PP-HGNetV2/SVTR + CTC）on LiteRT GPU delegate。
 *
 * line[1,3,48,W] -> logits[1,T,C]（CTC）。v6 用统一的 50 语言字符表（characters.json，
 * 含 blank 在 index 0），单模型覆盖中文 / 日文 / 韩文 / 拉丁等；[dictFile] 为 JSON 字符串
 * 数组，长度必须与模型输出 class 数一致。
 *
 * 输入宽 [inputWidth] 由模型自己读（320 / 640 / 960 三档），切段与字符坐标映射都跟着它走。
 */
/**
 * 单个解码出的字符：[text] 为字典字符，[step] 为 CTC 时间步，[x] 是映射回输入宽度的
 * 字符中心（x ≈ (step+0.5) × W / T；切段拼接去重按 [x] 判断位置）。
 */
data class CtcChar(val step: Int, val text: String, val x: Float)

/**
 * 一个切段：源图 x 范围 `[x0, x1)`。
 *
 * - [overlapSrc]：与上一段的重叠宽度（源图像素；首段为 0）。
 * - [dropSrc]：拼接时本段开头要丢弃的宽度（源图像素；首段为 0）。取
 *   `overlapSrc - 半个字形宽`：重叠区里被上一段完整看到的字丢掉，剩下刚好被本段
 *   完整看到的字。详见 [PpOcrRecognizer.lineSegments]。
 * - [dropTailSrc]：本段结尾要丢弃的宽度（源图像素；末段为 0）。上一段在结尾处
 *   只看到半个字形，CTC 仍可能解出一个错字/多字；这些字由下一段完整看到并输出，
 *   所以上一段尾部必须切掉。取值 = 半个字形宽。
 */
internal data class LineSegment(
    val x0: Int,
    val x1: Int,
    val overlapSrc: Int,
    val dropSrc: Int = 0,
    val dropTailSrc: Int = 0,
)

class PpOcrRecognizer(
    private val context: Context,
    modelFile: File,
    dictFile: File,
) : Closeable {

    companion object {
        /** 基准档（模型输入尺寸读不到时的回落值，也是单测的默认参数）。 */
        const val H = PpOcrModelRunner.DEFAULT_HEIGHT
        const val W = PpOcrModelRunner.DEFAULT_WIDTH

        /**
         * 字形宽度上界（相对行高）。CJK 字宽≈字高；拉丁宽字母（M/W）在窄行框里
         * 可能比框高还宽，留 30% 余量。切段去重靠它保证“重叠区里至少完整包含一个字”。
         */
        private const val GLYPH_WIDTH_HEIGHTS = 1.3f

        /**
         * 相邻段重叠 = 2 字高：大于字形宽度上界 1.3h，保证每个字至少在一段里完整；
         * 多出的 0.7h 是边界字的上下文余量（保留字距段首至少 0.7h，不贴边）。
         */
        private const val OVERLAP_HEIGHTS = 2.0f

        /**
         * CTC 贪心解码（带时间步）：去重复字符、去 blank（index 0）。
         *
         * [step] 线性映射回输入宽度：x ≈ (step + 0.5) × W / T，切段拼接时靠它
         * 判断字符落在段内什么位置（见 [PpOcrEngine] 的重叠区去重）。
         */
        internal fun decodeSteps(
            logits: FloatArray,
            chars: Array<String>,
            width: Int = W,
        ): List<CtcChar> {
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
                    val x = (step + 0.5f) * width / t
                    out.add(CtcChar(step, chars[best], x)) // CTC 折叠：去重复 + 去 blank
                }
                prev = best
            }
            return out
        }

        /** CTC 贪心解码：去重复字符、去 blank（index 0）。 */
        internal fun decode(logits: FloatArray, chars: Array<String>, width: Int = W): String =
            decodeSteps(logits, chars, width).joinToString("") { it.text }

        /**
         * 超宽行的切段方案：模型输入宽度固定 [modelWidth]，宽高比超过 W/H 的行若整体压扁到
         * [modelWidth] 宽会成批错字，必须按原图切段、每段独立识别后拼接。
         *
         * 返回源图 x 上的切段（升序、覆盖 `[0, width)`）：每段 resize 到 [modelHeight] 高后
         * 宽度 ≤ [modelWidth]（不压扁）。
         *
         * 重叠与去重的推导（h = 行高，C = 字形宽度上界 = 1.3h，O = 重叠 = 2h）：
         * - 本段开头丢弃中心 < `O - C/2` 的字符 ⇒ 被丢弃字的右边界 ≤ O，一定在**上一段里
         *   完整可见**，由上一段出字，不会丢字；
         * - 本段保留中心 ≥ `O - C/2` 的字符 ⇒ 字的左边界 ≥ `O - C` = 0.7h > 0，且离段首
         *   有足够上下文，一定在**本段里完整**，不会出现被切掉一半的字形；
         * - 本段结尾丢弃中心 > `段宽 - C/2` 的字符：这些字只有右半截在本段里，CTC 仍可能
         *   解出一个错字/多字；它们由下一段完整看到并输出（下一段的保留起点恰好是
         *   `段宽 - C/2`），所以上段尾部必须切掉。
         *
         * 三条合起来，每段实际贡献 `段宽 - O` 宽的字符，边界处每个字恰好由一侧完整出字，
         * 既不重复也不丢字。
         *
         * 纯函数，JVM 单测锁定「不压扁 / 全覆盖 / 相邻重叠 / 头尾丢弃窗口」语义。
         */
        internal fun lineSegments(
            width: Int,
            height: Int,
            modelWidth: Int = W,
            modelHeight: Int = H,
        ): List<LineSegment> {
            require(width >= 1 && height >= 1) { "非法行尺寸：${width}x${height}" }
            // 源图上的一段最宽可 resize 到 modelWidth 宽（不压扁）
            val maxSegW = modelWidth * height / modelHeight
            if (width <= maxSegW) return listOf(LineSegment(0, width, 0, 0, 0))
            val overlap = (height * OVERLAP_HEIGHTS).roundToInt()
                .coerceIn(height, maxSegW / 2)
            val glyphHalf = (height * GLYPH_WIDTH_HEIGHTS / 2f).roundToInt()
            // 先定段数（按“每段有效步进 = 段宽 - 重叠”能覆盖全行），再均分步进。
            val k = ceil(width / (maxSegW - overlap).toDouble()).toInt().coerceAtLeast(2)
            val segW = (ceil(width / k.toDouble()).toInt() + overlap).coerceAtMost(maxSegW)
            return (0 until k).map { i ->
                val start = i * (width - segW) / (k - 1)
                val prevStart = if (i == 0) 0 else (i - 1) * (width - segW) / (k - 1)
                // 实际重叠 = 上一段末尾与本段开头之差。均分步进后它常常大于计划值，
                // 前缀丢弃必须按**实际**重叠算，否则多出来的重叠区会被两段各出一次字。
                val actualOverlap = if (i == 0) 0 else prevStart + segW - start
                LineSegment(
                    x0 = start,
                    x1 = minOf(start + segW, width),
                    overlapSrc = actualOverlap,
                    // 上一段完整看到的字：中心 < 实际重叠 - 半个字形宽；其余由本段出。
                    dropSrc = if (i == 0) 0 else (actualOverlap - glyphHalf).coerceAtLeast(0),
                    // 末段没有下一段接手，尾部必须全部保留。
                    dropTailSrc = if (i == k - 1) 0 else glyphHalf,
                )
            }
        }
    }

    private val rec = PpOcrModelRunner.fromFile(context, modelFile)

    /** 模型自身的输入尺寸（由 tflite 输入张量读出）。 */
    val inputHeight: Int = rec.inputHeight
    val inputWidth: Int = rec.inputWidth

    /** CTC 布局：0=blank，其后为 characters.json 的其余字符。 */
    private val chars: Array<String>

    init {
        val json = org.json.JSONArray(dictFile.readText())
        chars = Array(json.length()) { json.getString(it) }
        require(chars.size > 2) { "字典为空：${dictFile.name}" }
    }

    /** crop：H*W*3 行主序 [0,255]（已 resize+pad 的文本行）。返回识别文本。 */
    fun recognize(crop: FloatArray): String =
        decodeSteps(runCtc(crop), chars, inputWidth).joinToString("") { it.text }

    /** 预处理 + 推理，返回原始 logits（recognize / recognizeDetailed 共用）。 */
    private fun runCtc(crop: FloatArray): FloatArray {
        val hw = inputHeight * inputWidth
        val chw = FloatArray(3 * hw)
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
    fun recognizeDetailed(crop: FloatArray): List<CtcChar> =
        decodeSteps(runCtc(crop), chars, inputWidth)

    override fun close() {
        rec.close()
    }
}
