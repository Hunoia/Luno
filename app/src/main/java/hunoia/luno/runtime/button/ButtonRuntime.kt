package hunoia.luno.runtime.button

import android.os.SystemClock
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.condition.matches
import hunoia.luno.runtime.condition.toConditionContext
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ButtonHideRuntime(
    private val scope: CoroutineScope,
    private val onStateChanged: () -> Unit,
) {
    private val hiddenButtons = mutableMapOf<String, Long>()

    fun hideTemporarily(button: GestureButton, delayMs: Long) {
        val key = button.id
        hiddenButtons[key] = SystemClock.uptimeMillis() + delayMs
        onStateChanged()
        scope.launch {
            delay(delayMs)
            if ((hiddenButtons[key] ?: 0L) <= SystemClock.uptimeMillis()) {
                hiddenButtons.remove(key)
                onStateChanged()
            }
        }
    }

    fun getSnapshot(): Map<String, Long> = hiddenButtons.toMap()
}

class ButtonRefreshCoordinator(
    private val runtimeSettingsStore: SettingsStore,
    private val buttonWindowController: ButtonWindowController,
) {
    fun refresh(runtimeState: GestureRuntimeState) {
        val settings = runtimeSettingsStore.snapshot()
        val policy = ButtonVisibilityPolicy(
            initialSettings = settings.initialSettings,
            rules = settings.automationRules,
            runtimeState = runtimeState,
        )
        buttonWindowController.updateVisibility(policy)
        if (BuildConfig.DEBUG) {
            val visibleCount = buttonWindowController.buttonViews.count { view ->
                val target = view.tag as? hunoia.luno.runtime.overlay.GestureButtonWindowTarget ?: return@count false
                policy.shouldShow(target.sourceButton)
            }
            Log.i("LunoLauncher", "button refresh: visible=$visibleCount total=${buttonWindowController.buttonViews.size}")
        }
    }
}

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
