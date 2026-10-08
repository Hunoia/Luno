package hunoia.luno.runtime

import android.os.SystemClock
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.config.model.KeyboardEventType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.PlaybackDirection
import hunoia.luno.config.model.RingerMode
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
    val dayOfWeek: Int = 1,
    val nowMs: Long = SystemClock.uptimeMillis(),

    // 电平条件
    val networkType: NetworkType = NetworkType.NONE,
    val headphonesConnected: Boolean = false,
    val bluetoothAdapterOn: Boolean? = null,
    val bluetoothAudioConnected: Boolean? = null,
    val airplaneMode: Boolean = false,
    val ringerMode: RingerMode = RingerMode.NORMAL,
    val volumePercent: Map<AudioStream, Int> = emptyMap(),
    val isMusicPlaying: Boolean = false,
    val isTorchOn: Boolean = false,
    val isDndOn: Boolean = false,

    // 一次性脉冲：本轮写入，buildRuntimeState() 消费即清空
    val appChangedTo: String? = null,
    val screenEvent: ScreenEventType? = null,
    val chargingChangedTo: Boolean? = null,
    val volumeChanged: VolumeChange? = null,
    // v2 新增脉冲（P2 事件源接入；默认 null=无脉冲）
    val notificationPosted: hunoia.luno.runtime.condition.NotificationPulse? = null,
    val bluetoothChanged: hunoia.luno.config.model.ConnectionDirection? = null,
    val headphonesChanged: hunoia.luno.config.model.ConnectionDirection? = null,
    val scheduledTimeHit: Boolean = false,
    /** 手势脉冲：本轮命中的按钮 id，无则 null。 */
    val gesturePerformedId: String? = null,
    /** v3 脉冲：键盘弹出/收起、音乐启停、手电/勿扰开关变化。 */
    val keyboardEvent: KeyboardEventType? = null,
    val musicChanged: PlaybackDirection? = null,
    val torchChangedTo: Boolean? = null,
    val dndChangedTo: Boolean? = null,
)

data class VolumeChange(
    val stream: AudioStream,
    val direction: VolumeDirection,
)
