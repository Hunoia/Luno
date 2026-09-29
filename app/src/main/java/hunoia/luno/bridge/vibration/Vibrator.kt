package hunoia.luno.bridge.vibration

import android.Manifest.permission.VIBRATE
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

@RequiresPermission(VIBRATE)
fun vibrate(context: Context) {
    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    val vibrator = vibratorManager.defaultVibrator
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
}
