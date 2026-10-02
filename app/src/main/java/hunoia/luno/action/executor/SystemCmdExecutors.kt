package hunoia.luno.action.executor

import hunoia.luno.action.controller.ShellController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult

class SystemCmdExecutors(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("systemCmd.reboot", "systemCmd.shutdown")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.Reboot -> {
                val result = shellController.runCommand("reboot")
                if (result.success) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Shutdown -> {
                val result = shellController.runCommand("shutdown -p")
                if (result.success) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
