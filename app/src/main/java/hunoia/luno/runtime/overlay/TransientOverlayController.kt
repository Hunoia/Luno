package hunoia.luno.runtime.overlay

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import hunoia.luno.bridge.window.removeWindow
import hunoia.luno.gesture.input.MotionEventDispatcher

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
        wm.addView(view, lp)
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
            try { wm.updateViewLayout(view, lp) } catch (_: Exception) { }
            return
        }
        val view = View(host).apply {
            setOnTouchListener { _, event ->
                MotionEventDispatcher.dispatch(event)
                true
            }
        }
        wm.addView(view, lp)
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
