package hunoia.luno.runtime.overlay

import android.view.View
import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.gesture.MotionEventDispatcher

class TransientOverlayController(
    private val host: android.content.Context,
) {
    private var actionPanelOverlayView: View? = null
    private var subGestureOverlayView: View? = null

    fun attachActionPanelOverlay() {
        if (actionPanelOverlayView != null) return
        val wm = host.windowManager()
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

    /** @return 是否真的拆掉了一个窗（供销毁路径补发配对的 PanelDismissed 用）。 */
    fun detachActionPanelOverlay(): Boolean {
        val view = actionPanelOverlayView ?: return false
        host.removeWindow(view)
        actionPanelOverlayView = null
        return true
    }

    fun attachSubGestureOverlay(center: Offset, radiusPx: Int) {
        val wm = host.windowManager()
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

    /** @return 是否真的拆掉了一个窗（供销毁路径补发配对的 PanelDismissed 用）。 */
    fun detachSubGestureOverlay(): Boolean {
        val view = subGestureOverlayView ?: return false
        host.removeWindow(view)
        subGestureOverlayView = null
        return true
    }

    /** 拆掉所有瞬态浮层，返回拆掉的数量。 */
    fun detachAll(): Int {
        var count = 0
        if (detachActionPanelOverlay()) count++
        if (detachSubGestureOverlay()) count++
        return count
    }
}
