package hunoia.luno.runtime.overlay

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import hunoia.luno.BuildConfig
import hunoia.luno.bridge.window.removeWindow
import hunoia.luno.bridge.window.removeWindows
import hunoia.luno.config.model.GestureButton
import hunoia.luno.gesture.input.MotionEventDispatcher
import hunoia.luno.runtime.button.ButtonVisibilityPolicy
import hunoia.luno.gesture.mirroredButton

class ButtonWindowController(
    private val host: android.content.Context,
) {
    private val wm: WindowManager get() = ContextCompat.getSystemService(host, WindowManager::class.java)!!
    private val _buttonViews = mutableListOf<View>()
    val buttonViews: List<View> get() = _buttonViews.toList()

    fun replaceGestureButtons(buttons: Collection<GestureButton>) {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "replace buttons: count=${buttons.size}")
        host.removeWindows(_buttonViews)
        _buttonViews.clear()
        for (button in buttons) {
            _buttonViews.add(attachGestureButton(GestureButtonWindowTarget(button, button)))
            button.mirroredButton()?.let { mirrored ->
                if (mirrored.bounds != button.bounds) {
                    _buttonViews.add(attachGestureButton(GestureButtonWindowTarget(button, mirrored)))
                }
            }
        }
    }

    fun updateVisibility(policy: ButtonVisibilityPolicy) {
        for (view in _buttonViews) {
            val target = view.tag as? GestureButtonWindowTarget ?: continue
            val lp = view.layoutParams as WindowManager.LayoutParams
            WindowLayoutFactory.updateGestureButton(lp, target.windowButton)
            lp.flags = if (policy.shouldShow(target.sourceButton)) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            try { wm.updateViewLayout(view, lp) } catch (_: Exception) { }
        }
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "ButtonWindowController release: count=${_buttonViews.size}")
        host.removeWindows(_buttonViews)
        _buttonViews.clear()
    }

    private fun attachGestureButton(target: GestureButtonWindowTarget): View {
        val lp = WindowLayoutFactory.gestureButtonLayoutParams(target.sourceButton.enabled).apply {
            WindowLayoutFactory.updateGestureButton(this, target.windowButton)
        }
        return View(host).apply {
            tag = target
            setOnTouchListener { v, event ->
                MotionEventDispatcher.dispatch(event)
                if (event.action == MotionEvent.ACTION_UP) v.performClick()
                false
            }
            wm.addView(this, lp)
        }
    }
}
