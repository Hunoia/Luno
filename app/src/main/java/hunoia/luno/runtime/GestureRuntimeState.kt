package hunoia.luno.runtime

import android.os.SystemClock

data class GestureRuntimeState(
    val currentPackageName: String,
    val isNowInLockScreenPage: Boolean,
    val isLandscape: Boolean,
    val isInLauncher: Boolean,
    val isKeyboardInputActive: Boolean,
    val hiddenGestureButtons: Map<String, Long>,
    val isMouseMode: Boolean,
    val nowMs: Long = SystemClock.uptimeMillis(),
)
