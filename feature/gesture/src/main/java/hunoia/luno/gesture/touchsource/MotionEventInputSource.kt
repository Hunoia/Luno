package hunoia.luno.gesture.touchsource

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import hunoia.luno.gesture.MotionEventDispatcher
import hunoia.luno.gesture.OnMotionEventListener
import hunoia.luno.gesture.TouchWindowHint
import hunoia.luno.runtime.gesturepanel.GestureSessionPipeline

/**
 * 无障碍按钮窗来源：把 [MotionEventDispatcher] 投递的窗口事件归一成管线的 DOWN/MOVE/UP。
 *
 * 触钮只有这一条触摸路径（Shizuku 内核流只服务全屏长按识屏），因此这里不再做来源仲裁。
 * 判据是**窗口身份**（触摸回调携带的 [TouchWindowHint]）：动作面板 / 子手势等自家瞬态窗
 * 不带 hint，不会被当成触钮来源而起会话。
 *
 * 一次会话只跟踪起手的那根手指：多指时忽略后续 pointer 的按下/抬起，坐标固定取该 pointer 的
 * 原始值。否则 index 0 在第二指抬起时会切换，单帧位移直接变成两指间距（误触发滑动）。
 */
class MotionEventInputSource(
    private val pipeline: GestureSessionPipeline,
    private val timeProvider: () -> Long = SystemClock::uptimeMillis,
) {

    private var listener: OnMotionEventListener? = null
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    /** 本次会话跟踪的 pointer（起手那根）。 */
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    /** 本次会话是否由本来源起（自家瞬态窗的事件不得污染进行中的触钮会话）。 */
    private var ownsSession = false

    /** 上一次会话事件的时间：判定旧会话是否已经失联（UP/CANCEL 丢失）。 */
    private var lastEventAtMs = 0L

    fun start() {
        if (listener != null) return
        val l = OnMotionEventListener { event, source -> onEvent(event, source) }
        listener = l
        MotionEventDispatcher.addOnMotionEventListener(l)
    }

    fun stop() {
        runCatching { reset() }
        listener?.let(MotionEventDispatcher::removeOnMotionEventListener)
        listener = null
        clearSession()
    }

    /** 会话被打断（配置换基线、旧会话失联）：让管线收尾，避免半截会话里长按 job 到点仍触发动作。 */
    fun reset() {
        if (!ownsSession) return
        ownsSession = false
        clearSession()
        pipeline.onCancel()
    }

    private fun onEvent(event: MotionEvent, source: TouchWindowHint?) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event, source)
            // 多指：只认起手指针，其余 pointer 的按下/抬起不参与会话。
            MotionEvent.ACTION_POINTER_DOWN -> Unit
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) finishUp()
            }
            MotionEvent.ACTION_UP -> finishUp()
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> finishCancel()
        }
    }

    private fun finishUp() {
        clearSession()
        if (!ownsSession) return
        ownsSession = false
        pipeline.onUp()
    }

    private fun finishCancel() {
        clearSession()
        if (!ownsSession) return
        ownsSession = false
        pipeline.onCancel()
    }

    private fun onDown(event: MotionEvent, source: TouchWindowHint?) {
        val point = Offset(event.rawX, event.rawY)
        lastX = point.x
        lastY = point.y
        if (source == null) {
            // 自家瞬态窗（面板 / 子手势）接管了这次按下：进行中的触钮会话作废。
            reset()
            return
        }
        if (ownsSession) {
            // 会话保活：UP/CANCEL 一旦丢失（按钮窗被摘、系统边缘手势吞掉抬手），会话状态
            // 会永久卡住，之后每一次按下都被当成“会话进行中”丢弃 —— 表现是触钮彻底失效。
            // 超过阈值即按旧会话已死处理。
            if (timeProvider() - lastEventAtMs <= SESSION_STALE_MS) return
            ownsSession = false
            clearSession()
            pipeline.onCancel()
        }
        ownsSession = true
        activePointerId = event.getPointerId(0)
        lastEventAtMs = timeProvider()
        pipeline.onDown(point, source)
    }

    private fun onMove(event: MotionEvent) {
        if (!ownsSession || lastX.isNaN() || lastY.isNaN()) return
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        val point = Offset(event.getRawX(index), event.getRawY(index))
        val delta = Offset(point.x - lastX, point.y - lastY)
        lastX = point.x
        lastY = point.y
        lastEventAtMs = timeProvider()
        pipeline.onMove(delta)
    }

    private fun clearSession() {
        lastX = Float.NaN
        lastY = Float.NaN
        lastEventAtMs = 0L
        activePointerId = MotionEvent.INVALID_POINTER_ID
    }

    private companion object {
        /** 超过此时长没有任何会话事件，判定旧会话的 UP/CANCEL 已丢失。 */
        const val SESSION_STALE_MS = 10_000L
    }
}
