package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureRecognizerTest {

    private lateinit var recognizer: GestureRecognizer
    private val thresholds = GestureThresholds(
        touchSlop = 10f,
        longPressDelayMs = 250L,
        doubleTapDelayMs = 300L,
        slideHoldDelayMs = 120L,
        longSlideHoldDelayMs = 120L,
        slideTriggerDistance = 30f,
        longSlideTriggerDistance = 100f,
    )
    private val defaultAngle = GestureButtonAngle()

    @Before
    fun setup() {
        recognizer = GestureRecognizer()
    }

    @Test
    fun `tap on up triggers Tap when within touch slop`() {
        val config = config(hasTap = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(1f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertTrigger(decision, GestureTriggerType.Tap, GestureDirection.Right)
    }

    @Test
    fun `double tap within threshold triggers DoubleTap`() {
        val config = config(hasTap = true, hasDoubleTap = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        val firstResult = recognizer.onUp(10, config)
        assertEquals(GestureDecision.PendingDoubleTap::class, firstResult::class)

        recognizer.onDown(20, Offset(105f, 205f), config)
        recognizer.onMove(25, Offset(1f, 1f), config)
        val secondResult = recognizer.onUp(30, config)
        assertTrigger(secondResult, GestureTriggerType.DoubleTap, GestureDirection.Right)
    }

    @Test
    fun `double tap timeout fallback returns fresh pending double tap`() {
        val config = config(hasTap = true, hasDoubleTap = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onUp(10, config)

        recognizer.clearPendingDoubleTap()

        recognizer.onDown(500, Offset(100f, 200f), config)
        val decision = recognizer.onUp(510, config)
        assertEquals(
            "After timeout, next tap is fresh PendingDoubleTap",
            GestureDecision.PendingDoubleTap::class,
            decision::class,
        )
    }

    @Test
    fun `double tap second touch beyond slop cancels double tap`() {
        val config = config(hasTap = true, hasDoubleTap = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onUp(10, config)

        recognizer.onDown(20, Offset(200f, 200f), config)
        recognizer.onMove(30, Offset(50f, 0f), config)
        val decision = recognizer.onUp(40, config)
        assertTrue("Expected Noop when double tap second touch slides", decision is GestureDecision.Noop)
    }

    @Test
    fun `long press triggers immediately after delay`() {
        val config = config(hasLongPress = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        val decision = recognizer.checkTime(300, config)
        assertTrigger(decision, GestureTriggerType.LongPress, GestureDirection.Right)
    }

    @Test
    fun `long press does not trigger before delay`() {
        val config = config(hasLongPress = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        val decision = recognizer.checkTime(100, config)
        assertEquals(GestureDecision.Noop::class, decision::class)
    }

    @Test
    fun `long press canceled by movement past touch slop`() {
        val config = config(hasLongPress = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(20f, 0f), config)
        val decision = recognizer.checkTime(300, config)
        assertEquals(GestureDecision.Noop::class, decision::class)
    }

    @Test
    fun `long press triggered then up does not double trigger`() {
        val config = config(hasLongPress = true, hasTap = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        val longPress = recognizer.checkTime(300, config)
        assertTrigger(longPress, GestureTriggerType.LongPress, GestureDirection.Right)

        val upDecision = recognizer.onUp(310, config)
        assertEquals(GestureDecision.Noop::class, upDecision::class)
    }

    @Test
    fun `slide triggers on up when distance exceeded`() {
        val config = config(hasSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(40f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertTrigger(decision, GestureTriggerType.Slide, GestureDirection.Right)
    }

    @Test
    fun `long slide triggers on up when long distance exceeded`() {
        val config = config(hasLongSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(150f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertTrigger(decision, GestureTriggerType.LongSlide, GestureDirection.Right)
    }

    @Test
    fun `slide hold triggers during move after hold delay`() {
        val config = config(hasSlideHold = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(5, Offset(40f, 0f), config)
        val decision = recognizer.onMove(150, Offset(0f, 0f), config)
        assertTrigger(decision, GestureTriggerType.SlideHold, GestureDirection.Right)
    }

    @Test
    fun `slide hold triggered then up does not double trigger`() {
        val config = config(hasSlideHold = true, hasTap = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(5, Offset(40f, 0f), config)
        val hold = recognizer.onMove(150, Offset(0f, 0f), config)
        assertTrigger(hold, GestureTriggerType.SlideHold, GestureDirection.Right)

        val upDecision = recognizer.onUp(160, config)
        assertEquals(GestureDecision.Noop::class, upDecision::class)
    }

    @Test
    fun `long slide hold triggers during move after hold delay`() {
        val config = config(hasLongSlideHold = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(5, Offset(150f, 0f), config)
        val decision = recognizer.onMove(150, Offset(0f, 0f), config)
        assertTrigger(decision, GestureTriggerType.LongSlideHold, GestureDirection.Right)
    }

    @Test
    fun `cancel resets state`() {
        val config = config(hasLongPress = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onCancel()

        val origin = recognizer.origin
        assertEquals(Offset.Unspecified, origin)
    }

    @Test
    fun `new down does not inherit previous gesture state`() {
        val config = config(hasLongPress = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(5, Offset(10f, 0f), config)
        recognizer.onUp(10, config)

        recognizer.onDown(20, Offset(300f, 400f), config)
        assertEquals(Offset(300f, 400f), recognizer.origin)
        assertEquals(Offset(300f, 400f), recognizer.finger)
    }

    @Test
    fun `direction is calculated correctly for right slide`() {
        val config = config(hasSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(100f, 0f), config)
        assertEquals(GestureDirection.Right, recognizer.triggerDirection)
        assertEquals(GestureDirection.Right, recognizer.actionDirection)
    }

    @Test
    fun `direction is calculated correctly for up slide`() {
        val config = config(hasSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(0f, -150f), config)
        assertEquals(GestureDirection.Up, recognizer.triggerDirection)
    }

    @Test
    fun `mirror keeps physical trigger direction and mirrors action direction`() {
        val config = config(hasSlide = true, isMirrorHorizontal = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(100f, 0f), config)

        assertEquals(GestureDirection.Right, recognizer.triggerDirection)
        assertEquals(GestureDirection.Left, recognizer.actionDirection)
    }

    @Test
    fun `slide at exact trigger distance triggers`() {
        val config = config(hasSlide = true,
            thresholds = thresholds.copy(slideTriggerDistance = 50f))
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(50f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertTrigger(decision, GestureTriggerType.Slide, GestureDirection.Right)
    }

    @Test
    fun `slide just below trigger distance does not trigger`() {
        val config = config(hasSlide = true,
            thresholds = thresholds.copy(slideTriggerDistance = 50f))
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(49f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertEquals(GestureDecision.Noop::class, decision::class)
    }

    @Test
    fun `no action configured for direction does not trigger`() {
        val config = config(hasSlide = false)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(100f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertEquals(GestureDecision.Noop::class, decision::class)
    }

    @Test
    fun `long slide hold without actions does not trigger`() {
        val config = config(hasLongSlideHold = false, hasSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(5, Offset(150f, 0f), config)
        val decision = recognizer.onMove(150, Offset(0f, 0f), config)
        assertEquals(GestureDecision.Noop::class, decision::class)
    }

    @Test
    fun `long slide triggers when long slide actions exist and slide actions also exist`() {
        val config = config(hasSlide = true, hasLongSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(150f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertTrigger(decision, GestureTriggerType.LongSlide, GestureDirection.Right)
    }

    @Test
    fun `slide triggers when only slide actions exist at slide distance`() {
        val config = config(hasSlide = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(10, Offset(40f, 0f), config)
        val decision = recognizer.onUp(20, config)
        assertTrigger(decision, GestureTriggerType.Slide, GestureDirection.Right)
    }

    @Test
    fun `checkTime does not trigger long press after directTriggered`() {
        val config = config(hasLongPress = true, hasSlideHold = true)
        recognizer.onDown(0, Offset(100f, 200f), config)
        recognizer.onMove(5, Offset(40f, 0f), config)
        recognizer.onMove(150, Offset(0f, 0f), config)
        val holdDecision = recognizer.checkTime(160, config)
        assertEquals(GestureDecision.Noop::class, holdDecision::class)
    }

    @Test
    fun `tap without double tap returns Trigger immediately`() {
        val config = config(hasTap = true, hasDoubleTap = false)
        recognizer.onDown(0, Offset(100f, 200f), config)
        val decision = recognizer.onUp(10, config)
        assertTrigger(decision, GestureTriggerType.Tap, GestureDirection.Right)
    }

    @Test
    fun `pending double tap cleared by different button down`() {
        val config1 = config(buttonId = "btn1", hasTap = true, hasDoubleTap = true)
        val config2 = config(buttonId = "btn2")
        recognizer.onDown(0, Offset(100f, 200f), config1)
        recognizer.onUp(10, config1)

        recognizer.onDown(20, Offset(300f, 400f), config2)
        val decision = recognizer.onUp(30, config2)
        assertEquals(GestureDecision.Noop::class, decision::class)
    }

    private fun config(
        buttonId: String = "test",
        hasTap: Boolean = false,
        hasDoubleTap: Boolean = false,
        hasLongPress: Boolean = false,
        hasSlide: Boolean = false,
        hasSlideHold: Boolean = false,
        hasLongSlide: Boolean = false,
        hasLongSlideHold: Boolean = false,
        thresholds: GestureThresholds = this.thresholds,
        isMirrorHorizontal: Boolean = false,
    ): GestureConfig = GestureConfig(
        buttonId = buttonId,
        angle = defaultAngle,
        thresholds = thresholds,
        isMirrorHorizontal = isMirrorHorizontal,
        hasTapActions = hasTap,
        hasDoubleTapActions = hasDoubleTap,
        hasLongPressActions = hasLongPress,
        hasActionInDirection = { type, direction ->
            when (type) {
                GestureTriggerType.Slide -> hasSlide
                GestureTriggerType.SlideHold -> hasSlideHold
                GestureTriggerType.LongSlide -> hasLongSlide
                GestureTriggerType.LongSlideHold -> hasLongSlideHold
                else -> false
            }
        },
    )

    private fun assertTrigger(
        decision: GestureDecision,
        expectedType: GestureTriggerType,
        expectedDirection: GestureDirection,
    ) {
        assertTrue("Expected Trigger, got ${decision::class.simpleName}", decision is GestureDecision.Trigger)
        val trigger = decision as GestureDecision.Trigger
        assertEquals(expectedType, trigger.triggerType)
        assertEquals(expectedDirection, trigger.actionDirection)
    }
}
