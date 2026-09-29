package hunoia.luno.action.executor

import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult

class InternalExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "internal.none", "internal.subGesture", "internal.hideGestureButton",
        "internal.quickAppLauncher", "internal.volumeScrub",
        "internal.keepScreenOn", "internal.previousApp"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.None -> ActionResult.Success()
            is Action.SubGesture -> ActionResult.Success()
            is Action.HideGestureButton -> {
                context.hideGestureButton(context.actionSettings.hideGestureButton.delayMs)
                ActionResult.Success()
            }
            is Action.QuickAppLauncher -> {
                context.toggleQuickAppLauncher()
                ActionResult.Success()
            }
            is Action.VolumeScrub -> {
                if (context.showVolumeScrub()) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.KeepScreenOn -> {
                context.toggleKeepScreenOn()
                ActionResult.Success()
            }
            is Action.PreviousApp -> {
                context.previousApp()
                ActionResult.Success()
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
