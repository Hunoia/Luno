package hunoia.luno.bridge.vibration

import android.Manifest.permission.VIBRATE
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

private var cachedVibrator: Vibrator? = null

private val clickEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)

@RequiresPermission(VIBRATE)
fun vibrate(context: Context) {
    // 进程级缓存：defaultVibrator 是跨进程获取，手势反馈的触发瞬间不该每次重付。
    val vibrator = cachedVibrator ?: run {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator.also { cachedVibrator = it }
    }
    vibrator.vibrate(clickEffect)
}
