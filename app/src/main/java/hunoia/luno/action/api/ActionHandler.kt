package hunoia.luno.action.api

import hunoia.luno.config.model.Action

interface ActionHandler {
    val supportedActions: Set<String>
    suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult
}
