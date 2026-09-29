package hunoia.luno.action.controller

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import hunoia.luno.bridge.vibration.vibrate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
