package hunoia.luno.action.handlers

import hunoia.luno.bridge.accessibility.Accessibility
import hunoia.luno.config.model.Action
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext

object ScrollActionHandler : ActionHandler {
    override val supportedActions = setOf(
        ActionFacade.BACK_TO_TOP,
    )

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        return when (action.value) {
            ActionFacade.BACK_TO_TOP -> {
                Accessibility.fastVerticalScroll(context.accessibilityService, toTop = true)
                ActionExecutionResult.Success
            }
            else -> ActionExecutionResult.Ignored
        }
    }
}
