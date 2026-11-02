package hunoia.luno.gesture.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内核输入帧聚合：把 `EV_ABS/EV_KEY` 事件按 `SYN_REPORT` 聚成帧。
 *
 * 关键语义是「多指不打断会话」「抬起必须产出一帧空点集」，两者错一个，
 * 触钮就会卡在按下态或漏掉长按判定。
 */
class TouchTrackerTest {

    private fun abs(code: String, value: Int, time: Double = 1.0) =
        RawInputEvent(time, "EV_ABS", code, value)

    private fun syn(time: Double = 1.0) =
        RawInputEvent(time, "EV_SYN", "SYN_REPORT", 0)

    private fun down(slot: Int, id: Int, x: Int, y: Int): List<RawInputEvent> = listOf(
        abs("ABS_MT_SLOT", slot),
        abs("ABS_MT_TRACKING_ID", id),
        abs("ABS_MT_POSITION_X", x),
        abs("ABS_MT_POSITION_Y", y),
        syn(),
    )

    private fun move(slot: Int, x: Int, y: Int): List<RawInputEvent> = listOf(
        abs("ABS_MT_SLOT", slot),
        abs("ABS_MT_POSITION_X", x),
        abs("ABS_MT_POSITION_Y", y),
        syn(),
    )

    private fun up(slot: Int): List<RawInputEvent> = listOf(
        abs("ABS_MT_SLOT", slot),
        abs("ABS_MT_TRACKING_ID", -1),
        syn(),
    )

    private fun feed(tracker: TouchTracker, events: List<RawInputEvent>): TouchFrame? {
        var last: TouchFrame? = null
        events.forEach { event -> tracker.feed(event)?.let { last = it } }
        return last
    }

    @Test
    fun `down then move then up produces one frame each`() {
        val tracker = TouchTracker()

        val down = feed(tracker, down(0, 7, 100, 200))!!
        assertEquals(TouchAction.Down, down.action)
        assertEquals(100, down.points.single().x)

        val move = feed(tracker, move(0, 120, 210))!!
        assertEquals(TouchAction.Move, move.action)
        assertEquals(120, move.points.single().x)

        val up = feed(tracker, up(0))!!
        assertEquals(TouchAction.Up, up.action)
        assertTrue(up.points.isEmpty())
    }

    @Test
    fun `second finger keeps the session and reports both points`() {
        val tracker = TouchTracker()
        feed(tracker, down(0, 7, 100, 200))

        val second = feed(tracker, down(1, 8, 300, 400))!!
        assertEquals(TouchAction.Down, second.action)
        assertEquals(2, second.points.size)

        val move = feed(tracker, move(1, 310, 410))!!
        assertEquals(TouchAction.Move, move.action)
        assertEquals(2, move.points.size)
    }

    @Test
    fun `single touch protocol uses BTN_TOUCH and ABS_X`() {
        val tracker = TouchTracker()
        val frame = feed(
            tracker,
            listOf(
                RawInputEvent(1.0, "EV_KEY", "BTN_TOUCH", 1),
                abs("ABS_X", 50),
                abs("ABS_Y", 60),
                syn(),
            ),
        )!!
        assertEquals(TouchAction.Down, frame.action)
        assertEquals(50, frame.points.single().x)

        val released = feed(tracker, listOf(RawInputEvent(2.0, "EV_KEY", "BTN_TOUCH", 0), syn(2.0)))!!
        assertEquals(TouchAction.Up, released.action)
    }

    @Test
    fun `reset drops leftover slots so the next gesture starts clean`() {
        val tracker = TouchTracker()
        feed(tracker, down(0, 7, 100, 200))
        tracker.reset()

        val next = feed(tracker, down(0, 9, 10, 20))!!
        assertEquals(TouchAction.Down, next.action)
        assertEquals(1, next.points.size)
    }

    @Test
    fun `empty syn reports before any touch produce nothing`() {
        val tracker = TouchTracker()
        assertNull(tracker.feed(syn()))
    }
}
