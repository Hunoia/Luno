package hunoia.luno.action.handlers

import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.action.api.shortcutInfo

object ShortcutActionHandler : ActionHandler {

    override val supportedActions = setOf(ActionFacade.EXTRA_LAUNCH_SHORTCUT)

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.EXTRA_LAUNCH_SHORTCUT -> {
                val shortcutInfo = action.shortcutInfo
                if (shortcutInfo != null) {
                    QuickLaunchFacade.launchShortcutInfo(context.appContext, shortcutInfo)
                }
            }
            else -> return ActionExecutionResult.Ignored
        }
        return ActionExecutionResult.Success
    }
}
