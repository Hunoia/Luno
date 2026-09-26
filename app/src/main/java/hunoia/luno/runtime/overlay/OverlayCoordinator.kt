package hunoia.luno.runtime.overlay

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import hunoia.luno.BuildConfig
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.button.ButtonVisibilityPolicy

class OverlayCoordinator(
    private val host: GestureHost,
) {
    val mainOverlayController = MainOverlayController(host)
    val buttonWindowController = ButtonWindowController(host.context)
    val transientOverlayController = TransientOverlayController(host.context)

    fun replaceMainOverlay(content: @Composable () -> Unit) {
        mainOverlayController.replaceContent(content)
    }

    fun updateMainLayout() {
        mainOverlayController.updateLayout()
    }

    fun replaceGestureButtons(buttons: Collection<hunoia.luno.config.model.GestureButton>) {
        buttonWindowController.replaceGestureButtons(buttons)
    }

    fun updateButtonVisibility(policy: ButtonVisibilityPolicy) {
        buttonWindowController.updateVisibility(policy)
    }

    fun attachActionPanelOverlay() {
        transientOverlayController.attachActionPanelOverlay()
    }

    fun detachActionPanelOverlay() {
        transientOverlayController.detachActionPanelOverlay()
    }

    fun attachSubGestureOverlay(center: Offset, radiusPx: Int) {
        transientOverlayController.attachSubGestureOverlay(center, radiusPx)
    }

    fun detachSubGestureOverlay() {
        transientOverlayController.detachSubGestureOverlay()
    }

    fun detachTransientOverlays() {
        transientOverlayController.detachAll()
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "OverlayCoordinator release")
        mainOverlayController.release()
        buttonWindowController.release()
        transientOverlayController.detachAll()
    }
}
