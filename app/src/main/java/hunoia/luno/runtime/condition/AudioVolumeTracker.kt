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

/** 提供音量与铃声电平值；仅在有音量/铃声相关规则时轮询，并产生音量变化脉冲 */
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
    private var polling = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(localContext: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) refresh()
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
            refresh()
            handler.postDelayed(poller, POLL_INTERVAL_MS)
        } else {
            runCatching { context.unregisterReceiver(receiver) }
            handler.removeCallbacks(poller)
            lastIndex.clear()
        }
    }

    fun unregister() {
        if (!polling) return
        polling = false
        runCatching { context.unregisterReceiver(receiver) }
        handler.removeCallbacks(poller)
        lastIndex.clear()
    }

    private fun poll() {
        runCatching {
            AudioStream.entries.forEach { stream ->
                val streamType = stream.toStreamType()
                val index = audioManager.getStreamVolume(streamType)
                val before = lastIndex[streamType]
                if (before != null && before != index) {
                    val direction = if (index > before) VolumeDirection.UP else VolumeDirection.DOWN
                    onVolumeChanged(VolumeChange(stream, direction))
                }
                lastIndex[streamType] = index
            }
            refresh()
        }
    }

    private fun refresh() {
        runCatching {
            volumePercent = AudioStream.entries.associateWith { stream ->
                val maxVolume = audioManager.getStreamMaxVolume(stream.toStreamType())
                if (maxVolume <= 0) {
                    -1
                } else {
                    audioManager.getStreamVolume(stream.toStreamType()) * 100 / maxVolume
                }
            }
            isRingerSilent = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
        }
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
