package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import hunoia.luno.config.model.ActionPanelStyles
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.LongSlideActionPanelStyles
import hunoia.luno.config.model.GestureDirection

object GestureFacade {

    fun bounds(button: GestureButton, imePadding: Int = 0): Rect =
        button.bounds(imePadding)

    fun find(buttons: List<GestureButton>, offset: Offset, imePadding: Int = 0): GestureButton? =
        buttons.find(offset, imePadding)

    fun findTouchTarget(buttons: List<GestureButton>, offset: Offset, imePadding: Int = 0): GestureTouchTarget? =
        buttons.findTouchTarget(offset, imePadding)

    fun styleBy(styles: LongSlideActionPanelStyles, direction: GestureDirection): ActionPanelStyles =
        styles.styleBy(direction)

    fun vibrateForActionPanel(gestureSettings: GestureSettings) {
        hunoia.luno.gesture.vibrateForActionPanel(gestureSettings)
    }
}
