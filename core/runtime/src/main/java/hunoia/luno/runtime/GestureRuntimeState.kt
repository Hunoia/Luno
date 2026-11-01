package hunoia.luno.runtime

import android.os.SystemClock

/** 触钮可见性求值所需的运行态。 */
data class GestureRuntimeState(
    val currentPackageName: String,
    val isNowInLockScreenPage: Boolean,
    val isLandscape: Boolean,
    val isInLauncher: Boolean,
    val isKeyboardInputActive: Boolean,
    val hiddenGestureButtons: Map<String, Long>,
    val nowMs: Long = SystemClock.uptimeMillis(),
)
