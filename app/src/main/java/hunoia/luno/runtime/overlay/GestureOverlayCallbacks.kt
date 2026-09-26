package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride

interface GestureOverlayCallbacks {
    fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int)
    fun onActionPanelOverlayChanged(show: Boolean)
    fun onAction(action: Action, sourceButton: GestureButton?, sourceOverride: GestureButtonActionSettingsOverride?)
}
