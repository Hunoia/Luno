package hunoia.luno.action.executor

import hunoia.luno.action.controller.SettingsController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult

class SettingsExecutors(private val controller: SettingsController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("settings.get", "settings.put")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.SettingsGet -> controller.get(action.namespace, action.key)
            is Action.SettingsPut -> controller.put(action.namespace, action.key, action.value)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
