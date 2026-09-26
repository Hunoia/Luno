package hunoia.luno.action.api

import hunoia.luno.action.ActionLibraryResolver
import hunoia.luno.action.handlers.allHandlers
import hunoia.luno.config.model.Action

object ActionRegistry {
    private val executor = ActionExecutor(
        handlers = allHandlers,
        resolveAction = { ActionLibraryResolver.resolve(it) }
    )

    fun isRegistered(actionId: String): Boolean = executor.isRegistered(actionId)

    suspend fun execute(action: Action, context: ActionHandlerContext): ActionExecutionResult =
        executor.execute(action, context)
}
