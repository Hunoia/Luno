package hunoia.luno.runtime.action

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.dispatcher.NewActionDispatcher
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.config.model.actionLibraryRefId
import hunoia.luno.config.model.newActionData
import hunoia.luno.runtime.GestureHost
import kotlinx.coroutines.CoroutineScope

class ActionDispatcher(
    private val host: GestureHost,
    private val scope: CoroutineScope,
    private val previousAppTracker: PreviousAppTracker,
    private val settingsSnapshot: () -> SettingsSnapshot,
    private val onToggleQuickAppLauncher: () -> Unit,
    private val onShowVolumeScrub: (VolumeScrubConfig) -> Boolean,
    private val onHideGestureButton: (GestureButton?, Long) -> Unit,
) {
    private val newDispatcher = NewActionDispatcher(
        host = host,
        scope = scope,
        previousAppTracker = previousAppTracker,
        settingsSnapshot = {
            val s = settingsSnapshot()
            hunoia.luno.action.dispatcher.SettingsSnapshot(
                actionSettings = s.actionSettings,
                advancedSettings = s.advancedSettings,
                gestureSettings = s.gestureSettings,
            )
        },
        onToggleQuickAppLauncher = onToggleQuickAppLauncher,
        onShowVolumeScrub = onShowVolumeScrub,
        onHideGestureButton = onHideGestureButton,
    )

    fun onAction(
        action: Action,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride? = sourceButton?.actionSettingsOverride,
    ) {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "dispatch action id=${action.value}")

        val touchPosition = action.extra.toTouchPair()

        action.newActionData()?.let { stored ->
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "new action typeId=${stored.typeId}")
            newDispatcher.dispatch(stored, sourceButton, sourceOverride, touchPosition)
            return
        }

        val entryId = action.actionLibraryRefId()
        if (entryId != null && dispatchEntry(entryId, sourceButton, sourceOverride, touchPosition)) {
            return
        }

        if (action.value == ActionFacade.BACK) {
            host.accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        val mapped = hunoia.luno.action.dispatcher.LegacyActionMapper.map(action)
        if (mapped != null) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "legacy mapped typeId=${mapped.typeId}")
            newDispatcher.dispatch(mapped, sourceButton, sourceOverride, touchPosition)
            return
        }

        if (BuildConfig.DEBUG) Log.w("LunoLauncher", "no executor for legacy action id=${action.value}")
    }

    private fun dispatchEntry(
        entryId: String,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride?,
        touchPosition: Pair<Int, Int>?,
    ): Boolean {
        val entry = settingsSnapshot().newActionLibrarySettings.entries.find { it.id == entryId }
        if (entry == null) {
            if (BuildConfig.DEBUG) Log.w("LunoLauncher", "entry not found id=$entryId")
            return false
        }
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "dispatch entry id=$entryId typeId=${entry.storedAction.typeId}")
        newDispatcher.dispatch(entry.storedAction, sourceButton, sourceOverride, touchPosition)
        return true
    }

    fun runActionEntry(entryId: String) {
        dispatchEntry(entryId, null, null, null)
    }

    private fun Any?.toTouchPair(): Pair<Int, Int>? {
        val list = this as? List<*> ?: return null
        val x = (list.getOrNull(0) as? Number)?.toInt() ?: return null
        val y = (list.getOrNull(1) as? Number)?.toInt() ?: return null
        return x to y
    }
}

data class SettingsSnapshot(
    val actionSettings: ActionSettings,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: GestureSettings,
    val newActionLibrarySettings: NewActionLibrarySettings = NewActionLibrarySettings(),
)
