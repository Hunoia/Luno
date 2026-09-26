package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureSettings

interface GestureOverlayCallbacks {
    fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int)
    fun onActionPanelOverlayChanged(show: Boolean)
    fun onAction(action: Action, sourceButton: GestureButton?, sourceOverride: GestureButtonActionSettingsOverride?)
    fun onPointerStart(settings: GestureSettings.Pointer): Boolean
    fun onPointerShow(settings: GestureSettings.Pointer): Boolean
    fun onPointerEnd()
    fun onPointerActionAtPosition(x: Int, y: Int, keepActive: Boolean)
}
