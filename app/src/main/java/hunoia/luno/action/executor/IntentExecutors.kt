package hunoia.luno.action.executor

import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.config.model.effectiveForAction
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.quicklaunch.QuickLaunchFacade

class IntentExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "intent.openUrl", "intent.shareText", "intent.shareFile", "intent.assist"
    )

    override suspend fun execute(action: Action, context: ExecutorContext):ActionResult {
        return when (action) {
            is Action.OpenUrl -> {
                if (action.url.isBlank()) return ActionResult.Failed(ActionFailure.InvalidParameter)
                val adv = context.advancedSettings.effectiveForAction(action)
                val data = OpenAppOrUrlData(
                    type = OpenAppOrUrlData.TYPE_URL,
                    url = action.url,
                    queryParameters = action.queryParameters.map {
                        OpenUrlQueryParameter(name = it.name, value = it.value, enabled = it.enabled)
                    },
                )
                val ok = QuickLaunchFacade.launchUrlDirect(
                    context = context.appContext,
                    data = data,
                    miniWindowHorizontalBias = adv.miniWindowHorizontalBias,
                    miniWindowVerticalBias = adv.miniWindowVerticalBias,
                    miniWindowWidthFraction = adv.miniWindowWidthFraction,
                    miniWindowHeightFraction = adv.miniWindowHeightFraction,
                    miniWindowOverrideBounds = adv.miniWindowOverrideBounds,
                )
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.ShareText -> intentController.shareText(action.text, action.mimeType)
            is Action.ShareFile -> intentController.shareFile()
            is Action.Assist -> intentController.launchAssist()
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
