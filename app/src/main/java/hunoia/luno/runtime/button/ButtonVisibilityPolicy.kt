package hunoia.luno.runtime.button

import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.VisibilityRule
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.condition.matches
import hunoia.luno.runtime.condition.toConditionContext

class ButtonVisibilityPolicy(
    private val initialSettings: InitialSettings,
    private val rules: List<VisibilityRule>,
    private val runtimeState: GestureRuntimeState,
) {
    fun shouldShow(button: GestureButton): Boolean {
        if (!initialSettings.gestureEnabled) return false
        if ((runtimeState.hiddenGestureButtons[button.id] ?: 0L) > runtimeState.nowMs) return false
        if (!button.enabled) return false

        val ctx = runtimeState.toConditionContext()
        var result = true
        for (rule in rules) {
            if (!rule.enabled) continue
            if (!rule.covers(button.id)) continue
            if (rule.condition.matches(ctx)) {
                result = rule.effect == RuleEffect.SHOW
            }
        }
        return result
    }
}
