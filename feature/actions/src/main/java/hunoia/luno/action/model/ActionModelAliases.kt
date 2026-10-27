package hunoia.luno.action.model

import android.media.AudioManager

/**
 * 兼容桥：动作模型已下沉 `hunoia.luno.core.domain`。
 * Phase 2 拆模块时删除这些 typealias，调用方改为直接 import 契约包。
 */
typealias StoredAction = hunoia.luno.core.domain.StoredAction

typealias AudioStream = hunoia.luno.core.domain.AudioStream

typealias VolumeDirection = hunoia.luno.core.domain.VolumeDirection

/** 动作层与运行时层共用，避免 AudioController / AudioVolumeTracker 各写一份。 */
fun AudioStream.toStreamType(): Int = when (this) {
    AudioStream.MUSIC -> AudioManager.STREAM_MUSIC
    AudioStream.RING -> AudioManager.STREAM_RING
    AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    AudioStream.ALARM -> AudioManager.STREAM_ALARM
    AudioStream.VOICE -> AudioManager.STREAM_VOICE_CALL
    AudioStream.SYSTEM -> AudioManager.STREAM_SYSTEM
}
