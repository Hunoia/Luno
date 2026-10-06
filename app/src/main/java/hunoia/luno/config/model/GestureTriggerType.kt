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

/** 非停留的滑动（短滑/长滑）。按住类不算 —— 它们的"松手即结束"语义要保留。 */
val GestureTriggerType.isPlainSlideType: Boolean
    get() = this == GestureTriggerType.Slide || this == GestureTriggerType.LongSlide

val GestureTriggerType.isLongSlideType: Boolean
    get() = this == GestureTriggerType.LongSlide || this == GestureTriggerType.LongSlideHold
