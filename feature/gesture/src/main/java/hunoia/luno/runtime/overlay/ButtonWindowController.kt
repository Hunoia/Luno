package hunoia.luno.runtime.overlay

import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import hunoia.luno.gesture.BuildConfig
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.removeWindows
import hunoia.luno.config.model.GestureButton
import hunoia.luno.gesture.MotionEventDispatcher
import hunoia.luno.gesture.TouchWindowHint
import hunoia.luno.runtime.button.ButtonVisibilityPolicy
import hunoia.luno.gesture.mirroredButton
import hunoia.luno.core.LOG_TAG

class ButtonWindowController(
    private val host: android.content.Context,
) {
    private val wm: WindowManager get() = host.windowManager()
    private val _buttonViews = mutableListOf<View>()
    val buttonViews: List<View> get() = _buttonViews.toList()

    fun replaceGestureButtons(buttons: Collection<GestureButton>) {
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "replace buttons: count=${buttons.size}")
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
            applyBounds(view, lp, target.windowButton)
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
            if (!lp.snapshot().contentEquals(before)) wm.safeUpdateViewLayout(view, lp)
        }
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "ButtonWindowController release: count=${_buttonViews.size}")
        host.removeWindows(_buttonViews)
        _buttonViews.clear()
    }

    /** 只增删真正变化的窗口：触发距离这类参数变化时列表内容不变，走 update 路径，不必全部重建。 */
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
                    applyBounds(view, lp, target.windowButton)
                    if (!lp.snapshot().contentEquals(before)) wm.safeUpdateViewLayout(view, lp)
                }
            }
        }

        existing.forEach { (_, view) -> host.removeWindow(view) }
        _buttonViews.removeAll { view ->
            existing.any { it.second === view }
        }
    }

    private fun attachGestureButton(target: GestureButtonWindowTarget): View? {
        val lp = WindowLayoutFactory.gestureButtonLayoutParams(target.sourceButton.enabled)
        val view = View(host).apply {
            tag = target
            setOnTouchListener { v, event ->
                if (BuildConfig.DEBUG && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    Log.i(
                        LOG_TAG,
                        "gesture down: mirror=${target.mirrored} raw=(${event.rawX},${event.rawY}) " +
                            "win=[${lp.x},${lp.y}][${lp.x + lp.width},${lp.y + lp.height}]",
                    )
                }
                MotionEventDispatcher.dispatch(
                    event,
                    TouchWindowHint(target.sourceButton.id, target.mirrored),
                )
                if (event.action == MotionEvent.ACTION_UP) v.performClick()
                false
            }
        }
        applyBounds(view, lp, target.windowButton)
        if (!wm.safeAddView(view, lp)) return null
        return view
    }

    /**
     * 窗口矩形与手势排除区必须同步更新。排除区用视图局部坐标填满整条带，绝对坐标 = 窗口位置 + 局部坐标；
     * 不声明的话条带与系统返回手势区是同一片像素且无仲裁，SystemUI 会在 ACTION_DOWN 就启动返回手势。
     * addView 之前设置（ViewRootImpl 首次 performTraversals 时收集）；之后的变更靠紧随其后的 updateViewLayout
     * （内部 setLayout 会 scheduleTraversals）重新收集，故调用点必须保证后面紧跟一次 safeUpdateViewLayout。
     */
    private fun applyBounds(view: View, lp: WindowManager.LayoutParams, button: GestureButton) {
        WindowLayoutFactory.updateGestureButton(lp, button)
        val rects = listOf(Rect(0, 0, lp.width, lp.height))
        if (view.systemGestureExclusionRects != rects) view.systemGestureExclusionRects = rects
    }
}

/** 同一个窗口：同一按钮的主窗口或镜像窗口。bounds 变化不影响身份。 */
private fun GestureButtonWindowTarget.sameWindowAs(other: GestureButtonWindowTarget): Boolean =
    sourceButton.id == other.sourceButton.id && mirrored == other.mirrored

private fun WindowManager.LayoutParams.snapshot(): IntArray =
    intArrayOf(flags, width, height, x, y)
