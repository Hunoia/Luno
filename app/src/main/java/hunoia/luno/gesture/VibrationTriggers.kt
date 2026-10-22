package hunoia.luno.gesture

import android.Manifest.permission.VIBRATE
import androidx.annotation.RequiresPermission
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.bridge.vibration.appContext
import hunoia.luno.bridge.vibration.vibrate

/** 按触发类型取震动开关。 */
private fun GestureButton.vibrateEnabledFor(type: GestureTriggerType): Boolean = when (type) {
    GestureTriggerType.Tap, GestureTriggerType.DoubleTap -> tapVibrate
    GestureTriggerType.LongPress -> longPressVibrate
    GestureTriggerType.Slide -> slideVibrate
    GestureTriggerType.SlideHold -> slideHoldVibrate
    GestureTriggerType.LongSlide -> longSlideVibrate
    GestureTriggerType.LongSlideHold -> longSlideHoldVibrate
}

private fun SubGesture.vibrateEnabledFor(type: GestureTriggerType): Boolean = when (type) {
    GestureTriggerType.SlideHold -> slideHoldVibrate
    GestureTriggerType.LongSlideHold -> longSlideHoldVibrate
    GestureTriggerType.LongSlide -> longSlideVibrate
    else -> slideVibrate
}

@RequiresPermission(VIBRATE)
fun GestureButton.tryVibrateFor(type: GestureTriggerType) {
    if (vibrateEnabledFor(type)) appContext?.let { vibrate(it) }
}

@RequiresPermission(VIBRATE)
fun SubGesture.tryVibrateFor(type: GestureTriggerType) {
    if (vibrateEnabledFor(type)) appContext?.let { vibrate(it) }
}

@RequiresPermission(VIBRATE)
fun vibrateForActionPanel(gestureSettings: GestureSettings) {
    if (gestureSettings.actionPanelVibrate) appContext?.let { vibrate(it) }
}
