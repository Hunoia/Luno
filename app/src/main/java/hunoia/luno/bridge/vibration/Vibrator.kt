package hunoia.luno.bridge.vibration

import android.Manifest.permission.VIBRATE
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

val DEFAULT_VIBRATION_EFFECT = VibrationEffects.Click
const val DEFAULT_VIBRATION_MS = 50L
const val MinCustomVibrationMs = 0L
const val MaxCustomVibrationMs = 100L

@RequiresPermission(VIBRATE)
fun vibrate(context: Context, effect: VibrationEffects, customVibrationMs: Long) {
    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    val vibrator = vibratorManager.defaultVibrator
    if (effect != VibrationEffects.None) {
        val ve = when (effect) {
            VibrationEffects.Tick -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            VibrationEffects.Click -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            VibrationEffects.HeavyClick -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            VibrationEffects.None -> error("Stub!")
        }
        vibrator.vibrate(ve)
    } else if (customVibrationMs > 0) {
        val ve = VibrationEffect.createOneShot(customVibrationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(ve)
    }
}
