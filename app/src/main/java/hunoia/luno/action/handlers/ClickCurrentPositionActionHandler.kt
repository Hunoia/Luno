package hunoia.luno.action.handlers

import hunoia.luno.bridge.accessibility.Accessibility
import hunoia.luno.config.model.Action
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import kotlinx.coroutines.delay

object ClickCurrentPositionActionHandler : ActionHandler {
    override val supportedActions = setOf(ActionFacade.CLICK_CURRENT_POSITION)

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        val extra = action.extra
        @Suppress("UNCHECKED_CAST")
        val xy = extra as? List<*> ?: return ActionExecutionResult.Ignored
        if (xy.size < 2) return ActionExecutionResult.Ignored
        val x = (xy[0] as? Number)?.toInt() ?: return ActionExecutionResult.Ignored
        val y = (xy[1] as? Number)?.toInt() ?: return ActionExecutionResult.Ignored
        context.hideGestureButton(250L)
        delay(80)
        return if (Accessibility.click(context.accessibilityService, x, y)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed("click failed")
        }
    }
}
