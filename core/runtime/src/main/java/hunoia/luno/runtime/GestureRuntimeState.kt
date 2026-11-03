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
    /**
     * 生成这份快照时的单调时钟。只用于「临时隐藏是否到期」这一瞬间判断；
     * 快照本身可被缓存（`RuntimeStateHub`），**不要**拿它当“当前时间”复用。
     */
    val nowMs: Long = SystemClock.uptimeMillis(),
)
