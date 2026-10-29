package hunoia.luno.gesture.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetEventParserTest {

    @Test
    fun `parse devices picks touchscreen and ranges`() {
        val devices = GetEventParser.parseDevices(SAMPLE_DEVICES)

        assertEquals(2, devices.size)

        val keys = devices.first { it.path == "/dev/input/event0" }
        assertEquals("gpio-keys", keys.name)
        assertTrue(keys.hasTouchKey == false)
        assertTrue(keys.isTouchscreen == false)

        val touch = devices.first { it.path == "/dev/input/event2" }
        assertEquals("synaptics_dsx", touch.name)
        assertTrue(touch.isTouchscreen)
        assertTrue(touch.hasMultiTouch)
        assertTrue(touch.isDirect)
        assertEquals(AbsRange(0, 1079), touch.xRange)
        assertEquals(AbsRange(0, 2399), touch.yRange)
        assertEquals(0.5f, touch.normalizedX(539)!!, 0.01f)
        assertEquals(1f, touch.normalizedY(3000)!!, 0.001f)
        assertTrue(touch.score > keys.score)
    }

    @Test
    fun `parse event line reads labeled type code and hex value`() {
        val event = GetEventParser.parseEvent(
            "[   75100.752262] EV_ABS       ABS_MT_POSITION_X    00000123"
        )
        assertNotNull(event)
        assertEquals(75100.752262, event!!.timestamp, 0.000001)
        assertEquals("EV_ABS", event.type)
        assertEquals("ABS_MT_POSITION_X", event.code)
        assertEquals(0x123, event.value)
    }

    @Test
    fun `parse event line converts ffffffff to minus one`() {
        val event = GetEventParser.parseEvent(
            "[   75100.752262] EV_ABS       ABS_MT_TRACKING_ID   ffffffff"
        )
        assertEquals(-1, event!!.value)
    }

    @Test
    fun `parse event line tolerates device path prefix`() {
        val event = GetEventParser.parseEvent(
            "/dev/input/event2: [   75100.752262] EV_ABS       ABS_MT_POSITION_X    00000123"
        )
        assertNotNull(event)
        assertEquals("EV_ABS", event!!.type)
        assertEquals("ABS_MT_POSITION_X", event.code)
        assertEquals(0x123, event.value)
    }

    @Test
    fun `parse event rejects non event lines`() {
        assertNull(GetEventParser.parseEvent("add device 1: /dev/input/event2"))
        assertNull(GetEventParser.parseEvent(""))
    }

    @Test
    fun `tracker emits down move up for protocol b`() {
        val tracker = TouchTracker()
        assertNull(tracker.feed(event("EV_ABS", "ABS_MT_SLOT", 0)))
        assertNull(tracker.feed(event("EV_ABS", "ABS_MT_TRACKING_ID", 5)))
        assertNull(tracker.feed(event("EV_ABS", "ABS_MT_POSITION_X", 100)))
        assertNull(tracker.feed(event("EV_ABS", "ABS_MT_POSITION_Y", 200)))
        val down = tracker.feed(event("EV_SYN", "SYN_REPORT", 0))
        assertEquals(TouchAction.Down, down!!.action)
        assertEquals(listOf(TouchPoint(5, 100, 200)), down.points)

        assertNull(tracker.feed(event("EV_ABS", "ABS_MT_POSITION_X", 120)))
        val move = tracker.feed(event("EV_SYN", "SYN_REPORT", 0))
        assertEquals(TouchAction.Move, move!!.action)
        assertEquals(listOf(TouchPoint(5, 120, 200)), move.points)

        assertNull(tracker.feed(event("EV_ABS", "ABS_MT_TRACKING_ID", -1)))
        val up = tracker.feed(event("EV_SYN", "SYN_REPORT", 0))
        assertEquals(TouchAction.Up, up!!.action)
        assertTrue(up.points.isEmpty())
    }

    @Test
    fun `tracker emits single touch via BTN_TOUCH`() {
        val tracker = TouchTracker()
        assertNull(tracker.feed(event("EV_ABS", "ABS_X", 10)))
        assertNull(tracker.feed(event("EV_ABS", "ABS_Y", 20)))
        assertNull(tracker.feed(event("EV_KEY", "BTN_TOUCH", 1)))
        val down = tracker.feed(event("EV_SYN", "SYN_REPORT", 0))
        assertEquals(TouchAction.Down, down!!.action)
        assertEquals(listOf(TouchPoint(0, 10, 20)), down.points)

        assertNull(tracker.feed(event("EV_KEY", "BTN_TOUCH", 0)))
        val up = tracker.feed(event("EV_SYN", "SYN_REPORT", 0))
        assertEquals(TouchAction.Up, up!!.action)
    }

    private fun event(type: String, code: String, value: Int) =
        RawInputEvent(timestamp = 1.0, type = type, code = code, value = value)

    private companion object {
        val SAMPLE_DEVICES = """
            add device 1: /dev/input/event0
              bus:      0019
              vendor    0000
              product   0000
              version   0000
              name:     "gpio-keys"
              location: "gpio-keys/input0"
              id:       "0001:0001:0001:0001"
              version:  1.0.1
              events:
                KEY (0001): KEY_VOLUMEUP KEY_VOLUMEDOWN
              input props:
                <none>

            add device 2: /dev/input/event2
              bus:      0018
              vendor    0000
              product   0000
              version   0000
              name:     "synaptics_dsx"
              location: "synaptics_dsx/input0"
              id:       "0018:0001:0001:0001"
              version:  1.0.1
              events:
                KEY (0001): BTN_TOUCH
                ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                            ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_X     : value 0, min 0, max 1079, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_Y     : value 0, min 0, max 2399, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT
        """.trimIndent()
    }
}
