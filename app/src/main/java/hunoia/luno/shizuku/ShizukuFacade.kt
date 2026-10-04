package hunoia.luno.shizuku

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

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

@Parcelize
data class PackageResult(
    val success: Boolean,
    val packageName: String = "",
    val errorMessage: String = "",
) : Parcelable

data class ShellCommandResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val elapsedMs: Long,
    val error: String? = null,
)

@Parcelize
data class ShellResult(
    val exitCode: Int = -1,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val errorMessage: String = "",
) : Parcelable {
    val isSuccess: Boolean
        get() = !timedOut && errorMessage.isEmpty() && exitCode == 0
}

data class ShizukuStatus(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val uid: Int?
) {
    val isReady: Boolean
        get() = binderAlive && permissionGranted
}
