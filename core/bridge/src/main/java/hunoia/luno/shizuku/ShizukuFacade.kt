package hunoia.luno.shizuku

import android.os.Parcelable
import hunoia.luno.bridge.ui.assertNotMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

object ShizukuFacade {

    fun isReady(): Boolean = ShizukuManager.currentStatus().isReady

    suspend fun runShellCommand(command: String): ShellResult = withContext(Dispatchers.IO) {
        assertNotMainThread("shizuku.shell")
        ShizukuManager.executeShell(command)
    }
}

@Parcelize
data class PackageResult(
    val success: Boolean,
    val packageName: String = "",
    val errorMessage: String = "",
) : Parcelable

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
) {
    val isReady: Boolean
        get() = binderAlive && permissionGranted
}
