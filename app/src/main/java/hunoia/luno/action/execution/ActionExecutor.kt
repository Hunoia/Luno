package hunoia.luno.action.execution

import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionResult

interface ActionExecutor {
    val supportedTypes: Set<String>
    suspend fun execute(action: Action, context: ExecutorContext): ActionResult
}
