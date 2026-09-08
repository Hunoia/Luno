package hunoia.luno.runtime.action

import android.content.Context
import android.os.PowerManager
import hunoia.luno.R

class KeepScreenOnController(
    private val context: Context,
    private val showToast: (String) -> Unit,
) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun toggle() {
        if (wakeLock != null) {
            safeReleaseWakeLock()
            showToast(context.getString(R.string.disable_keep_screen_on))
        } else {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "gulugulu:KeepScreenOn"
            )
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(KEEP_SCREEN_ON_WAKE_LOCK_TIMEOUT_MS)
            showToast(context.getString(R.string.enable_keep_screen_on))
        }
    }

    fun onRelease() {
        safeReleaseWakeLock()
    }

    private fun safeReleaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock?.isHeld == true) {
            try {
                lock.release()
            } catch (ignored: RuntimeException) { }
        }
    }

    private companion object {
        const val KEEP_SCREEN_ON_WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 1000L
    }
}
