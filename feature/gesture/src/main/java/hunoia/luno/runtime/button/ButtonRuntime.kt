package hunoia.luno.runtime.button

import android.os.SystemClock
import android.util.Log
import hunoia.luno.gesture.BuildConfig
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hunoia.luno.core.LOG_TAG

class ButtonHideRuntime(
    private val scope: CoroutineScope,
    private val onStateChanged: () -> Unit,
) {
    private val hiddenButtons = mutableMapOf<String, Long>()

    /** 批量临时隐藏：到期自动恢复；期间重复 hide 会刷新到期时间。 */
    fun hideTemporarily(buttonIds: Collection<String>, delayMs: Long) {
        if (buttonIds.isEmpty()) return
        val deadline = SystemClock.uptimeMillis() + delayMs
        buttonIds.forEach { hiddenButtons[it] = deadline }
        onStateChanged()
        scope.launch {
            delay(delayMs)
            val expired = buttonIds.filter { (hiddenButtons[it] ?: 0L) <= SystemClock.uptimeMillis() }
            if (expired.isNotEmpty()) {
                expired.forEach(hiddenButtons::remove)
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
            runtimeState = runtimeState,
        )
        buttonWindowController.updateVisibility(policy)
        if (BuildConfig.DEBUG) {
            val visibleCount = buttonWindowController.buttonViews.count { view ->
                val target = view.tag as? hunoia.luno.runtime.overlay.GestureButtonWindowTarget ?: return@count false
                policy.shouldShow(target.sourceButton)
            }
            Log.i(LOG_TAG, "button refresh: visible=$visibleCount total=${buttonWindowController.buttonViews.size}")
        }
    }
}

class ButtonVisibilityPolicy(
    private val initialSettings: InitialSettings,
    private val runtimeState: GestureRuntimeState,
) {
    fun shouldShow(button: GestureButton): Boolean {
        if (!initialSettings.gestureEnabled) return false
        if ((runtimeState.hiddenGestureButtons[button.id] ?: 0L) > runtimeState.nowMs) return false
        // 用户基线：场景配置只能相对它隐藏 / 恢复，不能把手动关掉的钮重新打开。
        if (!button.enabled) return false
        // 规则列表顺序 = 优先级：最后命中的一条生效；target 不覆盖本钮的配置不改状态。
        var visible = true
        initialSettings.buttonVisibility.profiles.forEach { profile ->
            if (profile.enabled && profile.matches(runtimeState) && profile.affects(button.id)) {
                visible = profile.visible
            }
        }
        return visible
    }
}

private fun VisibilityProfile.matches(state: GestureRuntimeState): Boolean {
    if (packageNames.isNotEmpty() && state.currentPackageName !in packageNames) return false
    if (screens.isNotEmpty() && screens.none { it.matches(state) }) return false
    return true
}

private fun VisibilityScreen.matches(state: GestureRuntimeState): Boolean = when (this) {
    VisibilityScreen.LOCK_SCREEN -> state.isNowInLockScreenPage
    VisibilityScreen.LAUNCHER -> state.isInLauncher
    VisibilityScreen.LANDSCAPE -> state.isLandscape
    VisibilityScreen.PORTRAIT -> !state.isLandscape
    VisibilityScreen.KEYBOARD_INPUT -> state.isKeyboardInputActive
}

private fun VisibilityProfile.affects(buttonId: String): Boolean = when (target) {
    hunoia.luno.action.model.ButtonTarget.ALL -> true
    hunoia.luno.action.model.ButtonTarget.ONLY -> buttonId in buttonIds
    hunoia.luno.action.model.ButtonTarget.EXCEPT -> buttonId !in buttonIds
}
