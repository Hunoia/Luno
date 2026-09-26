package hunoia.luno.action.handlers

import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.core.JsonSerializer
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShellCommandActionHandler : ActionHandler {
    override val supportedActions = setOf(ActionFacade.EXECUTE_SHELL_COMMAND)

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        val data = runCatching {
            JsonSerializer.decodeFromString<ShellCommandData>(action.data)
        }.getOrDefault(ShellCommandData())
        val command = data.command.trim()
        if (command.isBlank()) {
            context.showToast(context.appContext.getString(R.string.action_setting_hint_shell_command))
            return ActionExecutionResult.Ignored
        }

        val result = withContext(Dispatchers.IO) {
            ShizukuFacade.runShellCommand(context.appContext, command)
        }
        if (data.showToast) {
            val message = if (result.success) {
                result.output.ifBlank { context.appContext.getString(R.string.shell_command_no_output) }
            } else {
                result.error ?: result.output.ifBlank { "unknown error" }
            }.take(500)
            context.showToast(message)
        }
        return ActionExecutionResult.Success
    }
}
