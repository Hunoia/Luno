package hunoia.luno.runtime.condition

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import hunoia.luno.config.model.PlaybackDirection

/**
 * 音乐播放状态/变化脉冲：AudioPlaybackCallback 事件驱动（API 26+），
 * 无音乐条件时不注册、零开销；真值取 isMusicActive，避免把通知音/来电当音乐。
 *
 * 首读（注册基线）与重注册基线不发脉冲；停止监听后重置基线，
 * 重新注册时不以"监听空窗期"的变化补发脉冲。
 */
class MusicPlaybackTracker(
    private val context: Context,
    private val onChanged: (PlaybackDirection) -> Unit = {},
) {
    var isPlaying: Boolean = false
        private set

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var initialized = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            refresh()
        }
    }

    fun sync(needed: Boolean) {
        if (needed == registered) return
        if (needed) {
            initialized = false
            val ok = runCatching {
                audioManager.registerAudioPlaybackCallback(callback, handler)
            }.isSuccess
            if (!ok) return
            registered = true
            refresh()
        } else {
            runCatching { audioManager.unregisterAudioPlaybackCallback(callback) }
            registered = false
            initialized = false
        }
    }

    fun unregister() {
        if (!registered) return
        registered = false
        runCatching { audioManager.unregisterAudioPlaybackCallback(callback) }
    }

    private fun refresh() {
        val playing = runCatching { audioManager.isMusicActive }.getOrDefault(false)
        if (!initialized) {
            initialized = true
            isPlaying = playing
            return
        }
        if (playing == isPlaying) return
        isPlaying = playing
        onChanged(if (playing) PlaybackDirection.STARTED else PlaybackDirection.STOPPED)
    }
}
