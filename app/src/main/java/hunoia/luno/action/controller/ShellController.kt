package hunoia.luno.action.controller

import hunoia.luno.shizuku.ShizukuFacade

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String?,
    val exitCode: Int,
)

internal fun shellQuote(arg: String): String =
    "'" + arg.replace("'", "'\\''") + "'"

class ShellController {

    suspend fun runCommand(command: String): ShellResult {
        val result = ShizukuFacade.runShellCommand(command)
        return ShellResult(
            success = result.success,
            output = result.output,
            error = result.error,
            exitCode = result.exitCode,
        )
    }
}
