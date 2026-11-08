package hunoia.luno.gesture.touchsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchDeviceScannerTest {

    /** 真机（HyperOS，Xiaomi_Touch_Input_0 = /dev/input/event7）`getevent -pl` 逐节点输出的触摸屏段。 */
    private val touchscreenBlock = """
        add device 1: /dev/input/event7
          name:     "Xiaomi_Touch_Input_0"
          events:
            KEY (0001): 008f  0145* 014a* 0162
            ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                        ABS_MT_TOUCH_MAJOR    : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_X     : value 0, min 0, max 119999, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y     : value 0, min 0, max 266999, fuzz 0, flat 0, resolution 0
                        ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_DIRECT
    """.trimIndent()

    /** 非触摸设备（按键）。 */
    private val keysBlock = """
        add device 1: /dev/input/event0
          name:     "gpio-keys"
          events:
            KEY (0001): KEY_VOLUMEUP
          input props:
            <none>
    """.trimIndent()

    @Test
    fun `带标签输出能解析出触摸屏`() {
        val report = TouchDeviceScanner.parse(shellOk = true, output = "$keysBlock\n$touchscreenBlock\n")
        assertTrue(report.usable)
        assertEquals("/dev/input/event7", report.touchscreen?.path)
        assertEquals("Xiaomi_Touch_Input_0", report.touchscreen?.name)
        assertNotNull(report.touchscreen?.xRange)
        assertNotNull(report.touchscreen?.yRange)
    }

    @Test
    fun `不带 -l 的十六进制码输出解析不出触摸屏`() {
        // 这正是上次线上断点：getevent -p（无 -l）的 ABS 段是十六进制码，range 全空。
        val hexBlock = """
            add device 1: /dev/input/event7
              name:     "Xiaomi_Touch_Input_0"
              events:
                KEY (0001): 008f  0145* 014a* 0162
                ABS (0003): 002f  : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                            0035  : value 0, min 0, max 119999, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 266999, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT
        """.trimIndent()
        val report = TouchDeviceScanner.parse(shellOk = true, output = hexBlock)
        assertFalse(report.usable)
        assertNull(report.touchscreen)
    }

    @Test
    fun `shell 失败或无设备时不可用`() {
        assertFalse(TouchDeviceScanner.parse(shellOk = false, output = "").usable)
        assertFalse(TouchDeviceScanner.parse(shellOk = true, output = keysBlock).usable)
    }

    @Test
    fun `探测命令必须带 -l 且逐节点`() {
        val cmd = TouchDeviceScanner.PROBE_COMMAND
        assertTrue("探测必须用 -pl（保留 ABS 标签）", "-pl" in cmd)
        assertTrue("探测必须逐节点指定文件（避开 ROM 的目录 watch 限制）", "/dev/input/event*" in cmd)
    }
}
