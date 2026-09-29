package hunoia.luno.action.executor

import hunoia.luno.action.controller.PackageController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult

class PackageExecutors(private val controller: PackageController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "package.enable", "package.disable", "package.forceStop",
        "package.clearData", "package.uninstall"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.PackageEnable -> controller.enable(action.packageName)
            is Action.PackageDisable -> controller.disable(action.packageName)
            is Action.PackageForceStop -> controller.forceStop(action.packageName)
            is Action.PackageClearData -> controller.clearData(action.packageName)
            is Action.PackageUninstall -> controller.uninstall(action.packageName)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
