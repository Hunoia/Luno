package hunoia.luno.runtime.button

import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.settings.SettingsStore

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
