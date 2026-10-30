package hunoia.luno.capability.ocr.pp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * det 后处理：概率图 → 文本行框。
 *
 * 这里锁的是“连通域 + 平均概率 + 最小尺寸 + unclip”这套切分的语义 —— 曾经因为
 * 概率图被 int8 导出压到 0..0.5（见 [PpOcrDetector.PROB_SCALE]），平均概率过滤永远
 * 不成立，全屏 0 个框，表现成“未识别到可复制内容”。
 */
class PpOcrDetectorTest {

    private fun map(fill: Float = 0f) = FloatArray(PpOcrDetector.SIZE * PpOcrDetector.SIZE) { fill }

    /** 一条横排文字带：100..109 行、60..359 列。 */
    private fun textLineMap(value: Float): FloatArray {
        val prob = map()
        for (y in 100..109) for (x in 60..359) prob[y * PpOcrDetector.SIZE + x] = value
        return prob
    }

    @Test
    fun `空白概率图不产生任何框`() {
        assertTrue(PpOcrDetector.boxes(map()).isEmpty())
    }

    @Test
    fun `平均概率够高的文字带产出一个框`() {
        val boxes = PpOcrDetector.boxes(textLineMap(0.95f))
        assertEquals(1, boxes.size)
        val box = boxes.single()
        // 带子本体在 60..359 / 100..109，unclip 只允许外扩（不裁掉原带）。
        assertTrue("left=${box.x0}", box.x0 <= 60)
        assertTrue("right=${box.x1}", box.x1 >= 359)
        assertTrue("top=${box.y0}", box.y0 <= 100)
        assertTrue("bottom=${box.y1}", box.y1 >= 109)
    }

    @Test
    fun `平均概率低于阈值时丢弃`() {
        // 0.45 < BOX_THRESH(0.5)：还原概率图后仍不达标
        assertTrue(PpOcrDetector.boxes(textLineMap(0.45f)).isEmpty())
    }

    @Test
    fun `小于最小尺寸的噪点被丢弃`() {
        val prob = map()
        for (y in 100..102) for (x in 60..61) prob[y * PpOcrDetector.SIZE + x] = 0.99f
        assertTrue(PpOcrDetector.boxes(prob).isEmpty())
    }

    @Test
    fun `多行按阅读顺序从上到下返回`() {
        val prob = map()
        for (y in 300..309) for (x in 60..359) prob[y * PpOcrDetector.SIZE + x] = 0.95f
        for (y in 100..109) for (x in 60..359) prob[y * PpOcrDetector.SIZE + x] = 0.95f
        val boxes = PpOcrDetector.boxes(prob)
        assertEquals(2, boxes.size)
        assertTrue(boxes[0].y0 < boxes[1].y0)
    }
}
