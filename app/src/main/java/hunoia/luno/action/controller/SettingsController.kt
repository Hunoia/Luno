package hunoia.luno.action.controller

import android.content.Context
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.SettingsNamespace
import hunoia.luno.shizuku.ShizukuFacade

class SettingsController(private val context: Context) {

    suspend fun get(namespace: SettingsNamespace, key: String): ActionResult {
        val command = "settings get ${namespace.toShellName()} ${shellQuote(key)}"
        val result = ShizukuFacade.runShellCommand(context, command)
        return if (result.success) {
            val value = result.output.trim()
            if (value.equals("null", ignoreCase = true)) {
                ActionResult.Failed(ActionFailure.InvalidParameter)
            } else {
                ActionResult.Success(value.take(200))
            }
        } else {
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }

    suspend fun put(namespace: SettingsNamespace, key: String, value: String): ActionResult {
        val command = "settings put ${namespace.toShellName()} ${shellQuote(key)} ${shellQuote(value)}"
        val result = ShizukuFacade.runShellCommand(context, command)
        return if (result.success) {
            ActionResult.Success()
        } else {
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }

    private fun SettingsNamespace.toShellName(): String = when (this) {
        SettingsNamespace.SYSTEM -> "system"
        SettingsNamespace.GLOBAL -> "global"
        SettingsNamespace.SECURE -> "secure"
    }
}
