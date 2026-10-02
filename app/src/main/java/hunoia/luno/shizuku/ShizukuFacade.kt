package hunoia.luno.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuFacade {

    fun isReady(): Boolean = ShizukuManager.currentStatus().isReady

    suspend fun runShellCommand(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val result = ShizukuManager.executeShell(command)
        ShellCommandResult(
            success = result.isSuccess,
            exitCode = result.exitCode,
            output = result.stdout,
            elapsedMs = System.currentTimeMillis() - startMs,
            error = result.errorMessage.ifBlank { null }
        )
    }
}
