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

    private fun dropped(time: Double = 1.0) =
        RawInputEvent(time, "EV_SYN", "SYN_DROPPED", 0)

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

    /** 显式指定时间戳的一批事件：用于帧新鲜度校验的用例。 */
    private fun feed(
        tracker: TouchTracker,
        events: List<RawInputEvent>,
        time: Double,
    ): TouchFrame? {
        var last: TouchFrame? = null
        events.forEach { event -> tracker.feed(event.copy(timestamp = time))?.let { last = it } }
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

    @Test
    fun `dropped mid-session clears slots and emits up`() {
        val tracker = TouchTracker()
        feed(tracker, down(0, 7, 100, 200))
        feed(tracker, down(1, 8, 300, 400))

        // 内核丢掉了一批事件：槽位里还留着两个已经不存在的触点。
        val recovered = tracker.feed(dropped())!!
        assertEquals(TouchAction.Up, recovered.action)
        assertTrue(recovered.points.isEmpty())

        // 清空之后的第一帧必须是干净的新按下，不能带着幽灵触点。
        val next = feed(tracker, down(0, 9, 10, 20))!!
        assertEquals(TouchAction.Down, next.action)
        assertEquals(listOf(TouchPoint(9, 10, 20)), next.points)
    }

    @Test
    fun `dropped while idle emits nothing`() {
        val tracker = TouchTracker()
        // 没在跟踪触点时补 Up 会凭空造出一次抬手，把上层手势当成真抬手处理。
        assertNull(tracker.feed(dropped()))
        assertNull(tracker.feed(syn()))
    }

    @Test
    fun `dropped then the real release does not double emit up`() {
        val tracker = TouchTracker()
        feed(tracker, down(0, 7, 100, 200))
        assertEquals(TouchAction.Up, tracker.feed(dropped())!!.action)
        // 丢帧后应用侧残留的那个抬指事件仍可能到达：会话已收口，不能再发一次 Up。
        assertNull(feed(tracker, up(0)))
    }

    @Test
    fun `stale frame beyond the clock window is discarded instead of committing motion`() {
        var now = 10_000L
        val tracker = TouchTracker(clockMs = { now })
        feed(tracker, down(0, 7, 100, 200), clockFrameTime(now))

        // 读取侧积压：回放一帧 3 秒前的坐标。若当成真 Move，上层会按“现在的时刻”
        // 结算“过去的位移”，长按时序与滑动判定都会错。
        now += 3_000L
        val stale = feed(tracker, move(0, 900, 900), staleFrameTime(now - 3_000L))
        assertEquals(TouchAction.Up, stale!!.action)
        assertTrue(stale.points.isEmpty())

        val next = feed(tracker, down(0, 8, 10, 20), clockFrameTime(now))!!
        assertEquals(TouchAction.Down, next.action)
        assertEquals(listOf(TouchPoint(8, 10, 20)), next.points)
    }

    @Test
    fun `frame within the clock window is committed`() {
        var now = 10_000L
        val tracker = TouchTracker(clockMs = { now })
        feed(tracker, down(0, 7, 100, 200), clockFrameTime(now))

        // 900ms 前的帧仍在窗口内：允许提交，不误杀。
        val move = feed(tracker, move(0, 120, 210), staleFrameTime(now - 900L))!!
        assertEquals(TouchAction.Move, move.action)
        assertEquals(120, move.points.single().x)
    }

    @Test
    fun `frame timestamped in the future beyond tolerance is discarded`() {
        var now = 10_000L
        val tracker = TouchTracker(clockMs = { now })
        feed(tracker, down(0, 7, 100, 200), clockFrameTime(now))
        val ahead = feed(tracker, move(0, 120, 210), clockFrameTime(now + 500L))
        assertEquals(TouchAction.Up, ahead!!.action)
    }

    @Test
    fun `missing timestamp skips the freshness check`() {
        // getevent 没给可解析时间戳时 frameTime 为 0：宁可放过，也不要把整段手势误杀。
        val tracker = TouchTracker(clockMs = { 10_000L })
        val frame = feed(tracker, down(0, 7, 100, 200), time = 0.0)!!
        assertEquals(TouchAction.Down, frame.action)
    }

    @Test
    fun `BTN_TOOL_FINGER release does not end a BTN_TOUCH session`() {
        val tracker = TouchTracker()
        val pressed = feed(
            tracker,
            listOf(
                RawInputEvent(1.0, "EV_KEY", "BTN_TOUCH", 1),
                RawInputEvent(1.0, "EV_KEY", "BTN_TOOL_FINGER", 1),
                abs("ABS_X", 50),
                abs("ABS_Y", 60),
                syn(),
            ),
        )!!
        assertEquals(TouchAction.Down, pressed.action)

        // 部分机型抬指前先发 BTN_TOOL_FINGER=0（手掌抑制 / tool 切换），而 BTN_TOUCH 仍为 1。
        // 两者共用字段时这里会被误判成抬手，会话提前结束。
        val stillDown = feed(
            tracker,
            listOf(
                RawInputEvent(2.0, "EV_KEY", "BTN_TOOL_FINGER", 0),
                abs("ABS_X", 52),
                abs("ABS_Y", 62),
                syn(2.0),
            ),
        )!!
        assertEquals(TouchAction.Move, stillDown.action)

        // 真正的抬手信号是 BTN_TOUCH=0。
        val released = feed(
            tracker,
            listOf(RawInputEvent(3.0, "EV_KEY", "BTN_TOUCH", 0), syn(3.0)),
        )!!
        assertEquals(TouchAction.Up, released.action)
    }

    @Test
    fun `BTN_TOOL_FINGER only device still reports down and up`() {
        val tracker = TouchTracker()
        val frame = feed(
            tracker,
            listOf(
                RawInputEvent(1.0, "EV_KEY", "BTN_TOOL_FINGER", 1),
                abs("ABS_X", 50),
                abs("ABS_Y", 60),
                syn(),
            ),
        )!!
        assertEquals(TouchAction.Down, frame.action)

        // 从不报 BTN_TOUCH 的设备只能靠 BTN_TOOL_FINGER 收尾，否则永远停在按下态。
        val released = feed(
            tracker,
            listOf(RawInputEvent(2.0, "EV_KEY", "BTN_TOOL_FINGER", 0), syn(2.0)),
        )!!
        assertEquals(TouchAction.Up, released.action)
    }

    // 内核事件时间戳是秒（CLOCK_MONOTONIC 浮点），假时钟用毫秒。
    private fun clockFrameTime(nowMs: Long): Double = nowMs / 1000.0

    /** 假装这一帧是 [pastMs] 那个时刻产生的，但设备时钟已经走到了现在。 */
    private fun staleFrameTime(eventTimeMs: Long): Double = eventTimeMs / 1000.0
}
