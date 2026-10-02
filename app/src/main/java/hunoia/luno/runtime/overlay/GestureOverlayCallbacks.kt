package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride

interface GestureOverlayCallbacks {
    fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int)
    fun onActionPanelOverlayChanged(show: Boolean)

    /**
     * ActionPanel 是否正在显示。用于把主 overlay 在隐藏时收成 1x1,
     * 避免常驻一块什么都不画的全屏 RGBA_8888 buffer。与 [onActionPanelOverlayChanged] 是两件事:
     * 后者管的是面板需要交互时那个临时的可触摸空窗口。
     */
    fun onActionPanelVisibilityChanged(visible: Boolean)

    fun onAction(action: Action, sourceButton: GestureButton?, sourceOverride: GestureButtonActionSettingsOverride?)
}
