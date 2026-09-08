package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerSemanticsTest {

    private val thresholds = GestureThresholds(
        touchSlop = 10f,
        longPressDelayMs = 250L,
        doubleTapDelayMs = 300L,
        slideHoldDelayMs = 120L,
        longSlideHoldDelayMs = 120L,
        slideTriggerDistance = 30f,
        longSlideTriggerDistance = 100f,
    )

    @Test
    fun `touch slop uses euclidean distance for long press cancellation`() {
        val recognizer = GestureRecognizer()
        val config = config(hasLongPress = true)

        recognizer.onDown(0, Offset(100f, 100f), config)
        recognizer.onMove(10, Offset(8f, 8f), config)

        assertEquals(GestureDecision.Noop::class, recognizer.checkTime(300, config)::class)
    }

    @Test
    fun `movement beyond touch slop exits tap and double tap semantics`() {
        val recognizer = GestureRecognizer()
        val config = config(hasTap = true, hasDoubleTap = true)

        recognizer.onDown(0, Offset(100f, 100f), config)
        recognizer.onMove(10, Offset(8f, 8f), config)

        assertEquals(GestureDecision.Noop::class, recognizer.onUp(20, config)::class)
    }

    @Test
    fun `same button second touch beyond slop consumes pending tap without restoring it`() {
        val recognizer = GestureRecognizer()
        val config = config(hasTap = true, hasDoubleTap = true)

        recognizer.onDown(0, Offset(100f, 100f), config)
        assertEquals(GestureDecision.PendingDoubleTap::class, recognizer.onUp(10, config)::class)

        recognizer.onDown(20, Offset(100f, 100f), config)
        recognizer.onMove(30, Offset(8f, 8f), config)

        assertEquals(GestureDecision.Noop::class, recognizer.onUp(40, config)::class)
    }

    @Test
    fun `only double tap enters pending and timeout clear prevents fallback trigger`() {
        val recognizer = GestureRecognizer()
        val config = config(hasDoubleTap = true)

        recognizer.onDown(0, Offset(100f, 100f), config)
        assertEquals(GestureDecision.PendingDoubleTap::class, recognizer.onUp(10, config)::class)

        recognizer.clearPendingDoubleTap()
        recognizer.onDown(500, Offset(100f, 100f), config)

        assertEquals(GestureDecision.PendingDoubleTap::class, recognizer.onUp(510, config)::class)
    }

    @Test
    fun `long slide hold triggered then up does not trigger long slide`() {
        val recognizer = GestureRecognizer()
        val config = config(hasLongSlide = true, hasLongSlideHold = true)

        recognizer.onDown(0, Offset(100f, 100f), config)
        recognizer.onMove(5, Offset(120f, 0f), config)
        assertTrigger(recognizer.onMove(150, Offset.Zero, config), GestureTriggerType.LongSlideHold)

        assertEquals(GestureDecision.Noop::class, recognizer.onUp(160, config)::class)
    }

    private fun config(
        hasTap: Boolean = false,
        hasDoubleTap: Boolean = false,
        hasLongPress: Boolean = false,
        hasLongSlide: Boolean = false,
        hasLongSlideHold: Boolean = false,
    ): GestureConfig = GestureConfig(
        buttonId = "button",
        angle = GestureButtonAngle(),
        thresholds = thresholds,
        isMirrorHorizontal = false,
        hasTapActions = hasTap,
        hasDoubleTapActions = hasDoubleTap,
        hasLongPressActions = hasLongPress,
        hasActionInDirection = { type, _ ->
            when (type) {
                GestureTriggerType.LongSlide -> hasLongSlide
                GestureTriggerType.LongSlideHold -> hasLongSlideHold
                else -> false
            }
        },
    )

    private fun assertTrigger(decision: GestureDecision, triggerType: GestureTriggerType) {
        assertTrue("Expected Trigger, got ${decision::class.simpleName}", decision is GestureDecision.Trigger)
        val trigger = decision as GestureDecision.Trigger
        assertEquals(triggerType, trigger.triggerType)
        assertEquals(GestureDirection.Right, trigger.actionDirection)
    }
}
