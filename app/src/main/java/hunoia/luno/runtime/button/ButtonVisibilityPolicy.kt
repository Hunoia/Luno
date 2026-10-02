package hunoia.luno.runtime.button

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.condition.matches
import hunoia.luno.runtime.condition.toConditionContext

class ButtonVisibilityPolicy(
    private val initialSettings: InitialSettings,
    private val rules: List<AutomationRule>,
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
            val type = rule.effect.type
            if (type != RuleEffectType.HIDE_BUTTONS && type != RuleEffectType.SHOW_BUTTONS) continue
            if (!rule.effect.covers(button.id)) continue
            if (rule.matches(ctx)) {
                result = type == RuleEffectType.SHOW_BUTTONS
            }
        }
        return result
    }
}
