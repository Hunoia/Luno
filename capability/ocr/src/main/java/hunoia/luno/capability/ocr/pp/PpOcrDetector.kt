package hunoia.luno.capability.ocr.pp

import android.content.Context
import java.io.Closeable
import java.io.File
import kotlin.math.min

/**
 * PP-OCRv5 文本检测（DBNet）on LiteRT（移植自 google-ai-edge/litert-samples）。
 *
 * image[1,3,640,640] -> prob map[640*640]（sigmoid 文本区域概率）。模型来自用户导入的
 * host AOT 包，优先 NPU、失败降级 CPU；取框（阈值 + 连通域 + unclip）在 CPU，
 * 标准 OCR 部署切分。
 */
class PpOcrDetector(context: Context, modelFile: File) : Closeable {

    companion object {
        const val SIZE = 640
        // ImageNet 归一化（PP-OCR det 预处理）
        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        const val THRESH = 0.3f
        const val BOX_THRESH = 0.5f
        const val MIN_SIZE = 6

        /**
         * unclip 面积比：按 PaddleOCR DB 后处理的标准做法，把连通域沿法线向外扩
         * `area * ratio / perimeter`。实测直接取 bbox（不扩）会把字形上下裁掉，
         * 识别结果在「蓝牙/医牙」「WLAN/NAN」之间抖。
         */
        const val UNCLIP_RATIO = 1.6f

        /**
         * det 概率图的还原系数。
         *
         * 这版 int8 导出把 sigmoid 结果量化成 int8(scale=1/256, zero_point=-128) 再
         * DEQUANTIZE 成 float：`(q + 128) / 256`，实测输出落在 0..0.5，文字区域大面积
         * 饱和在 0.5（`-128` 那半段是负概率）。直接拿它当 0..1 概率用，[BOX_THRESH]
         * 的“连通域平均概率”过滤永远不成立（平均 ≤ 0.5），绝大多数界面 0 个框 ——
         * 表现就是“未识别到可复制内容”。乘以 2 还原到 0..1 后检测恢复正常。
         */
        const val PROB_SCALE = 2f

        /**
         * 对概率图阈值化、找连通文本区域，返回 unclip 后的框（SIZE 空间坐标系）。
         */
        fun boxes(prob: FloatArray): List<Box> {
            val n = SIZE * SIZE
            val bin = BooleanArray(n) { prob[it] > THRESH }
            val label = IntArray(n) { -1 }
            val out = ArrayList<Box>()
            val stack = ArrayDeque<Int>()
            for (start in 0 until n) {
                if (!bin[start] || label[start] != -1) continue
                var x0 = SIZE
                var y0 = SIZE
                var x1 = 0
                var y1 = 0
                var area = 0
                var scoreSum = 0f
                stack.addLast(start)
                label[start] = start
                while (stack.isNotEmpty()) {
                    val p = stack.removeLast()
                    val px = p % SIZE
                    val py = p / SIZE
                    if (px < x0) x0 = px
                    if (px > x1) x1 = px
                    if (py < y0) y0 = py
                    if (py > y1) y1 = py
                    area++
                    scoreSum += prob[p]
                    if (px > 0 && bin[p - 1] && label[p - 1] == -1) {
                        label[p - 1] = start
                        stack.addLast(p - 1)
                    }
                    if (px < SIZE - 1 && bin[p + 1] && label[p + 1] == -1) {
                        label[p + 1] = start
                        stack.addLast(p + 1)
                    }
                    if (py > 0 && bin[p - SIZE] && label[p - SIZE] == -1) {
                        label[p - SIZE] = start
                        stack.addLast(p - SIZE)
                    }
                    if (py < SIZE - 1 && bin[p + SIZE] && label[p + SIZE] == -1) {
                        label[p + SIZE] = start
                        stack.addLast(p + SIZE)
                    }
                }
                val w = x1 - x0 + 1
                val h = y1 - y0 + 1
                if (w < MIN_SIZE || h < MIN_SIZE) continue
                if (scoreSum / area < BOX_THRESH) continue
                // unclip：PaddleOCR 的 DB 后处理是 area * ratio / perimeter（等距外扩），
                // 比“按短边 0.35 倍”更贴合细长行：短边小、周长大 → 外扩量随行长变化。
                val pad = (area * UNCLIP_RATIO / (2f * (w + h))).toInt().coerceIn(2, 30)
                out.add(
                    Box(
                        (x0 - pad).coerceAtLeast(0),
                        (y0 - pad).coerceAtLeast(0),
                        (x1 + pad).coerceAtMost(SIZE - 1),
                        (y1 + pad).coerceAtMost(SIZE - 1),
                    )
                )
            }
            // 从上到下、同行从左到右
            return out.sortedWith(compareBy({ it.y0 / 16 }, { it.x0 }))
        }
    }

    data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    private val det = PpOcrModelRunner.fromFile(context, modelFile)

    /** rgb：SIZE*SIZE*3 行主序 [0,255]。返回 [SIZE*SIZE] 概率图。 */
    fun probMap(rgb: FloatArray): FloatArray {
        val chw = FloatArray(3 * SIZE * SIZE)
        val hw = SIZE * SIZE
        for (i in 0 until hw) {
            chw[i] = (rgb[i * 3] / 255f - MEAN[0]) / STD[0]
            chw[hw + i] = (rgb[i * 3 + 1] / 255f - MEAN[1]) / STD[1]
            chw[2 * hw + i] = (rgb[i * 3 + 2] / 255f - MEAN[2]) / STD[2]
        }
        det.writeInput(0, chw)
        det.run()
        val prob = det.readOutput(0)
        for (i in prob.indices) {
            prob[i] = (prob[i] * PROB_SCALE).coerceAtMost(1f)
        }
        return prob
    }

    override fun close() {
        det.close()
    }
}

