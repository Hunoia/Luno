package hunoia.luno.gesture

import hunoia.luno.config.model.GestureTriggerType

sealed interface GestureDecision {
    data object Noop : GestureDecision

    data object Cancel : GestureDecision

    data class Trigger(
        val triggerType: GestureTriggerType,
        val direction: Int,
        val actionDirection: Int,
    ) : GestureDecision

    data class PendingDoubleTap(
        val tapDecision: Trigger,
    ) : GestureDecision
}
