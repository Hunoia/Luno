package hunoia.luno.bridge.system

import android.media.AudioManager
import hunoia.luno.core.domain.AudioStream

/**
 * 契约层的音量流 → 平台 StreamType。
 *
 * 动作层（音量控制）与条件层（音量读取）都要用，所以归平台边界的 `:core:bridge`，
 * 不允许 feature 之间互相借。
 */
fun AudioStream.toStreamType(): Int = when (this) {
    AudioStream.MUSIC -> AudioManager.STREAM_MUSIC
    AudioStream.RING -> AudioManager.STREAM_RING
    AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    AudioStream.ALARM -> AudioManager.STREAM_ALARM
    AudioStream.VOICE -> AudioManager.STREAM_VOICE_CALL
    AudioStream.SYSTEM -> AudioManager.STREAM_SYSTEM
}
