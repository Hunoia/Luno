package hunoia.luno.runtime.overlay

import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.gesture.MotionEventDispatcher

class TransientOverlayController(
    private val host: android.content.Context,
) {
    private var actionPanelOverlayView: View? = null
    private var subGestureOverlayView: View? = null

    fun attachActionPanelOverlay() {
        if (actionPanelOverlayView != null) return
        val wm = ContextCompat.getSystemService(host, WindowManager::class.java)!!
        val lp = WindowLayoutFactory.actionPanelOverlayLayoutParams()
        val view = View(host).apply {
            setOnTouchListener { _, event ->
                MotionEventDispatcher.dispatch(event)
                true
            }
        }
        if (!wm.safeAddView(view, lp)) return
        actionPanelOverlayView = view
    }

    fun detachActionPanelOverlay() {
        val view = actionPanelOverlayView ?: return
        host.removeWindow(view)
        actionPanelOverlayView = null
    }

    fun attachSubGestureOverlay(center: Offset, radiusPx: Int) {
        val wm = ContextCompat.getSystemService(host, WindowManager::class.java)!!
        val lp = WindowLayoutFactory.subGestureLayoutParams(center, radiusPx)
        subGestureOverlayView?.let { view ->
            wm.safeUpdateViewLayout(view, lp)
            return
        }
        val view = View(host).apply {
            setOnTouchListener { _, event ->
                MotionEventDispatcher.dispatch(event)
                true
            }
        }
        if (!wm.safeAddView(view, lp)) return
        subGestureOverlayView = view
    }

    fun detachSubGestureOverlay() {
        val view = subGestureOverlayView ?: return
        host.removeWindow(view)
        subGestureOverlayView = null
    }

    fun detachAll() {
        detachActionPanelOverlay()
        detachSubGestureOverlay()
    }
}
