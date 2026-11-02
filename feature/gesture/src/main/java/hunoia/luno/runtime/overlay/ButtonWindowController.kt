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

/**
 * 触钮窗口控制器：只负责把「可见性求值结果」落到窗口上。
 *
 * 可见性是纯函数求值（[ButtonVisibilityPolicy] + 软隐藏输入位），窗口侧的矩形、触摸 flags、
 * 系统手势排除区三者必须同步修改，统一走 [applyWindowState]。
 */
class ButtonWindowController(
    private val host: android.content.Context,
) {
    private val wm: WindowManager get() = host.windowManager()
    private val _buttonViews = mutableListOf<View>()
    val buttonViews: List<View> get() = _buttonViews.toList()

    /**
     * Surface 短态隐藏的输入位（标注/面板等）。
     *
     * 以前这里直接对窗口写 `FLAG_NOT_TOUCHABLE`，而 [updateVisibility] 每次都会按策略重推 flags，
     * 于是任意一次普通刷新都会把软隐藏撤销。现在它参与求值，恢复也走同一条反射路径。
     */
    private var softHidden = false

    fun replaceGestureButtons(buttons: Collection<GestureButton>) {
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "replace buttons: count=${buttons.size}")
        // 窗口按触发区摆放：触钮固定走无障碍按钮窗，没有“贴边窄条”之类的派生几何。
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
            // 软隐藏参与求值：刷新不能把短态隐藏撤销。
            val show = !softHidden && policy.shouldShow(target.sourceButton)
            applyWindowState(view, target, show)
        }
    }

    /**
     * 软隐藏输入位。hide 立即断触摸（防误触）；恢复只清输入位，
     * 真实显隐由调用方随后用 [updateVisibility] 重算，不在这里假定“应该显示”。
     */
    fun setSoftHidden(hidden: Boolean) {
        softHidden = hidden
        if (!hidden) return
        for (view in _buttonViews) {
            val lp = view.layoutParams as? WindowManager.LayoutParams ?: continue
            val before = lp.snapshot()
            WindowLayoutFactory.updateGestureButtonTouch(lp, touchEnabled = false)
            if (!lp.snapshot().contentEquals(before)) wm.safeUpdateViewLayout(view, lp)
        }
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "ButtonWindowController release: count=${_buttonViews.size}")
        host.removeWindows(_buttonViews)
        _buttonViews.clear()
        softHidden = false
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
                    // 保持当前触摸状态：只改几何，显隐由 updateVisibility 统一决定。
                    applyWindowState(view, target, show = lp.isTouchable())
                }
            }
        }

        existing.forEach { (_, view) -> host.removeWindow(view) }
        _buttonViews.removeAll { view ->
            existing.any { it.second === view }
        }
    }

    private fun attachGestureButton(target: GestureButtonWindowTarget): View? {
        // 初始一律不可点：真实显隐由紧随其后的 updateVisibility 求值决定，
        // 否则总开关关闭 / 场景隐藏的钮在 addView 到首次刷新之间有一小段可点窗口。
        val lp = WindowLayoutFactory.gestureButtonLayoutParams(touchEnabled = false)
        // addView 前先把几何写好：此时调 updateViewLayout 会落到尚未挂载的窗口上。
        WindowLayoutFactory.updateGestureButton(lp, target.windowButton)
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
                if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                // 窗口独占这条触摸流：不消费 DOWN 会让 MOVE/UP 的派发依赖 ViewGroup 的兜底行为。
                true
            }
        }
        if (!wm.safeAddView(view, lp)) return null
        // 挂窗后补齐排除区状态（隐藏态为空）；真实显隐由紧随其后的 updateVisibility 求值。
        applyWindowState(view, target, show = false)
        return view
    }

    /**
     * 窗口状态单点写入：矩形、触摸 flags、系统手势排除区三者同步。
     *
     * 排除区只在可点（show=true）时声明——隐藏态不应继续要求系统让位边缘手势。
     * addView 之前调用时 ViewRootImpl 首次 performTraversals 会收集；之后的变更由紧随其后的
     * updateViewLayout（内部 setLayout 会 scheduleTraversals）重新收集。
     */
    private fun applyWindowState(view: View, target: GestureButtonWindowTarget, show: Boolean) {
        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
        val before = lp.snapshot()
        WindowLayoutFactory.updateGestureButton(lp, target.windowButton)
        WindowLayoutFactory.updateGestureButtonTouch(lp, touchEnabled = show)
        val rects = if (show) listOf(Rect(0, 0, lp.width, lp.height)) else emptyList()
        if (view.systemGestureExclusionRects != rects) view.systemGestureExclusionRects = rects
        if (!lp.snapshot().contentEquals(before)) wm.safeUpdateViewLayout(view, lp)
    }
}

/** 同一个窗口：同一按钮的主窗口或镜像窗口。bounds 变化不影响身份。 */
private fun GestureButtonWindowTarget.sameWindowAs(other: GestureButtonWindowTarget): Boolean =
    sourceButton.id == other.sourceButton.id && mirrored == other.mirrored

private fun WindowManager.LayoutParams.isTouchable(): Boolean =
    (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0

private fun WindowManager.LayoutParams.snapshot(): IntArray =
    intArrayOf(flags, width, height, x, y)
