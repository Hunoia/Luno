package hunoia.luno.gesture.touchsource

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GlobalGestureKind
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.GlobalGestureTrigger
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.ZoneBounds
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gesture.touch.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 识别区手势状态机：长按就绪、拖动点火、超时作废、冷却、多指升级与区命中。
 *
 * 这几条错了的表现是「按住不动永远不出卡」「拖动误弹卡」「第一下有效第二下失效」，
 * 真机很难复现，所以用假时钟把时序钉死。
 */
class GlobalGestureRuntimeTest {

    private class FakeTimer : LongPressTimer {
        class Entry(val delayMs: Long, val task: () -> Unit) {
            var cancelled = false
        }

        val entries = mutableListOf<Entry>()

        val lastDelay: Long get() = entries.lastOrNull()?.delayMs ?: -1L

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
    private val hits = mutableListOf<GlobalGestureHit>()
    private val drags = mutableListOf<Pair<Int, Int>>()
    private val releases = mutableListOf<Pair<Int, Int>>()
    private val preCaptures = mutableListOf<Triple<Int, Int, Long>>()
    private var preCaptureCancels = 0

    private var ignoredStart: ((Int, Int) -> Boolean) = { _, _ -> false }
    private var zoneVisible: (String) -> Boolean = { true }
    private var forwardDrag = true

    private fun trigger(
        kind: GlobalGestureKind = GlobalGestureKind.LongPress,
        fireOnHold: Boolean = false,
        longPressDelayMs: Int = 400,
        dragStartTimeoutMs: Int = 3_000,
        triggerCooldownMs: Int = 0,
        fingers: Int = 1,
        angle: GestureButtonAngle = GestureButtonAngle(listOf(0.25f)),
        directionActions: DirectionActions = DirectionActions(
            mapOf(0 to StoredAction.of("test.action")),
        ),
    ) = GlobalGestureTrigger(
        id = "t",
        kind = kind,
        fingers = fingers,
        fireOnHold = fireOnHold,
        directionActions = directionActions,
        angle = angle,
        longPressDelayMs = longPressDelayMs,
        dragStartTimeoutMs = dragStartTimeoutMs,
        slopDp = 10f,
        triggerCooldownMs = triggerCooldownMs,
    )

    private fun runtime(
        trigger: GlobalGestureTrigger = trigger(),
        zoneEnabled: Boolean = true,
        bounds: ZoneBounds = ZoneBounds.Full,
    ) = GlobalGestureRuntime(
        config = {
            GlobalGestureSettings(
                zones = listOf(
                    GlobalGestureZone(
                        id = "z",
                        enabled = zoneEnabled,
                        bounds = bounds,
                        triggers = listOf(trigger),
                    ),
                ),
            )
        },
        now = { now },
        timer = timer,
        density = { 1f },
        screenWidth = { 1000f },
        screenHeight = { 1000f },
        shouldIgnoreStart = { x, y -> ignoredStart(x, y) },
        zoneVisible = { zoneVisible(it) },
        wantsPreCapture = { true },
        onFire = { hit ->
            hits += hit
            forwardDrag
        },
        onDrag = { x, y -> drags += x to y },
        onRelease = { x, y -> releases += x to y },
        onPreCapture = { x, y, at -> preCaptures += Triple(x, y, at) },
        onPreCaptureCancel = { preCaptureCancels++ },
    )

    @Test
    fun `down starts pre-capture and up cancels it`() {
        val subject = runtime()
        now = 123
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        assertEquals(listOf(Triple(10, 20, 123L)), preCaptures)
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Up)
        assertTrue(preCaptureCancels >= 1)
    }

    @Test
    fun `ignored start point never arms`() {
        ignoredStart = { _, y -> y <= 120 }
        val subject = runtime()
        subject.onFrame(listOf(Offset(10f, 60f)), TouchAction.Down)
        assertTrue(timer.entries.isEmpty())
        assertTrue(preCaptures.isEmpty())

        subject.onFrame(listOf(Offset(10f, 400f)), TouchAction.Down)
        assertEquals(400L, timer.lastDelay)
        now = 400
        timer.fire(400)
        subject.onFrame(listOf(Offset(200f, 400f)), TouchAction.Move)
        assertEquals(1, hits.size)
    }

    @Test
    fun `disabled zone schedules nothing`() {
        val subject = runtime(zoneEnabled = false)
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        assertTrue(timer.entries.isEmpty())
        assertFalse(subject.isActive)
    }

    @Test
    fun `hidden zone never arms`() {
        zoneVisible = { false }
        val subject = runtime()
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        assertTrue(timer.entries.isEmpty())
        assertFalse(subject.isActive)
    }

    @Test
    fun `down outside zone never arms`() {
        val subject = runtime(bounds = ZoneBounds(x = 0.5f, y = 0f, width = 0.5f, height = 1f))
        subject.onFrame(listOf(Offset(100f, 20f)), TouchAction.Down)
        assertTrue(timer.entries.isEmpty())
        assertFalse(subject.isActive)
    }

