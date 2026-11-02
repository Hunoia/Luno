package hunoia.luno.gesture.touchsource

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.ScreenPickGesture
import hunoia.luno.gesture.touch.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全屏长按状态机：定时器触发、MOVE 早触发、超 slop 取消、触发后的拖动/抬手转发、
 * 无拖动自动取消与触发冷却。
 *
 * 这几条一旦错，表现是“按住不动永远不出卡”或“拖动时误弹卡”，真机上很难复现，
 * 所以用假时钟把时序钉死。
 */
class GlobalLongPressTriggerTest {

    private class FakeTimer : LongPressTimer {
        class Entry(val delayMs: Long, val task: () -> Unit) {
            var cancelled = false
        }

        val entries = mutableListOf<Entry>()

        val lastDelay: Long get() = entries.lastOrNull()?.delayMs ?: -1L
        val lastCancelled: Boolean get() = entries.lastOrNull()?.cancelled == true

        override fun schedule(delayMs: Long, task: () -> Unit): Cancelable {
            val entry = Entry(delayMs, task)
            entries += entry
            return Cancelable { entry.cancelled = true }
        }

        fun fire(delayMs: Long? = null) {
            entries.lastOrNull { !it.cancelled && (delayMs == null || it.delayMs == delayMs) }
                ?.task
                ?.invoke()
        }
    }

    private var now = 0L
    private val timer = FakeTimer()
    private val triggers = mutableListOf<Pair<Int, Int>>()
    private val drags = mutableListOf<Pair<Int, Int>>()
    private val releases = mutableListOf<Pair<Int, Int>>()
    private val preCaptures = mutableListOf<Triple<Int, Int, Long>>()
    private var preCaptureCancels = 0

    private fun trigger(
        config: ScreenPickGesture = ScreenPickGesture(
            enabled = true,
            longPressDelayMs = 400,
            slopDp = 10f,
            dragStartTimeoutMs = 3_000,
            triggerCooldownMs = 0,
        ),
    ) = GlobalLongPressTrigger(
        config = { config },
        now = { now },
        timer = timer,
        onTrigger = { x, y -> triggers += x to y },
        onDrag = { x, y -> drags += x to y },
        onRelease = { x, y -> releases += x to y },
        onPreCapture = { x, y, at -> preCaptures += Triple(x, y, at) },
        onPreCaptureCancel = { preCaptureCancels++ },
    )

    @Test
    fun `down starts pre-capture and up cancels it`() {
        val subject = trigger()
        now = 123
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        assertEquals(listOf(Triple(10, 20, 123L)), preCaptures)
        subject.onFrame(Offset(10f, 20f), TouchAction.Up)
        assertTrue(preCaptureCancels >= 1)
    }

    @Test
    fun `disabled config schedules nothing`() {
        val subject = trigger(ScreenPickGesture(enabled = false))
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        assertTrue(timer.entries.isEmpty())
        assertFalse(subject.isFired)
    }

    @Test
    fun `long press alone does not trigger`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        assertEquals(400L, timer.lastDelay)

        timer.fire(400)
        assertTrue(triggers.isEmpty())
        assertFalse(subject.isFired)
        // 长按窗口到期仍不动：本次会话作废。
        timer.fire(3_000)
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `long press then drag triggers and forwards frames`() {
        val subject = trigger(
            ScreenPickGesture(
                enabled = true,
                longPressDelayMs = 400,
                slopDp = 10f,
                dragStartTimeoutMs = 3_000,
                triggerCooldownMs = 0,
            ),
        )
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire(400)
        assertTrue(triggers.isEmpty())

        // 小于 slop：仍不触发。
        subject.onFrame(Offset(12f, 22f), TouchAction.Move)
        assertTrue(triggers.isEmpty())

        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        assertEquals(listOf(10 to 20), triggers)
        assertTrue(subject.isFired)
        assertEquals(listOf(80 to 20), drags)

        subject.onFrame(Offset(90f, 100f), TouchAction.Up)
        assertEquals(listOf(90 to 100), releases)
        assertFalse(subject.isFired)
    }

    @Test
    fun `drag before long press cancels`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        timer.fire(400)
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `no drag within window means no trigger`() {
        val subject = trigger(
            ScreenPickGesture(
                enabled = true,
                longPressDelayMs = 400,
                slopDp = 10f,
                dragStartTimeoutMs = 800,
                triggerCooldownMs = 0,
            ),
        )
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire(400)
        timer.fire(800)
        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        assertTrue(triggers.isEmpty())
        assertFalse(subject.isFired)
    }

    @Test
    fun `drag after long press window without timer tick still triggers`() {
        val subject = trigger(
            ScreenPickGesture(
                enabled = true,
                longPressDelayMs = 400,
                slopDp = 10f,
                dragStartTimeoutMs = 3_000,
                triggerCooldownMs = 0,
            ),
        )
        now = 450
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        now = 900
        // 定时器尚未执行：MOVE 到达时先补 arm，再按拖动触发。
        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        assertEquals(listOf(10 to 20), triggers)
    }

    @Test
    fun `zero drag window still waits for drag`() {
        val subject = trigger(
            ScreenPickGesture(
                enabled = true,
                longPressDelayMs = 400,
                slopDp = 10f,
                dragStartTimeoutMs = 0,
                triggerCooldownMs = 0,
            ),
        )
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire(400)
        assertTrue(triggers.isEmpty())
        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        assertEquals(listOf(10 to 20), triggers)
    }

    @Test
    fun `trigger cooldown blocks immediate re-arm`() {
        val subject = trigger(
            ScreenPickGesture(
                enabled = true,
                longPressDelayMs = 400,
                slopDp = 10f,
                dragStartTimeoutMs = 3_000,
                triggerCooldownMs = 800,
            ),
        )
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire(400)
        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        subject.onFrame(Offset(80f, 20f), TouchAction.Up)
        assertEquals(1, triggers.size)

        now = 500
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        subject.onFrame(Offset(10f, 20f), TouchAction.Up)
        assertEquals(1, triggers.size)

        now = 900
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire(400)
        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        assertEquals(listOf(10 to 20, 10 to 20), triggers)
    }

}
