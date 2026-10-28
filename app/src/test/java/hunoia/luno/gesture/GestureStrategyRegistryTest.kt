package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GestureStrategyRegistryTest {

    private val thresholds = GestureThresholds(
        touchSlop = 10f,
        longPressDelayMs = 500L,
        doubleTapDelayMs = 300L,
        slideHoldDelayMs = 200L,
        longSlideHoldDelayMs = 200L,
        slideTriggerDistance = 100f,
        longSlideTriggerDistance = 200f,
    )

    private fun config(
        tap: Boolean = true,
        doubleTap: Boolean = false,
        longPress: Boolean = false,
    ) = GestureConfig(
        buttonId = "b1",
        angle = GestureButtonAngle(),
        thresholds = thresholds,
        isMirrorHorizontal = false,
        hasTapActions = tap,
        hasDoubleTapActions = doubleTap,
        hasLongPressActions = longPress,
        hasActionInDirection = { _, _ -> true },
    )

    private fun trigger(decision: GestureDecision): GestureDecision.Trigger {
        return when (decision) {
            is GestureDecision.Trigger -> decision
            is GestureDecision.PendingDoubleTap -> decision.tapDecision
            else -> throw AssertionError("expected trigger, got $decision")
        }
    }

    @Test
    fun `tap is recognized`() {
        val recognizer = GestureRecognizer()
        val cfg = config()
        recognizer.onDown(0L, Offset.Zero, cfg)
        assertEquals(GestureTriggerType.Tap, trigger(recognizer.onUp(50L, cfg)).triggerType)
    }

    @Test
    fun `double tap is recognized`() {
        val recognizer = GestureRecognizer()
        val cfg = config(doubleTap = true)
        recognizer.onDown(0L, Offset.Zero, cfg)
        assertTrue(recognizer.onUp(50L, cfg) is GestureDecision.PendingDoubleTap)
        recognizer.onDown(100L, Offset.Zero, cfg)
        assertEquals(GestureTriggerType.DoubleTap, trigger(recognizer.onUp(150L, cfg)).triggerType)
    }

    @Test
    fun `slide and long slide are recognized`() {
        val cfg = config()
        val slide = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, cfg)
            onMove(10L, Offset(150f, 0f), cfg)
        }
        assertEquals(GestureTriggerType.Slide, trigger(slide.onUp(20L, cfg)).triggerType)

        val longSlide = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, cfg)
            onMove(10L, Offset(250f, 0f), cfg)
        }
        assertEquals(GestureTriggerType.LongSlide, trigger(longSlide.onUp(20L, cfg)).triggerType)
    }

    @Test
    fun `long press is recognized on tick`() {
        val recognizer = GestureRecognizer()
        val cfg = config(longPress = true)
        recognizer.onDown(0L, Offset.Zero, cfg)
        assertNotEquals(GestureDecision.Noop, recognizer.checkTime(500L, cfg))
    }

    @Test
    fun `slide hold and long slide hold are recognized on move`() {
        val cfg = config()
        val slideHold = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, cfg)
            onMove(10L, Offset(150f, 0f), cfg)
        }
        assertEquals(
            GestureTriggerType.SlideHold,
            trigger(slideHold.onMove(250L, Offset.Zero, cfg)).triggerType,
        )

        val longSlideHold = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, cfg)
            onMove(10L, Offset(250f, 0f), cfg)
        }
        assertEquals(
            GestureTriggerType.LongSlideHold,
            trigger(longSlideHold.onMove(250L, Offset.Zero, cfg)).triggerType,
        )
    }

    @Test
    fun `duplicate strategy id fails fast`() {
        try {
            GestureStrategyRegistry.register(
                "slide",
                object : GestureStrategy {},
            )
            fail("重复策略 id 应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
