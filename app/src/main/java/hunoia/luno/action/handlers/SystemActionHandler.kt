package hunoia.luno.action.handlers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import hunoia.luno.R
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.bridge.accessibility.GlobalAction
import hunoia.luno.bridge.FlashlightController
import hunoia.luno.bridge.intent.launchAssist
import hunoia.luno.bridge.intent.gotoAppDetailSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SystemActionHandler : ActionHandler {

    override val supportedActions = setOf(
        ActionFacade.POWER_BUTTON,
        ActionFacade.LOCK_SCREEN,
        ActionFacade.FLASHLIGHT,
        ActionFacade.SPLIT_SCREEN,
        ActionFacade.ASSIST_APP,
        ActionFacade.SCREENSHOT,
        ActionFacade.KEEP_SCREEN_ON,
        ActionFacade.HIDE_GESTURE_BUTTON,
    )

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.POWER_BUTTON -> GlobalAction.powerDialog(context.accessibilityService)
            ActionFacade.LOCK_SCREEN -> handleLockScreen(context)
            ActionFacade.FLASHLIGHT -> handleFlashlight(context)
            ActionFacade.SPLIT_SCREEN -> handleSplitScreen(context)
            ActionFacade.ASSIST_APP -> context.appContext.launchAssist()
            ActionFacade.SCREENSHOT -> handleScreenshot(context)
            ActionFacade.KEEP_SCREEN_ON -> context.toggleKeepScreenOn()
            ActionFacade.HIDE_GESTURE_BUTTON -> context.hideGestureButton(context.actionSettings.hideGestureButton.delayMs)
            else -> return ActionExecutionResult.Ignored
        }
        return ActionExecutionResult.Success
    }

    private fun handleLockScreen(context: ActionHandlerContext) {
        GlobalAction.lockScreen(context.accessibilityService)
    }

    private suspend fun handleFlashlight(context: ActionHandlerContext) {
        if (!FlashlightController.isSupported(context.appContext)) {
            context.showToast(context.appContext.getString(R.string.flashlight_failed))
            return
        }
        if (!FlashlightController.hasPermission(context.appContext)) {
            context.showToast(context.appContext.getString(R.string.grant_camera_permission))
            context.showToast(context.appContext.getString(R.string.goto_grant_camera_permission))
            context.appContext.gotoAppDetailSettings()
            return
        }
        if (!FlashlightController.toggle(context.appContext)) {
            context.showToast(context.appContext.getString(R.string.flashlight_failed))
        }
    }

    private fun handleSplitScreen(context: ActionHandlerContext) {
        GlobalAction.toggleSplitScreen(context.accessibilityService)
    }

    private fun handleScreenshot(context: ActionHandlerContext) {
        context.scope.launch {
            delay(200)
            GlobalAction.takeScreenshot(context.accessibilityService)
        }
    }

}
