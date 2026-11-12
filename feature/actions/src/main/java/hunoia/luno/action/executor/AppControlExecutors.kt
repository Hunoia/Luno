package hunoia.luno.action.executor

import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.actions.R
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.launch.PackageControl

/**
 * 一键冻结 / 解冻名单的唯一执行器。
 *
 * 批量与缓存刷新都在 [PackageControl]：这里只把结果翻译成三态，不在 Executor 里
 * 做鉴权、查配置或另开 Shizuku 写入口（architecture §9.1）。
 */
class AppControlExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "app.freezeOneKey",
        "app.unfreezeOneKey",
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult = when (action) {
        is Action.FreezeOneKey -> {
            val result = PackageControl.freezeOneKey()
            if (result.total == 0) {
                ActionResult.Failed(
                    reason = ActionFailure.InvalidParameter,
                    userMessage = context.environment.appContext.getString(R.string.app_control_one_key_empty),
                )
            } else {
                report(context, result)
            }
        }
        is Action.UnfreezeOneKey -> {
            val result = PackageControl.unfreezeOneKey()
            if (result.total == 0) {
                ActionResult.Success(
                    message = context.environment.appContext.getString(
                        R.string.app_control_nothing_to_unfreeze,
                    ),
                )
            } else {
                report(context, result)
            }
        }
        else -> ActionResult.Failed(ActionFailure.Unsupported)
    }

    private fun report(context: ExecutorContext, result: PackageControl.BatchResult): ActionResult {
        val appContext = context.environment.appContext
        if (result.failures.isEmpty()) {
            return ActionResult.Success(
                message = appContext.getString(R.string.app_control_result_success, result.successes.size),
            )
        }
        return ActionResult.Failed(
            reason = ActionFailure.ExecutionFailed,
            message = result.failures.joinToString { "${it.packageName}:${it.reason}" },
            userMessage = appContext.getString(
                R.string.app_control_result_partial,
                result.successes.size,
                result.failures.size,
            ),
        )
    }
}
