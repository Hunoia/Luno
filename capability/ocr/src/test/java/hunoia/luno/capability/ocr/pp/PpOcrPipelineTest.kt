package hunoia.luno.capability.ocr.pp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证从 litert-samples 移植的纯算法（与设备 / GPU 无关）：
 * - 检测框提取（阈值 + 连通域 + unclip）
 * - CTC 贪心解码（去重复 + 去 blank）
 */
class PpOcrPipelineTest {

    @Test
    fun ctcDropsBlankAndConsecutiveRepeatsButKeepsSeparatedRepeats() {
        val chars = arrayOf("", "A", "B", " ")
        val path = intArrayOf(1, 1, 0, 1, 2, 2, 3, 0)
        val logits = FloatArray(path.size * chars.size) { -5f }
        path.forEachIndexed { time, token -> logits[time * chars.size + token] = 5f }
        assertEquals("AAB ", PpOcrRecognizer.decode(logits, chars))
    }

    @Test
    fun textBoxFloodFillExpandsAcceptedRegionAndRejectsLowConfidence() {
        val p = FloatArray(PpOcrDetector.SIZE * PpOcrDetector.SIZE)
        for (y in 20..27) {
            for (x in 30..37) {
                p[y * PpOcrDetector.SIZE + x] = 0.9f
            }
        }
        for (y in 50..57) {
            for (x in 50..57) {
                p[y * PpOcrDetector.SIZE + x] = 0.4f // 低于 BOX_THRESH，应被拒
            }
        }
        assertEquals(
            listOf(PpOcrDetector.Box(27, 17, 40, 30)),
            PpOcrDetector.boxes(p),
        )
    }
}
