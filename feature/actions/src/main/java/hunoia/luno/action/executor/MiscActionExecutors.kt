package hunoia.luno.action.executor

import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult

/** 图片标注：选择器与编辑 Activity 在 `:app`，这里只转发入口。 */
class MiscActionExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("image.edit")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult = when (action) {
        is Action.ImageEdit -> {
            val opened = context.openImageAnnotate()
            if (opened) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
        else -> ActionResult.Failed(ActionFailure.Unsupported)
    }
}
