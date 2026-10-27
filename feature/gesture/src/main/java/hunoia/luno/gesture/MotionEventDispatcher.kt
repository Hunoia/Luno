package hunoia.luno.gesture

import android.view.MotionEvent

object MotionEventDispatcher {

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<OnMotionEventListener>()

    fun addOnMotionEventListener(listener: OnMotionEventListener) {
        listeners.add(listener)
    }

    fun removeOnMotionEventListener(listener: OnMotionEventListener) {
        listeners.remove(listener)
    }

    fun dispatch(event: MotionEvent, source: TouchWindowHint? = null) {
        listeners.forEach { it.onDispatch(event, source) }
    }
}

/**
 * 事件来自哪个手势按钮窗。非 null 时 WMS 已完成命中测试，可直接采信，
 * 不必再按列表顺序做几何匹配（见 GestureButtonExt.findTouchTarget）。
 */
data class TouchWindowHint(val buttonId: String, val mirrored: Boolean)

fun interface OnMotionEventListener {

    fun onDispatch(event: MotionEvent, source: TouchWindowHint?)
}