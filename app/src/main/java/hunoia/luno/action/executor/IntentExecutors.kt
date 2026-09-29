package hunoia.luno.action.executor

import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult

class IntentExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "intent.openUrl", "intent.shareText", "intent.shareFile", "intent.openFile", "intent.assist"
    )

    override suspend fun execute(action: Action, context: ExecutorContext):ActionResult {
        return when (action) {
            is Action.OpenUrl -> {
                val ok = intentController.launchUrl(action.url)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.ShareText -> {
                val ok = intentController.shareText(action.text, action.mimeType)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.ShareFile -> {
                val ok = intentController.shareFile(action.filePath, action.mimeType)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.OpenFile -> {
                val ok = intentController.openFile(action.filePath, action.mimeType)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Assist -> {
                val ok = intentController.launchAssist()
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
