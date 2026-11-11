package hunoia.luno.ui.theme

import hunoia.luno.config.model.MotionSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 动效档位换算（ui-guidelines §5.1 / §5.2）：只乘一次、三家族独立、交互反馈不入档位。 */
class MotionProfileTest {

    private fun profile(
        panel: MotionSpeed = MotionSpeed.Standard,
        overlay: MotionSpeed = MotionSpeed.Standard,
        floating: MotionSpeed = MotionSpeed.Standard,
    ) = MotionProfile(panel, overlay, floating)

    @Test
    fun `四档按家族基础值换算`() {
        assertEquals(160, profile(panel = MotionSpeed.Faster).millisOf(MotionFamily.PanelCard))
        assertEquals(200, profile(panel = MotionSpeed.Standard).millisOf(MotionFamily.PanelCard))
        assertEquals(260, profile(panel = MotionSpeed.Slower).millisOf(MotionFamily.PanelCard))
        assertEquals(340, profile(panel = MotionSpeed.Slowest).millisOf(MotionFamily.PanelCard))

        assertEquals(192, profile(panel = MotionSpeed.Faster).millisOf(MotionFamily.PanelFull))
        assertEquals(408, profile(panel = MotionSpeed.Slowest).millisOf(MotionFamily.PanelFull))

        assertEquals(120, profile(overlay = MotionSpeed.Faster).millisOf(MotionFamily.Overlay))
        assertEquals(255, profile(overlay = MotionSpeed.Slowest).millisOf(MotionFamily.Overlay))

        assertEquals(306, profile(floating = MotionSpeed.Slowest).millisOf(MotionFamily.FloatingMorph))
        assertEquals(136, profile(floating = MotionSpeed.Faster).millisOf(MotionFamily.FloatingSnap))
    }

    @Test
    fun `家族归属：面板两档共用面板倍率，浮窗三档共用浮窗倍率`() {
        val p = profile(
            panel = MotionSpeed.Slower,
            overlay = MotionSpeed.Faster,
            floating = MotionSpeed.Slowest,
        )
        assertEquals(MotionSpeed.Slower, p.speedOf(MotionFamily.PanelCard))
        assertEquals(MotionSpeed.Slower, p.speedOf(MotionFamily.PanelFull))
        assertEquals(MotionSpeed.Faster, p.speedOf(MotionFamily.Overlay))
        assertEquals(MotionSpeed.Slowest, p.speedOf(MotionFamily.Floating))
        assertEquals(MotionSpeed.Slowest, p.speedOf(MotionFamily.FloatingMorph))
        assertEquals(MotionSpeed.Slowest, p.speedOf(MotionFamily.FloatingSnap))
    }

    @Test
    fun `倍率单调且只乘一次`() {
        for (family in MotionFamily.entries) {
            val faster = profile(
                panel = MotionSpeed.Faster,
                overlay = MotionSpeed.Faster,
                floating = MotionSpeed.Faster,
            ).millisOf(family)
            val standard = profile().millisOf(family)
            val slower = profile(
                panel = MotionSpeed.Slower,
                overlay = MotionSpeed.Slower,
                floating = MotionSpeed.Slower,
            ).millisOf(family)
            val slowest = profile(
                panel = MotionSpeed.Slowest,
                overlay = MotionSpeed.Slowest,
                floating = MotionSpeed.Slowest,
            ).millisOf(family)
            assertTrue("$family: $faster < $standard", faster < standard)
            assertTrue("$family: $standard < $slower", standard < slower)
            assertTrue("$family: $slower < $slowest", slower < slowest)
            assertEquals((family.baseMillis * MotionSpeed.Standard.factor).toInt(), standard)
        }
    }

    @Test
    fun `交互反馈与编排延迟不参与档位`() {
        val bases = MotionFamily.entries.map { it.baseMillis }.toSet()
        assertFalse("确认倒计时不入家族", MotionConfirmCountdown in bases)
        assertFalse("编排延迟不是动画", ChoreographyPostHideDelay.toInt() in bases)
        assertEquals(1200, MotionConfirmCountdown)
        assertEquals(250L, ChoreographyPostHideDelay)
        assertEquals(MotionSpeed.Faster.factor, MotionSpeed.entries.first().factor)
    }
}
