package hunoia.luno.runtime

import android.os.SystemClock

data class GestureRuntimeState(
    val currentPackageName: String,
    val isNowInLockScreenPage: Boolean,
    val isLandscape: Boolean,
    val isInLauncher: Boolean,
    val isKeyboardInputActive: Boolean,
    val hiddenGestureButtons: Map<String, Long>,
    val isCharging: Boolean = false,
    val batteryLevel: Int = -1,
    val minuteOfDay: Int = 0,
    val nowMs: Long = SystemClock.uptimeMillis(),
)
