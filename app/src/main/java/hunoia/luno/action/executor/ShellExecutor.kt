package hunoia.luno.action.executor

import hunoia.luno.action.controller.ShellController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult

class ShellExecutor(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("shell.custom")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.CustomCommand -> {
                val command = action.command.trim()
                if (command.isBlank()) return ActionResult.Failed(ActionFailure.InvalidParameter)
                val result = shellController.runCommand(command)
                val message = if (action.showToast) {
                    if (result.success) {
                        result.output.ifBlank { "No output" }
                    } else {
                        result.error ?: result.output.ifBlank { "Unknown error" }
                    }.take(500)
                } else {
                    null
                }
                if (result.success) ActionResult.Success(message) else ActionResult.Failed(ActionFailure.ExecutionFailed, message)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
