package hunoia.luno.action.controller

import android.content.Context
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String?,
    val exitCode: Int,
)

class ShellController(private val context: Context) {

    suspend fun runCommand(command: String): ShellResult = withContext(Dispatchers.IO) {
        val result = ShizukuFacade.runShellCommand(context, command)
        ShellResult(
            success = result.success,
            output = result.output,
            error = result.error,
            exitCode = result.exitCode,
        )
    }
}
