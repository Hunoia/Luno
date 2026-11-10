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

/**
 * 子手势的「会话反馈」振动：与方向动作的 [SubGesture.tryVibrateFor] 分开——
 * 那四个是「拖到某个方向时」响，这几个是「会话本身的生命周期节点」响。
 * 入口两个开关按**手指状态**分，而不是按入口名字分：按着进 = holdFire，抬起后进 = overlay。
 */
@RequiresPermission(VIBRATE)
fun SubGesture.tryVibrateHoldFire() {
    if (holdFireVibrate) appContext?.let { vibrate(it) }
}

@RequiresPermission(VIBRATE)
fun SubGesture.tryVibrateOverlay() {
    if (overlayVibrate) appContext?.let { vibrate(it) }
}

@RequiresPermission(VIBRATE)
fun SubGesture.tryVibratePress() {
    if (pressVibrate) appContext?.let { vibrate(it) }
}

@RequiresPermission(VIBRATE)
fun SubGesture.tryVibrateTimeout() {
    if (timeoutVibrate) appContext?.let { vibrate(it) }
}
