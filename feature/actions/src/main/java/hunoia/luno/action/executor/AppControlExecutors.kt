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
        is Action.FreezeOneKey -> report(context, PackageControl.freezeOneKey(), frozen = true)
        is Action.UnfreezeOneKey -> report(context, PackageControl.unfreezeOneKey(), frozen = false)
        else -> ActionResult.Failed(ActionFailure.Unsupported)
    }

    private fun report(
        context: ExecutorContext,
        result: PackageControl.BatchResult,
        frozen: Boolean,
    ): ActionResult {
        val appContext = context.environment.appContext
        // toast 按实际冻结/解冻数显示（含 0 → 「冻结0」/「解冻0」），不再用「已处理」混淆失败数。
        val toast = appContext.getString(
            if (frozen) R.string.app_control_frozen_toast else R.string.app_control_unfrozen_toast,
            result.successes.size,
        )
        if (result.failures.isEmpty()) {
            return ActionResult.Success(message = toast)
        }
        // 失败明细仍进结果（日志/上报），toast 只给计数。
        return ActionResult.Failed(
            reason = ActionFailure.ExecutionFailed,
            message = result.failures.joinToString { "${it.packageName}:${it.reason}" },
            userMessage = toast,
        )
    }
}
