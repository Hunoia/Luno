package hunoia.luno.gesture.touchsource

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.DensityProvider
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
    private val screenWidth: Int = DensityProvider.screenWidthPx,
    private val screenHeight: Int = DensityProvider.screenHeightPx,
    private val density: Float = DensityProvider.density,
) {
    // 逐帧都会问几何（全局长按监听 / 每帧 MOVE），但按钮与拦截宽度只在配置变化时变。
    // 按实例缓存，命中同一份快照时不再重算矩形列表。
    private val buttonRectsCache by lazy(LazyThreadSafetyMode.NONE) {
        TouchInputGeometry.buttonRects(buttons, imePadding, screenWidth, screenHeight, density)
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

    /**
     * 拦截窗**要摆**的几何：不受 [shizukuStreaming] 门控。
     *
     * 摆放窗口不能用 [interceptRegions]：那个函数按“拦截窗已生效”过滤，而该标志的唯一来源
     * 正是“窗口是否摆上去了”。拿它当摆放输入会死锁——窗永远建不出来，内核触摸流永远拿不到
     * 辖区，触钮只能靠无障碍按钮窗硬扛系统边缘手势（表现就是边缘滑动时灵时不灵）。
     */
    fun desiredInterceptRegions(): List<InterceptRegion> = interceptRegionsCache

    /** 只有当内核触摸流在跑且拦截窗已生效时才存在拦截区；否则没有像素需要被“吃掉”。 */
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
    private val timeProvider: () -> Long = SystemClock::uptimeMillis,
) {

    @Volatile
    private var activeSource: TouchSourceKind? = null

    /** 上一次会话事件的时间：判定旧会话是否已经失联（UP/CANCEL 丢失）。 */
    @Volatile
    private var lastEventAtMs: Long = 0L

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
        val accepted = when (kind) {
            TouchSourceKind.WINDOW -> acceptsWindow(point, hint)
            TouchSourceKind.SHIZUKU -> acceptsShizuku(point) != null
        }
        if (!accepted) return false
        if (activeSource != null) {
            // 会话保活：UP/CANCEL 一旦丢失（按钮窗被摘、系统边缘手势吞掉抬手），activeSource
            // 会永久卡住，之后每一次按下都被当成“会话进行中”丢弃 —— 表现是触钮彻底失效，
            // 只有重启总开关（stop() → reset()）才恢复。超过阈值即按旧会话已死处理。
            if (timeProvider() - lastEventAtMs <= SESSION_STALE_MS) return false
            reset()
        }
        activeSource = kind
        lastEventAtMs = timeProvider()
        pipeline.onDown(point, hint)
        return true
    }

    fun onMove(delta: Offset) {
        if (activeSource == null) return
        lastEventAtMs = timeProvider()
        pipeline.onMove(delta)
    }

    fun onUp() {
        if (activeSource == null) return
        activeSource = null
        lastEventAtMs = 0L
        pipeline.onUp()
    }

    fun onCancel() {
        if (activeSource == null) return
        activeSource = null
        lastEventAtMs = 0L
        pipeline.onCancel()
    }

    /**
     * 会话被打断（输入源重启、配置换基线、旧会话失联）：复位仲裁并让管线收尾。
     * 只清 activeSource 会把半截会话留在管线里（长按 job 到点仍会触发动作），所以一并取消。
     */
    fun reset() {
        if (activeSource == null) return
        activeSource = null
        lastEventAtMs = 0L
        pipeline.onCancel()
    }

    private companion object {
        /** 超过此时长没有任何会话事件，判定旧会话的 UP/CANCEL 已丢失。 */
        const val SESSION_STALE_MS = 10_000L
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
