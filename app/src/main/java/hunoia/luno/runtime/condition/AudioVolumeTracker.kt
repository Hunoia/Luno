package hunoia.luno.runtime.condition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.runtime.VolumeChange

/**
 * 提供音量与铃声电平值；仅在有音量/铃声相关规则时才轮询，并产生音量变化脉冲。
 *
 * 单次 tick 只对每路音频读一次音量：方向判定与百分比换算共用同一个 index，
 * 且 `getStreamMaxVolume` 缓存起来（它不随音量变化而变）。相比逐路读两次，
 * 每 tick 的 Binder 调用从 18 次降到 6 次。
 *
 * 注：本项目所用 android.jar 未包含 AudioManager.OnVolumeChangedListener（API 23+ 的事件驱动
 * 方案），因此只能保留轮询。若日后 SDK 补齐，可换成 registerVolumeChangeListener 彻底去掉定时器。
 */
class AudioVolumeTracker(
    private val context: Context,
    private val onVolumeChanged: (VolumeChange) -> Unit = {},
) {
    var volumePercent: Map<AudioStream, Int> = emptyMap()
        private set

    var isRingerSilent: Boolean = false
        private set

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val lastIndex = HashMap<Int, Int>()
    private val maxVolumeCache = HashMap<Int, Int>()
    private var polling = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(localContext: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) refreshRingerMode()
        }
    }

    private val poller = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /** 有 VOLUME_RANGE / VOLUME_CHANGED / RINGER_SILENT 规则时才轮询 */
    fun sync(pollNeeded: Boolean) {
        if (pollNeeded == polling) return
        polling = pollNeeded
        if (polling) {
            context.registerReceiver(
                receiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                Context.RECEIVER_NOT_EXPORTED,
            )
            poll()
            handler.postDelayed(poller, POLL_INTERVAL_MS)
        } else {
            stopListening()
        }
    }

    fun unregister() {
        if (!polling) return
        polling = false
        stopListening()
    }

    private fun stopListening() {
        runCatching { context.unregisterReceiver(receiver) }
        handler.removeCallbacks(poller)
        lastIndex.clear()
    }

    private fun poll() {
        runCatching {
            val percent = HashMap<AudioStream, Int>(AudioStream.entries.size)
            for (stream in AudioStream.entries) {
                val streamType = stream.toStreamType()
                val index = audioManager.getStreamVolume(streamType)
                val maxVolume = maxVolumeCache.getOrPut(streamType) { audioManager.getStreamMaxVolume(streamType) }
                percent[stream] = if (maxVolume <= 0) -1 else index * 100 / maxVolume

                val before = lastIndex.put(streamType, index)
                if (before != null && before != index) {
                    val direction = if (index > before) VolumeDirection.UP else VolumeDirection.DOWN
                    onVolumeChanged(VolumeChange(stream, direction))
                }
            }
            volumePercent = percent
            refreshRingerMode()
        }
    }

    private fun refreshRingerMode() {
        runCatching { isRingerSilent = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT }
    }

    private fun AudioStream.toStreamType(): Int = when (this) {
        AudioStream.MUSIC -> AudioManager.STREAM_MUSIC
        AudioStream.RING -> AudioManager.STREAM_RING
        AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        AudioStream.ALARM -> AudioManager.STREAM_ALARM
        AudioStream.VOICE -> AudioManager.STREAM_VOICE_CALL
        AudioStream.SYSTEM -> AudioManager.STREAM_SYSTEM
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
