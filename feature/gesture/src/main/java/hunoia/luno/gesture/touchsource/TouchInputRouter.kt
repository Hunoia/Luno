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
    // 逐帧都会问几何（全局长按监听 / 每帧 MOVE），但按钮与拦截宽度只在配置变化时变。
    // 按实例缓存，命中同一份快照时不再重算矩形列表。
    private val buttonRectsCache by lazy(LazyThreadSafetyMode.NONE) {
        TouchInputGeometry.buttonRects(buttons, imePadding)
    }
    private val interceptRegionsCache by lazy(LazyThreadSafetyMode.NONE) {
        val shizukuIds = buttons
            .asSequence()
            .filter { it.touchInput.mode != hunoia.luno.config.model.TouchInputMode.ONLY_ACCESSIBILITY }
            .mapTo(HashSet()) { it.id }
        buttonRectsCache
            .filter { it.buttonId in shizukuIds }
            .map { InterceptRegion(it.buttonId, it.mirrored, it.rect) }
    }

    fun buttonRects(): List<TouchButtonRect> = buttonRectsCache

    /** 只有当内核触摸流在跑时才存在拦截区；否则没有像素需要被“吃掉”。 */
    fun interceptRegions(): List<InterceptRegion> =
        if (shizukuStreaming) interceptRegionsCache else emptyList()
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

    /**
     * 无障碍窗口投递的触摸能否采信。
     *
     * 判据是**窗口身份**（触摸回调携带的 [TouchWindowHint]），不再按屏幕坐标是否落在
     * 按钮矩形里过滤：窗口被系统重新摆放/多显示器偏移时，坐标可能落到矩形外，
     * 按几何过滤会把真实触摸丢掉。动作面板/子手势等自家瞬态窗不带 hint，
     * 因此不会被当成触钮来源而起会话。
     *
     * 拦截区仍必须排除：否则同一次按下会和内核流双触发。
     */
    fun acceptsWindow(point: Offset, hint: TouchWindowHint?): Boolean {
        if (hint == null) return false
        return TouchInputGeometry.contains(layoutProvider().interceptRegions(), point) == null
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
            TouchSourceKind.WINDOW -> acceptsWindow(point, hint)
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

    /** 本次会话是否由本来源起（自家瞬态窗的事件不得污染进行中的触钮会话）。 */
    private var ownsSession = false

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
        ownsSession = false
    }

    private fun onEvent(event: MotionEvent, source: TouchWindowHint?) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = Offset(event.rawX, event.rawY)
                lastX = point.x
                lastY = point.y
                ownsSession = router.onDown(TouchSourceKind.WINDOW, point, source)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!ownsSession || lastX.isNaN() || lastY.isNaN()) return
                val point = Offset(event.rawX, event.rawY)
                val delta = Offset(point.x - lastX, point.y - lastY)
                lastX = point.x
                lastY = point.y
                router.onMove(delta)
            }
            MotionEvent.ACTION_UP -> {
                lastX = Float.NaN
                lastY = Float.NaN
                if (!ownsSession) return
                ownsSession = false
                router.onUp()
            }
            MotionEvent.ACTION_CANCEL -> {
                lastX = Float.NaN
                lastY = Float.NaN
                if (!ownsSession) return
                ownsSession = false
                router.onCancel()
            }
        }
    }
}
