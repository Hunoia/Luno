package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class GestureTriggerType {
    Tap,
    DoubleTap,
    LongPress,
    Slide,
    SlideHold,
    LongSlide,
    LongSlideHold,
}

val GestureTriggerType.isSlideType: Boolean
    get() = this == GestureTriggerType.Slide ||
        this == GestureTriggerType.SlideHold ||
        this == GestureTriggerType.LongSlide ||
        this == GestureTriggerType.LongSlideHold

val GestureTriggerType.isHoldType: Boolean
    get() = this == GestureTriggerType.LongPress ||
        this == GestureTriggerType.SlideHold ||
        this == GestureTriggerType.LongSlideHold

val GestureTriggerType.isLongSlideType: Boolean
    get() = this == GestureTriggerType.LongSlide || this == GestureTriggerType.LongSlideHold

val GestureTriggerType.isTapType: Boolean
    get() = this == GestureTriggerType.Tap || this == GestureTriggerType.DoubleTap

val GestureTriggerType.isDoubleTapType: Boolean
    get() = this == GestureTriggerType.DoubleTap

val GestureTriggerType.isTriggerImmediately: Boolean
    get() = this == GestureTriggerType.LongPress ||
        this == GestureTriggerType.SlideHold ||
        this == GestureTriggerType.LongSlideHold

val GestureTriggerType.isTriggerOnRelease: Boolean
    get() = this == GestureTriggerType.Tap ||
        this == GestureTriggerType.DoubleTap ||
        this == GestureTriggerType.Slide ||
        this == GestureTriggerType.LongSlide

val GestureTriggerType.requiresDirection: Boolean
    get() = this == GestureTriggerType.Slide ||
        this == GestureTriggerType.SlideHold ||
        this == GestureTriggerType.LongSlide ||
        this == GestureTriggerType.LongSlideHold
