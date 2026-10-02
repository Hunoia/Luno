package hunoia.luno.runtime

import android.os.SystemClock
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType

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

    // 电平条件
    val networkType: NetworkType = NetworkType.NONE,
    val headphonesConnected: Boolean = false,
    val bluetoothAdapterOn: Boolean? = null,
    val bluetoothAudioConnected: Boolean? = null,
    val airplaneMode: Boolean = false,
    val isRingerSilent: Boolean = false,
    val volumePercent: Map<AudioStream, Int> = emptyMap(),

    // 一次性脉冲：本轮写入，buildRuntimeState() 消费即清空
    val appChangedTo: String? = null,
    val screenEvent: ScreenEventType? = null,
    val chargingChangedTo: Boolean? = null,
    val volumeChanged: VolumeChange? = null,
)

data class VolumeChange(
    val stream: AudioStream,
    val direction: VolumeDirection,
)
