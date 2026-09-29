package hunoia.luno.action.executor

import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import kotlinx.coroutines.launch

class AppExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("app.launch", "app.openActivity", "app.details")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.LaunchApp -> {
                val ok = intentController.launchApp(action.packageName, "", action.miniWindow)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.OpenActivity -> {
                val ok = intentController.openActivity(action.packageName, action.activityClassName)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.AppDetails -> {
                val ok = intentController.openAppDetails(action.packageName)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
