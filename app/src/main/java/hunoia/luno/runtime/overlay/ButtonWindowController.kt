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
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "replace buttons: count=${buttons.size}")
        val targets = buttons.flatMap { button ->
            buildList {
                add(GestureButtonWindowTarget(button, button))
                button.mirroredButton()?.let { mirrored ->
                    if (mirrored.bounds != button.bounds) {
                        add(GestureButtonWindowTarget(button, mirrored, mirrored = true))
                    }
                }
            }
        }
        syncWindowTargets(targets)
    }

    fun updateVisibility(policy: ButtonVisibilityPolicy) {
        for (view in _buttonViews) {
            val target = view.tag as? GestureButtonWindowTarget ?: continue
            val lp = view.layoutParams as? WindowManager.LayoutParams ?: continue
            val show = policy.shouldShow(target.sourceButton)
            val before = lp.snapshot()
            WindowLayoutFactory.updateGestureButton(lp, target.windowButton)
            lp.flags = if (show) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            // 只有真正变化才发跨进程 updateViewLayout，避免每次窗口事件都让 WMS 重新 layout
            if (lp.snapshot() != before) wm.updateViewLayout(view, lp)
        }
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "ButtonWindowController release: count=${_buttonViews.size}")
        host.removeWindows(_buttonViews)
        _buttonViews.clear()
    }

    /**
     * 只增删真正变化的窗口。触发距离这类参数变化时按钮列表内容不变，
     * 走 update 路径即可，不必全部 removeViewImmediate + addView 重建。
     */
    private fun syncWindowTargets(targets: List<GestureButtonWindowTarget>) {
        val existing = _buttonViews.mapNotNull { view ->
            (view.tag as? GestureButtonWindowTarget)?.let { it to view }
        }.toMutableList()

        targets.forEach { target ->
            val hit = existing.indexOfFirst { (tag, _) -> tag.sameWindowAs(target) }
            if (hit < 0) {
                attachGestureButton(target)?.let { _buttonViews.add(it) }
            } else {
                val (tag, view) = existing.removeAt(hit)
                view.tag = target
                val lp = view.layoutParams as? WindowManager.LayoutParams
                if (lp != null && tag.windowButton.bounds != target.windowButton.bounds) {
                    val before = lp.snapshot()
                    WindowLayoutFactory.updateGestureButton(lp, target.windowButton)
                    if (lp.snapshot() != before) wm.updateViewLayout(view, lp)
                }
            }
        }

        existing.forEach { (_, view) -> host.removeWindow(view) }
        _buttonViews.removeAll { view ->
            existing.any { it.second === view }
        }
    }

    private fun attachGestureButton(target: GestureButtonWindowTarget): View? {
        val lp = WindowLayoutFactory.gestureButtonLayoutParams(target.sourceButton.enabled).apply {
            WindowLayoutFactory.updateGestureButton(this, target.windowButton)
        }
        val view = View(host).apply {
            tag = target
            setOnTouchListener { v, event ->
                MotionEventDispatcher.dispatch(event)
                if (event.action == MotionEvent.ACTION_UP) v.performClick()
                false
            }
        }
        if (!wm.safeAddView(view, lp)) return null
        return view
    }
}

/** 同一个窗口：同一按钮的主窗口或镜像窗口。bounds 变化不影响身份。 */
private fun GestureButtonWindowTarget.sameWindowAs(other: GestureButtonWindowTarget): Boolean =
    sourceButton.id == other.sourceButton.id && mirrored == other.mirrored

private fun WindowManager.LayoutParams.snapshot(): IntArray =
    intArrayOf(flags, width, height, x, y)
