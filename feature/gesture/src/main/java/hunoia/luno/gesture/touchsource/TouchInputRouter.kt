package hunoia.luno.gesture.touchsource

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButton
import hunoia.luno.gesture.MotionEventDispatcher
import hunoia.luno.gesture.OnMotionEventListener
import hunoia.luno.gesture.TouchWindowHint
import hunoia.luno.runtime.gesturepanel.GestureSessionPipeline

/**
 * 判定来源所需的当前布局。任何一项变化都要重算，否则会出现“窗动了、判定没动”。
 *
 * [shizukuStreaming] 表示内核触摸流真的在跑且拦截窗已生效；false 时整块按钮区都归窗口，
 * 这样 Shizuku 掉线/没有悬浮窗权限时触钮不会失效。
 */
data class TouchInputLayout(
    val buttons: List<GestureButton> = emptyList(),
    val shizukuStreaming: Boolean = false,
    val imePadding: Int = 0,
) {
    fun buttonRects(): List<TouchButtonRect> =
        TouchInputGeometry.buttonRects(buttons, imePadding)

    /** 只有当内核触摸流在跑时才存在拦截区；否则没有像素需要被“吃掉”。 */
    fun interceptRegions(): List<InterceptRegion> =
        if (shizukuStreaming) {
            TouchInputGeometry.interceptRegions(buttons, imePadding)
        } else {
            emptyList()
        }
}

/**
 * 来源仲裁：决定这一帧归谁，并把归一后的 DOWN/MOVE/UP 投给唯一的手势会话管线。
 *
 * 为什么需要它：`getevent` 看得见**全屏**触摸，无障碍按钮窗也会收到自己那块。
 * 两个来源都能看到同一次按下，若各自起会话，就会一个动作触发两次。
 * 裁决口径：
 * - 拦截区内的触摸归 Shizuku（拦截窗在 WMS 层级上更高，实际也只会送到它）；
 * - 按钮区其余像素归无障碍窗口；
 * - 一次会话一旦归属确定，后续 MOVE/UP 只认同一来源。
 */
class TouchInputRouter(
    private val pipeline: GestureSessionPipeline,
    private val layoutProvider: () -> TouchInputLayout,
) {

    @Volatile
    private var activeSource: TouchSourceKind? = null

    val currentSource: TouchSourceKind? get() = activeSource

    fun layout(): TouchInputLayout = layoutProvider()

    /** 无障碍窗口投递的触摸能否采信（几何上是本应用按钮区，且不在 Shizuku 辖区）。 */
    fun acceptsWindow(point: Offset): Boolean {
        val layout = layoutProvider()
        if (TouchInputGeometry.containsButton(layout.buttonRects(), point) == null) return false
        return TouchInputGeometry.contains(layout.interceptRegions(), point) == null
    }

    /** 内核触摸帧是否落在 Shizuku 辖区。 */
    fun acceptsShizuku(point: Offset): InterceptRegion? =
        TouchInputGeometry.contains(layoutProvider().interceptRegions(), point)

    fun onDown(
        kind: TouchSourceKind,
        point: Offset,
        hint: TouchWindowHint? = null,
    ): Boolean {
        if (activeSource != null) return false
        val accepted = when (kind) {
            TouchSourceKind.WINDOW -> acceptsWindow(point)
            TouchSourceKind.SHIZUKU -> acceptsShizuku(point) != null
        }
        if (!accepted) return false
        activeSource = kind
        pipeline.onDown(point, hint)
        return true
    }

    fun onMove(delta: Offset) {
        if (activeSource == null) return
        pipeline.onMove(delta)
    }

    fun onUp() {
        if (activeSource == null) return
        activeSource = null
        pipeline.onUp()
    }

    fun onCancel() {
        if (activeSource == null) return
        activeSource = null
        pipeline.onCancel()
    }

    /** 会话被打断（输入源重启、配置换基线）：强制复位，避免下一次 DOWN 被旧会话挡住。 */
    fun reset() {
        activeSource = null
    }
}

/**
 * 无障碍按钮窗来源：把 `MotionEventDispatcher` 的原始事件归一成管线的 DOWN/MOVE/UP。
 *
 * 原来这段逻辑在 `DragGestureHandler`（Compose 里），现在它与 Shizuku 源共用同一套入口，
 * 因此必须自己做来源仲裁——否则两个来源各自起会话。
 */
class MotionEventInputSource(
    private val router: TouchInputRouter,
) {

    private var listener: OnMotionEventListener? = null
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    fun start() {
        if (listener != null) return
        val l = OnMotionEventListener { event, source -> onEvent(event, source) }
        listener = l
        MotionEventDispatcher.addOnMotionEventListener(l)
    }

    fun stop() {
        listener?.let(MotionEventDispatcher::removeOnMotionEventListener)
        listener = null
        lastX = Float.NaN
        lastY = Float.NaN
    }

    private fun onEvent(event: MotionEvent, source: TouchWindowHint?) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = Offset(event.rawX, event.rawY)
                lastX = point.x
                lastY = point.y
                router.onDown(TouchSourceKind.WINDOW, point, source)
            }
            MotionEvent.ACTION_MOVE -> {
                if (lastX.isNaN() || lastY.isNaN()) return
                val point = Offset(event.rawX, event.rawY)
                val delta = Offset(point.x - lastX, point.y - lastY)
                lastX = point.x
                lastY = point.y
                router.onMove(delta)
            }
            MotionEvent.ACTION_UP -> {
                lastX = Float.NaN
                lastY = Float.NaN
                router.onUp()
            }
            MotionEvent.ACTION_CANCEL -> {
                lastX = Float.NaN
                lastY = Float.NaN
                router.onCancel()
            }
        }
    }
}
