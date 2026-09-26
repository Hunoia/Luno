package hunoia.luno.action.handlers

import hunoia.luno.R
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.freeze.FreezeFacade

object FreezeAppsActionHandler : ActionHandler {

    override val supportedActions = setOf(ActionFacade.ONE_KEY_FREEZE_APPS)

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.ONE_KEY_FREEZE_APPS -> {
                val result = FreezeFacade.oneKeyFreeze(context.appContext)
                val msg = context.appContext.getString(R.string.bulk_frozen_count, result.successCount)
                context.showToast(msg)
            }
            else -> return ActionExecutionResult.Ignored
        }
        return ActionExecutionResult.Success
    }
}
