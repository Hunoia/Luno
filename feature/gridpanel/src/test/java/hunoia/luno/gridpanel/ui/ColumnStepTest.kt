package hunoia.luno.gridpanel.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 捏合调列数一次只走一档，且每档至少隔 [ColumnStepMinIntervalMillis]：
 * 输入事件比屏幕帧密时，同一帧里连走两档会让中间那档根本没被画出来（「5 被跳过」）。
 */
class ColumnStepTest {

    @Test
    fun `small jitter does not step`() {
        assertEquals(0, columnStepFor(1.0f))
        assertEquals(0, columnStepFor(1.1f))
        assertEquals(0, columnStepFor(0.95f))
        assertEquals(-1, columnStepFor(1.25f))
        assertEquals(1, columnStepFor(1f / 1.25f))
    }

    @Test
    fun `stepper walks one column per 25 percent span change`() {
        val stepper = ColumnStepper()
        stepper.start(columns = 6, span = 100f, atMillis = 0L)
        // 间距每放大 25% 走一档：6 → 5 → 4 → 3（时间是递增的，间隔不拦）。
        assertEquals(5, stepper.update(130f, 0L))
        assertEquals(4, stepper.update(170f, 100L))
        assertEquals(3, stepper.update(225f, 200L))
        assertEquals(3, stepper.columns)
    }

    @Test
    fun `fast pinch does not skip a level but is paced by time`() {
        val stepper = ColumnStepper()
        stepper.start(columns = 6, span = 100f, atMillis = 1_000L)
        // 一帧之内间距翻倍：只允许走一档，剩下的按 90ms 一档补完（2x = 1.25^3）。
        assertEquals(5, stepper.update(200f, 1_000L))
        assertNull(stepper.update(200f, 1_020L))
        assertNull(stepper.update(200f, 1_080L))
        assertEquals(4, stepper.update(200f, 1_090L))
        assertEquals(3, stepper.update(200f, 1_180L))
        assertNull(stepper.update(200f, 1_270L))
    }

    @Test
    fun `jitter after a fast pinch does not step back`() {
        val stepper = ColumnStepper()
        stepper.start(columns = 6, span = 100f, atMillis = 0L)
        // 快捏超调到 2 倍间距，按档补到 3 列（基准停在阈值位 195.3，而不是 200）。
        assertEquals(5, stepper.update(200f, 0L))
        assertEquals(4, stepper.update(200f, 90L))
        assertEquals(3, stepper.update(200f, 180L))
        // 扭动导致间距回缩到 170 / 165：落在反向死区里，不再跳回 4。
        assertNull(stepper.update(170f, 400L))
        assertNull(stepper.update(165f, 410L))
        // 反向门槛是正向的平方（1.5625×）：要收到 125 以下才退一档，明显回缩才算数。
        assertNull(stepper.update(155f, 500L))
        assertEquals(4, stepper.update(120f, 600L))
    }

    @Test
    fun `same direction keeps stepping no matter how much it overshoots`() {
        val stepper = ColumnStepper()
        stepper.start(columns = 6, span = 100f, atMillis = 0L)
        // 一直往外捏：列数只减不增（没有任何反向判定）。
        assertEquals(5, stepper.update(130f, 0L))
        assertEquals(4, stepper.update(170f, 100L))
        assertEquals(3, stepper.update(220f, 200L))
        assertEquals(2, stepper.update(290f, 300L))
        // 到下限后继续捏：不再变，也不回跳。
        assertNull(stepper.update(400f, 400L))
        assertEquals(2, stepper.columns)
    }

    @Test
    fun `stepper clamps to the configured range`() {
        val stepper = ColumnStepper(minColumns = 2, maxColumns = 4)
        stepper.start(columns = 4, span = 100f, atMillis = 0L)
        var t = 0L
        var last = 4
        repeat(10) {
            t += ColumnStepMinIntervalMillis
            stepper.update(100f * (1f + 0.3f * (it + 1)), t)?.let { last = it }
        }
        assertEquals(2, last)
        assertEquals(2, stepper.columns)
    }
}
