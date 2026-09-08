package hunoia.luno.shizuku

import android.content.Context
import kotlinx.coroutines.runBlocking

object ShizukuFacade {

    fun isReady(): Boolean = ShizukuManager.currentStatus().isReady

    fun runShellCommand(context: Context, command: String): ShellCommandResult {
        val startMs = System.currentTimeMillis()
        val result = runBlocking { ShizukuManager.executeShell(command) }
        return ShellCommandResult(
            success = result.isSuccess,
            exitCode = result.exitCode,
            output = result.stdout,
            elapsedMs = System.currentTimeMillis() - startMs,
            error = result.errorMessage.ifBlank { null }
        )
    }
}
