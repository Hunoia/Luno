package hunoia.luno.runtime.action

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.content.Context
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.api.ActionRegistry
import hunoia.luno.action.dispatcher.NewActionDispatcher
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibrarySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.effectiveFor
import hunoia.luno.config.model.actionLibraryRefId
import hunoia.luno.bridge.feedback.showToast as showToastUtil
import hunoia.luno.bridge.feedback.showToastLong as showToastLongUtil
import hunoia.luno.bridge.feedback.showVersionTooLowToast as showVersionTooLowToastUtil
import hunoia.luno.runtime.GestureHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionDispatcher(
    private val host: GestureHost,
    private val scope: CoroutineScope,
    private val previousAppTracker: PreviousAppTracker,
    private val keepScreenOnController: KeepScreenOnController,
    private val settingsSnapshot: () -> SettingsSnapshot,
    private val onToggleQuickAppLauncher: () -> Unit,
    private val onShowVolumeScrub: () -> Boolean,
    private val onHideGestureButton: (GestureButton?, Long) -> Unit,
) {
    private val newDispatcher = NewActionDispatcher(
        host = host,
        scope = scope,
        previousAppTracker = previousAppTracker,
        keepScreenOnController = keepScreenOnController,
        settingsSnapshot = {
            hunoia.luno.action.dispatcher.SettingsSnapshot(
                actionSettings = settingsSnapshot().actionSettings,
                advancedSettings = settingsSnapshot().advancedSettings,
                gestureSettings = settingsSnapshot().gestureSettings,
                actionLibrarySettings = settingsSnapshot().actionLibrarySettings,
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
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "dispatch action id=${action.value}")

        val entryId = action.actionLibraryRefId()
        if (entryId != null) {
            val newSettings = settingsSnapshot().newActionLibrarySettings
            val newEntry = newSettings.entries.find { it.id == entryId }
            if (newEntry != null) {
                newDispatcher.dispatch(newEntry.storedAction, sourceButton, sourceOverride)
                return
            }
        }

        if (action.value == ActionFacade.BACK) {
            host.accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        scope.launch(Dispatchers.Main.immediate) {
            ActionRegistry.execute(action, buildActionHandlerContext(sourceButton, sourceOverride))
        }
    }

    private fun buildActionHandlerContext(
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride?,
    ): ActionHandlerContext {
        val snap = settingsSnapshot()
        val context: Context = host.context.applicationContext
        return ActionHandlerContext(
            accessibilityService = host.accessibilityService,
            appContext = context,
            scope = scope,
            actionSettings = snap.actionSettings.effectiveFor(sourceOverride),
            advancedSettings = snap.advancedSettings.effectiveFor(sourceOverride),
            gestureSettings = snap.gestureSettings,
            actionLibrarySettings = snap.actionLibrarySettings,
            showToast = { showToastUtil(it) },
            showLongToast = { showToastLongUtil(it) },
            currentPackageName = { host.getCurrentPackageName() },
            nowInLauncher = { host.nowInLauncher() },
            requestEnableDisabledPackage = { packageName, onResult ->
                host.requestEnableDisabledPackage(packageName, onResult)
            },
            toggleQuickAppLauncher = onToggleQuickAppLauncher,
            showVolumeScrub = onShowVolumeScrub,
            hideGestureButton = { delayMs ->
                if (sourceButton != null) {
                    onHideGestureButton(sourceButton, delayMs)
                }
            },
            toggleKeepScreenOn = { keepScreenOnController.toggle() },
            showVersionTooLowToast = { resId ->
                showVersionTooLowToastUtil(context, resId)
            },
            previousApp = { previousAppTracker.previousApp() },
        )
    }
}

data class SettingsSnapshot(
    val actionSettings: ActionSettings,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: GestureSettings,
    val actionLibrarySettings: ActionLibrarySettings,
    val newActionLibrarySettings: NewActionLibrarySettings = NewActionLibrarySettings(),
)
