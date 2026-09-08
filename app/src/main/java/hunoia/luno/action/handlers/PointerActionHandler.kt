package hunoia.luno.action.handlers

import hunoia.luno.config.model.Action
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext

object PointerActionHandler : ActionHandler {
    override val supportedActions = setOf(ActionFacade.POINTER)

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        return if (context.showPointer(context.gestureSettings.pointer.continuousMode)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Ignored
        }
    }
}
