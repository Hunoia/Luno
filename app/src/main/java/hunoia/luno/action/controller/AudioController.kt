package hunoia.luno.action.controller

import android.content.Context
import android.media.AudioManager
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.MediaCommand
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.bridge.dispatchMediaKeyEvent
import hunoia.luno.bridge.toggleMute
import hunoia.luno.bridge.volumeDown
import hunoia.luno.bridge.volumeUp
import android.view.KeyEvent

class AudioController(private val context: Context) {

    fun volumeUp() { context.volumeUp() }
    fun volumeDown() { context.volumeDown() }
    fun toggleMute() { context.toggleMute() }

    fun adjustVolume(stream: AudioStream, direction: VolumeDirection) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamType = stream.toStreamType()
        val adjustType = if (direction == VolumeDirection.UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(streamType, adjustType, AudioManager.FLAG_SHOW_UI)
    }

    fun mediaCommand(command: MediaCommand) {
        when (command) {
            MediaCommand.PLAY_PAUSE -> context.dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            MediaCommand.NEXT -> context.dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            MediaCommand.PREVIOUS -> context.dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MediaCommand.MUTE -> toggleMute()
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
}
