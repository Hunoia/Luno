package hunoia.luno.runtime.button

import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.runtime.GestureRuntimeState

class ButtonVisibilityPolicy(
    private val initialSettings: InitialSettings,
    private val advancedSettings: AdvancedSettings,
    private val runtimeState: GestureRuntimeState,
) {
    fun shouldShow(button: GestureButton): Boolean {
        return initialSettings.gestureEnabled &&
            (runtimeState.hiddenGestureButtons[button.id] ?: 0L) <= runtimeState.nowMs &&
            !runtimeState.isMouseMode &&
            !(button.fitSoftKeyboard && runtimeState.isKeyboardInputActive) &&
            !(button.hideLandscape && runtimeState.isLandscape) &&
            !(button.hideHomeScreen && runtimeState.isInLauncher) &&
            !(button.hideScreenLock && runtimeState.isNowInLockScreenPage) &&
            runtimeState.currentPackageName !in advancedSettings.excludeApps &&
            button.enabled
    }
}
