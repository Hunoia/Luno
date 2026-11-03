package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.evenBoundaries
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
        angle: GestureButtonAngle = GestureButtonAngle(),
        mirrored: Boolean = false,
    ) = GestureConfig(
        buttonId = "b1",
        angle = angle,
        thresholds = thresholds,
        isMirrorHorizontal = mirrored,
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
    fun `cancel clears pending double tap`() {
        // 回归 G-03：待定双击在 CANCEL 后必须失效，否则下一次单击会被判成双击。
        val recognizer = GestureRecognizer()
        val cfg = config(doubleTap = true)
        recognizer.onDown(0L, Offset.Zero, cfg)
        assertTrue(recognizer.onUp(50L, cfg) is GestureDecision.PendingDoubleTap)

        recognizer.onCancel()

        recognizer.onDown(10_000L, Offset.Zero, cfg)
        assertTrue(recognizer.onUp(10_050L, cfg) is GestureDecision.PendingDoubleTap)
    }

    @Test
    fun `eight way slot and horizontal mirror drive the action slot`() {
        val angle = GestureButtonAngle(evenBoundaries(8))
        // 22.5°（右上）落在槽 7（[0°,45°)）；镜像后 157.5°（左上）落在槽 2（[135°,180°)）。
        val offset = Offset(100f, -41.42f)
        val plain = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, config(angle = angle))
            onMove(10L, offset, config(angle = angle))
        }
        val plainDecision = trigger(plain.onUp(20L, config(angle = angle)))
        assertEquals(7, plainDecision.direction)
        assertEquals(7, plainDecision.actionDirection)

        val mirroredCfg = config(angle = angle, mirrored = true)
        val mirrored = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, mirroredCfg)
            onMove(10L, offset, mirroredCfg)
        }
        val mirroredDecision = trigger(mirrored.onUp(20L, mirroredCfg))
        assertEquals(7, mirroredDecision.direction)
        assertEquals(2, mirroredDecision.actionDirection)
    }

    @Test
    fun `single direction maps every slide to slot zero`() {
        val angle = GestureButtonAngle(evenBoundaries(1))
        val cfg = config(angle = angle)
        val recognizer = GestureRecognizer().apply {
            onDown(0L, Offset.Zero, cfg)
            onMove(10L, Offset(-80f, 120f), cfg)
        }
        val decision = trigger(recognizer.onUp(20L, cfg))
        assertEquals(0, decision.direction)
        assertEquals(0, decision.actionDirection)
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
