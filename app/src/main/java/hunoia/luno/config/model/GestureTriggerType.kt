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

val GestureTriggerType.isHoldType: Boolean
    get() = this == GestureTriggerType.LongPress ||
        this == GestureTriggerType.SlideHold ||
        this == GestureTriggerType.LongSlideHold

val GestureTriggerType.isLongSlideType: Boolean
    get() = this == GestureTriggerType.LongSlide || this == GestureTriggerType.LongSlideHold
