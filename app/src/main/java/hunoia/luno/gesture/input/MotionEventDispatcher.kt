package hunoia.luno.gesture.input

import android.view.MotionEvent

object MotionEventDispatcher {

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<OnMotionEventListener>()

    fun addOnMotionEventListener(listener: OnMotionEventListener) {
        listeners.add(listener)
    }

    fun removeOnMotionEventListener(listener: OnMotionEventListener) {
        listeners.remove(listener)
    }

    fun dispatch(event: MotionEvent) {
        listeners.forEach { it.onDispatch(event) }
    }
}

fun interface OnMotionEventListener {

    fun onDispatch(event: MotionEvent)
}