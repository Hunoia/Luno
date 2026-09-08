package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureTriggerTypeTest {

    @Test
    fun tap_classification() {
        assertEquals(false, GestureTriggerType.Tap.isSlideType)
        assertEquals(false, GestureTriggerType.Tap.isHoldType)
        assertEquals(false, GestureTriggerType.Tap.isLongSlideType)
        assertEquals(true, GestureTriggerType.Tap.isTapType)
        assertEquals(false, GestureTriggerType.Tap.isDoubleTapType)
        assertEquals(false, GestureTriggerType.Tap.isTriggerImmediately)
        assertEquals(true, GestureTriggerType.Tap.isTriggerOnRelease)
        assertEquals(false, GestureTriggerType.Tap.requiresDirection)
    }

    @Test
    fun doubleTap_classification() {
        assertEquals(false, GestureTriggerType.DoubleTap.isSlideType)
        assertEquals(false, GestureTriggerType.DoubleTap.isHoldType)
        assertEquals(false, GestureTriggerType.DoubleTap.isLongSlideType)
        assertEquals(true, GestureTriggerType.DoubleTap.isTapType)
        assertEquals(true, GestureTriggerType.DoubleTap.isDoubleTapType)
        assertEquals(false, GestureTriggerType.DoubleTap.isTriggerImmediately)
        assertEquals(true, GestureTriggerType.DoubleTap.isTriggerOnRelease)
        assertEquals(false, GestureTriggerType.DoubleTap.requiresDirection)
    }

    @Test
    fun longPress_classification() {
        assertEquals(false, GestureTriggerType.LongPress.isSlideType)
        assertEquals(true, GestureTriggerType.LongPress.isHoldType)
        assertEquals(false, GestureTriggerType.LongPress.isLongSlideType)
        assertEquals(false, GestureTriggerType.LongPress.isTapType)
        assertEquals(false, GestureTriggerType.LongPress.isDoubleTapType)
        assertEquals(true, GestureTriggerType.LongPress.isTriggerImmediately)
        assertEquals(false, GestureTriggerType.LongPress.isTriggerOnRelease)
        assertEquals(false, GestureTriggerType.LongPress.requiresDirection)
    }

    @Test
    fun slide_classification() {
        assertEquals(true, GestureTriggerType.Slide.isSlideType)
        assertEquals(false, GestureTriggerType.Slide.isHoldType)
        assertEquals(false, GestureTriggerType.Slide.isLongSlideType)
        assertEquals(false, GestureTriggerType.Slide.isTapType)
        assertEquals(false, GestureTriggerType.Slide.isDoubleTapType)
        assertEquals(false, GestureTriggerType.Slide.isTriggerImmediately)
        assertEquals(true, GestureTriggerType.Slide.isTriggerOnRelease)
        assertEquals(true, GestureTriggerType.Slide.requiresDirection)
    }

    @Test
    fun slideHold_classification() {
        assertEquals(true, GestureTriggerType.SlideHold.isSlideType)
        assertEquals(true, GestureTriggerType.SlideHold.isHoldType)
        assertEquals(false, GestureTriggerType.SlideHold.isLongSlideType)
        assertEquals(false, GestureTriggerType.SlideHold.isTapType)
        assertEquals(false, GestureTriggerType.SlideHold.isDoubleTapType)
        assertEquals(true, GestureTriggerType.SlideHold.isTriggerImmediately)
        assertEquals(false, GestureTriggerType.SlideHold.isTriggerOnRelease)
        assertEquals(true, GestureTriggerType.SlideHold.requiresDirection)
    }

    @Test
    fun longSlide_classification() {
        assertEquals(true, GestureTriggerType.LongSlide.isSlideType)
        assertEquals(false, GestureTriggerType.LongSlide.isHoldType)
        assertEquals(true, GestureTriggerType.LongSlide.isLongSlideType)
        assertEquals(false, GestureTriggerType.LongSlide.isTapType)
        assertEquals(false, GestureTriggerType.LongSlide.isDoubleTapType)
        assertEquals(false, GestureTriggerType.LongSlide.isTriggerImmediately)
        assertEquals(true, GestureTriggerType.LongSlide.isTriggerOnRelease)
        assertEquals(true, GestureTriggerType.LongSlide.requiresDirection)
    }

    @Test
    fun longSlideHold_classification() {
        assertEquals(true, GestureTriggerType.LongSlideHold.isSlideType)
        assertEquals(true, GestureTriggerType.LongSlideHold.isHoldType)
        assertEquals(true, GestureTriggerType.LongSlideHold.isLongSlideType)
        assertEquals(false, GestureTriggerType.LongSlideHold.isTapType)
        assertEquals(false, GestureTriggerType.LongSlideHold.isDoubleTapType)
        assertEquals(true, GestureTriggerType.LongSlideHold.isTriggerImmediately)
        assertEquals(false, GestureTriggerType.LongSlideHold.isTriggerOnRelease)
        assertEquals(true, GestureTriggerType.LongSlideHold.requiresDirection)
    }

    @Test
    fun totalEnumCount_7() {
        assertEquals(7, GestureTriggerType.entries.size)
    }
}
