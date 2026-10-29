package hunoia.luno.gesture.touchsource

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.ScreenPickGesture
import hunoia.luno.gesture.touch.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全屏长按状态机：定时器触发、MOVE 早触发、超 slop 取消、触发后的拖动/抬手转发。
 *
 * 这几条一旦错，表现是“按住不动永远不出卡”或“拖动时误弹卡”，真机上很难复现，
 * 所以用假时钟把时序钉死。
 */
class GlobalLongPressTriggerTest {

    private class FakeTimer : LongPressTimer {
        var task: (() -> Unit)? = null
        var delayMs = -1L
        var cancelled = false

        override fun schedule(delayMs: Long, task: () -> Unit): Cancelable {
            this.delayMs = delayMs
            this.task = task
            cancelled = false
            return Cancelable { cancelled = true }
        }

        fun fire() {
            task?.invoke()
        }
    }

    private var now = 0L
    private val timer = FakeTimer()
    private val triggers = mutableListOf<Pair<Int, Int>>()
    private val drags = mutableListOf<Pair<Int, Int>>()
    private val releases = mutableListOf<Pair<Int, Int>>()

    private fun trigger(
        config: ScreenPickGesture = ScreenPickGesture(enabled = true, longPressDelayMs = 400, slopDp = 10f),
    ) = GlobalLongPressTrigger(
        config = { config },
        now = { now },
        timer = timer,
        onTrigger = { x, y -> triggers += x to y },
        onDrag = { x, y -> drags += x to y },
        onRelease = { x, y -> releases += x to y },
    )

    @Test
    fun `disabled config schedules nothing`() {
        val subject = trigger(ScreenPickGesture(enabled = false))
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        assertEquals(-1L, timer.delayMs)
        assertFalse(subject.isFired)
    }

    @Test
    fun `timer fires at anchor even without further frames`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        assertEquals(400L, timer.delayMs)

        timer.fire()
        assertEquals(listOf(10 to 20), triggers)
        assertTrue(subject.isFired)
    }

    @Test
    fun `fired session forwards drag and release`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire()

        subject.onFrame(Offset(80f, 90f), TouchAction.Move)
        subject.onFrame(Offset(90f, 100f), TouchAction.Up)

        assertEquals(listOf(80 to 90), drags)
        assertEquals(listOf(90 to 100), releases)
        assertFalse(subject.isFired)
    }

    @Test
    fun `move beyond slop cancels the session`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)

        subject.onFrame(Offset(80f, 20f), TouchAction.Move)
        assertTrue(timer.cancelled)

        timer.fire()
        assertTrue(triggers.isEmpty())

        subject.onFrame(Offset(80f, 20f), TouchAction.Up)
        assertTrue(releases.isEmpty())
    }

    @Test
    fun `move past delay fires without waiting for timer`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)

        now = 400
        subject.onFrame(Offset(12f, 22f), TouchAction.Move)

        assertEquals(listOf(10 to 20), triggers)
        assertTrue(subject.isFired)
    }

    @Test
    fun `a new down restarts a finished session`() {
        val subject = trigger()
        subject.onFrame(Offset(10f, 20f), TouchAction.Down)
        timer.fire()
        subject.onFrame(Offset(10f, 20f), TouchAction.Up)

        subject.onFrame(Offset(30f, 40f), TouchAction.Down)
        timer.fire()

        assertEquals(listOf(10 to 20, 30 to 40), triggers)
    }
}
