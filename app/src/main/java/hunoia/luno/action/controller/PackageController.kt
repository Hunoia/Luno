package hunoia.luno.action.controller

import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.shizuku.ShizukuFacade

class PackageController {

    suspend fun enable(packageName: String): ActionResult =
        runShell("cmd package set-enabled ${shellQuote(packageName)} enabled")

    suspend fun disable(packageName: String): ActionResult =
        runShell("cmd package set-enabled ${shellQuote(packageName)} disabled")

    suspend fun forceStop(packageName: String): ActionResult =
        runShell("am force-stop ${shellQuote(packageName)}")

    suspend fun clearData(packageName: String): ActionResult =
        runShell("pm clear ${shellQuote(packageName)}")

    suspend fun uninstall(packageName: String): ActionResult =
        runShell("pm uninstall ${shellQuote(packageName)}")

    private suspend fun runShell(command: String): ActionResult {
        val result = ShizukuFacade.runShellCommand(command)
        return if (result.success) {
            ActionResult.Success()
        } else {
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }
}
