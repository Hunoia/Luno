package hunoia.luno.gesture

import android.Manifest.permission.VIBRATE
import androidx.annotation.RequiresPermission
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.SubGesture
import hunoia.luno.bridge.vibration.DEFAULT_VIBRATION_EFFECT
import hunoia.luno.bridge.vibration.DEFAULT_VIBRATION_MS
import hunoia.luno.bridge.vibration.appContext
import hunoia.luno.bridge.vibration.vibrate

@RequiresPermission(VIBRATE)
fun GestureButton.tryVibrateForSlide() {
    val ctx = appContext ?: return
    if (slideVibrate) {
        vibrate(ctx, vibrationEffect, customVibrationMs)
    }
}

@RequiresPermission(VIBRATE)
fun GestureButton.tryVibrateForLongSlide() {
    val ctx = appContext ?: return
    if (longSlideVibrate) {
        vibrate(ctx, vibrationEffect, customVibrationMs)
    }
}

@RequiresPermission(VIBRATE)
fun GestureButton.tryVibrateForTap() {
    val ctx = appContext ?: return
    if (tapVibrate) {
        vibrate(ctx, vibrationEffect, customVibrationMs)
    }
}

@RequiresPermission(VIBRATE)
fun GestureButton.tryVibrateForLongPress() {
    val ctx = appContext ?: return
    if (longPressVibrate) {
        vibrate(ctx, vibrationEffect, customVibrationMs)
    }
}

@RequiresPermission(VIBRATE)
fun SubGesture.tryVibrateForSlide() {
    val ctx = appContext ?: return
    if (slideVibrate) {
        vibrate(ctx, vibrationEffect, customVibrationMs)
    }
}

@RequiresPermission(VIBRATE)
fun SubGesture.tryVibrateForLongSlide() {
    val ctx = appContext ?: return
    if (longSlideVibrate) {
        vibrate(ctx, vibrationEffect, customVibrationMs)
    }
}

@RequiresPermission(VIBRATE)
fun vibrateForActionPanel(gestureSettings: GestureSettings) {
    val ctx = appContext ?: return
    if (gestureSettings.actionPanelVibrate) {
        vibrate(ctx, DEFAULT_VIBRATION_EFFECT, DEFAULT_VIBRATION_MS)
    }
}
