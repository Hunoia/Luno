package hunoia.luno.runtime.condition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.VolumeDirection
import hunoia.luno.config.model.RingerMode
import hunoia.luno.runtime.VolumeChange
import hunoia.luno.action.model.toStreamType

/**
 * 提供音量与铃声电平；仅有相关规则时轮询并产生音量变化脉冲。
 * 单次 tick 每路只读一次音量（方向判定与百分比共用 index），getStreamMaxVolume 缓存，
 * 每 tick Binder 调用从 18 次降到 6 次。android.jar 无 OnVolumeChangedListener，只能轮询。
 */
class AudioVolumeTracker(
    private val context: Context,
    private val onVolumeChanged: (VolumeChange) -> Unit = {},
) {
    var volumePercent: Map<AudioStream, Int> = emptyMap()
        private set

    var ringerMode: RingerMode = RingerMode.NORMAL
        private set

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
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
            // 息屏降频到 1/5：音量规则多在交互期有意义；息屏时蓝牙耳机调音量这类
            // 变化仍能触发（晚几秒），而不是彻底停轮询丢能力。
            val intervalMs =
                if (powerManager.isInteractive) POLL_INTERVAL_MS else POLL_INTERVAL_IDLE_MS
            handler.postDelayed(this, intervalMs)
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
        runCatching {
            ringerMode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                else -> RingerMode.NORMAL
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L

        /** 息屏轮询间隔（交互期的 1/5）。 */
        private const val POLL_INTERVAL_IDLE_MS = 5_000L
    }
}
