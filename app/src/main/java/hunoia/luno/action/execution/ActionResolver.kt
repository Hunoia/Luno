package hunoia.luno.action.execution

import hunoia.luno.action.model.ActionMapper
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.StoredAction

class ActionResolver(
    private val registry: ActionExecutorRegistry,
) {
    suspend fun resolveFromLibrary(
        stored: StoredAction,
        context: ExecutorContext,
    ): ActionResult {
        val action = ActionMapper.fromStored(stored)
        return registry.execute(action, context)
    }
}
