package hunoia.luno.action.controller

import android.content.Context
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageController(private val context: Context) {

    suspend fun enable(packageName: String): ActionResult = runShell("cmd package set-enabled $packageName enabled")

    suspend fun disable(packageName: String): ActionResult = runShell("cmd package set-enabled $packageName disabled")

    suspend fun forceStop(packageName: String): ActionResult = runShell("am force-stop $packageName")

    suspend fun clearData(packageName: String): ActionResult = runShell("pm clear $packageName")

    suspend fun uninstall(packageName: String): ActionResult = runShell("pm uninstall $packageName")

    private suspend fun runShell(command: String):ActionResult = withContext(Dispatchers.IO) {
        val result = ShizukuFacade.runShellCommand(context, command)
        if (result.success) {
            ActionResult.Success()
        } else {
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }
}
