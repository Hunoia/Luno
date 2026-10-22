package hunoia.luno.action.controller

import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.KeyEvent
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.MediaCommand
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.action.model.toStreamType
import hunoia.luno.bridge.system.copySensitiveText
import hunoia.luno.bridge.system.dispatchMediaKeyEvent
import hunoia.luno.bridge.system.toggleMute
import hunoia.luno.bridge.vibration.vibrate
import hunoia.luno.bridge.system.volumeDown
import hunoia.luno.bridge.system.volumeUp
import hunoia.luno.shizuku.ShizukuFacade

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
}

class ClipboardController(private val context: Context) {

    fun copyText(text: String, label: String = ""): Boolean {
        return copySensitiveText(context, label, text)
    }

    fun generateRandomName(): String? {
        val blockedNames = setOf("test", "null", "admin", "root", "system", "user")
        val regexThreeVowels = Regex("[aeiou]{3}")
        val regexThreeConsonants = Regex("[bcdfghjklmnpqrstvwxz]{3}")
        val openingSyllables = arrayOf(
            "ve", "se", "sa", "si", "ke", "ka", "ki", "me", "ma", "mi", "mo",
            "ne", "na", "ni", "no", "le", "la", "li", "lo", "re", "ra", "ri",
            "ro", "te", "ta", "ti", "to", "be", "ba", "bi", "bo", "de", "da",
            "di", "do", "fe", "fa", "fi", "fo", "ze", "za", "zi", "ly", "my",
            "ny", "ry", "ae", "ei", "ia", "io", "ey", "ya", "so", "lu",
            "ve", "se", "ke", "me", "ne", "le", "re", "te"
        )
        val middleSyllables = arrayOf(
            "so", "lo", "ro", "mo", "no", "si", "li", "ri", "mi", "ni",
            "sa", "la", "ra", "ma", "na", "ve", "le", "re", "me", "ne",
            "di", "da", "do", "ze", "za", "fa", "fe", "ta", "te", "to",
            "ka", "ki", "ke", "bi", "bo", "be", "ly", "my", "ny", "ry",
            "ae", "ei", "ia", "io"
        )
        val endingSyllables = arrayOf(
            "ra", "ria", "la", "na", "ya", "el", "iel", "li", "ri", "ni",
            "ly", "ny", "da", "ra", "la", "na", "lia", "nia", "ria",
            "lya", "nya", "ra", "la", "ya", "da", "ra", "la", "na"
        )
        repeat(20) {
            val useThreeSyllables = Math.random() < 0.4
            val opening = openingSyllables[(Math.random() * openingSyllables.size).toInt()]
            val ending = endingSyllables[(Math.random() * endingSyllables.size).toInt()]
            val name = if (useThreeSyllables) {
                val middle = middleSyllables[(Math.random() * middleSyllables.size).toInt()]
                if (opening == middle || middle == ending) return@repeat
                opening + middle + ending
            } else {
                if (opening == ending) return@repeat
                opening + ending
            }
            if (name.length in 4..8 && !regexThreeVowels.containsMatchIn(name) && !regexThreeConsonants.containsMatchIn(name)) {
                val formatted = name.replaceFirstChar { it.uppercase() }
                if (formatted.lowercase() !in blockedNames) return formatted
            }
        }
        return null
    }
}

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String?,
    val exitCode: Int,
)

internal fun shellQuote(arg: String): String =
    "'" + arg.replace("'", "'\\''") + "'"

class ShellController {

    suspend fun runCommand(command: String): ShellResult {
        val result = ShizukuFacade.runShellCommand(command)
        return ShellResult(
            success = result.success,
            output = result.output,
            error = result.error,
            exitCode = result.exitCode,
        )
    }
}

class VibrateController(private val context: Context) {

    fun vibrate() {
        vibrate(context)
    }

    fun vibrateWithPattern(pattern: String) {
        try {
            val parts = pattern.split(",").map { it.trim().toLong() }
            if (parts.isEmpty()) return
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(parts.toLongArray(), -1))
        } catch (_: Exception) {
            vibrate()
        }
    }
}