    @Test
    fun `long press alone does not trigger`() {
        val subject = runtime()
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        assertEquals(400L, timer.lastDelay)

        now = 400
        timer.fire(400)
        assertTrue(hits.isEmpty())
        // 长按窗口到期仍不动：本次会话作废。
        now = 3_400
        timer.fire(3_000)
        assertTrue(hits.isEmpty())
        assertFalse(subject.isActive)
    }

    @Test
    fun `long press then drag triggers and forwards frames`() {
        val subject = runtime()
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 400
        timer.fire(400)
        assertTrue(hits.isEmpty())

        // 小于 slop：仍不触发。
        subject.onFrame(listOf(Offset(12f, 22f)), TouchAction.Move)
        assertTrue(hits.isEmpty())

        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        assertEquals(1, hits.size)
        assertEquals(Offset(10f, 20f), hits.single().anchor)
        assertEquals(Offset(80f, 20f), hits.single().position)
        // 点火那一帧只负责判定，后续帧才转发拖动。
        assertTrue(drags.isEmpty())
        subject.onFrame(listOf(Offset(90f, 30f)), TouchAction.Move)
        assertEquals(listOf(90 to 30), drags)

        subject.onFrame(listOf(Offset(90f, 100f)), TouchAction.Up)
        assertEquals(listOf(90 to 100), releases)
        assertFalse(subject.isActive)
    }

    @Test
    fun `fire on hold triggers without drag`() {
        val subject = runtime(trigger(fireOnHold = true))
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 400
        timer.fire(400)
        assertEquals(1, hits.size)
        assertTrue(hits.single().action.typeId == "test.action")
    }

    @Test
    fun `drag before long press cancels`() {
        val subject = runtime()
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        now = 400
        timer.fire(400)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `no drag within window means no trigger`() {
        val subject = runtime(trigger(dragStartTimeoutMs = 800))
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 400
        timer.fire(400)
        now = 1_200
        timer.fire()
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        assertTrue(hits.isEmpty())
        assertFalse(subject.isActive)
    }

    @Test
    fun `drag after long press window without timer tick still triggers`() {
        val subject = runtime()
        now = 450
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 900
        // 定时器尚未执行：MOVE 到达时先补 arm，再按拖动触发。
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        assertEquals(1, hits.size)
    }

    @Test
    fun `zero drag window still waits for drag`() {
        val subject = runtime(trigger(dragStartTimeoutMs = 0))
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 400
        timer.fire(400)
        assertTrue(hits.isEmpty())
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        assertEquals(1, hits.size)
    }

    @Test
    fun `trigger cooldown blocks immediate re-arm`() {
        val subject = runtime(trigger(triggerCooldownMs = 800))
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 400
        timer.fire(400)
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Up)
        assertEquals(1, hits.size)

        now = 500
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Up)
        assertEquals(1, hits.size)

        now = 1_300
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 1_700
        timer.fire(400)
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        assertEquals(2, hits.size)
    }

    @Test
    fun `two fingers landing inside aggregation window use two finger trigger`() {
        val oneFinger = trigger(id = "one")
        val twoFinger = trigger(fingers = 2, id = "two")
        val subject = GlobalGestureRuntime(
            config = {
                GlobalGestureSettings(
                    zones = listOf(
                        GlobalGestureZone(
                            id = "z",
                            triggers = listOf(twoFinger, oneFinger),
                        ),
                    ),
                )
            },
            now = { now },
            timer = timer,
            density = { 1f },
            screenWidth = { 1000f },
            screenHeight = { 1000f },
            onFire = { hit ->
                hits += hit
                true
            },
        )
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 50
        subject.onFrame(listOf(Offset(10f, 20f), Offset(30f, 20f)), TouchAction.Down)
        now = 450
        timer.fire(400)
        subject.onFrame(listOf(Offset(80f, 20f), Offset(30f, 20f)), TouchAction.Move)
        assertEquals(1, hits.size)
        assertEquals(2, hits.single().trigger.fingers)
    }

    @Test
    fun `unconfigured direction slot does not fire`() {
        val subject = runtime(
            trigger(
                directionActions = DirectionActions(mapOf(1 to StoredAction.of("test.other"))),
            ),
        )
        subject.onFrame(listOf(Offset(10f, 20f)), TouchAction.Down)
        now = 400
        timer.fire(400)
        // 单扇区任意方向 → 槽 0；只配了槽 1 时不响应。
        subject.onFrame(listOf(Offset(80f, 20f)), TouchAction.Move)
        assertTrue(hits.isEmpty())
    }

    private fun trigger(id: String, fingers: Int = 1): GlobalGestureTrigger =
        trigger(fingers = fingers).copy(id = id)
}
