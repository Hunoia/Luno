package hunoia.luno.action.handlers

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action

object NavigationActionHandler : ActionHandler {

    override val supportedActions = setOf(
        ActionFacade.BACK,
        ActionFacade.HOME,
        ActionFacade.RECENT,
        ActionFacade.OPEN_NOTIFICATION_PANEL,
        ActionFacade.OPEN_QUICK_PANEL,
        ActionFacade.PREVIOUS_APP,
    )

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.BACK -> context.accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)
            ActionFacade.HOME -> context.accessibilityService.performGlobalAction(GLOBAL_ACTION_HOME)
            ActionFacade.RECENT -> context.accessibilityService.performGlobalAction(GLOBAL_ACTION_RECENTS)
            ActionFacade.OPEN_NOTIFICATION_PANEL -> context.accessibilityService.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            ActionFacade.OPEN_QUICK_PANEL -> context.accessibilityService.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            ActionFacade.PREVIOUS_APP -> {
                context.previousApp()
                return ActionExecutionResult.Success
            }
        }
        return ActionExecutionResult.Success
    }
}
