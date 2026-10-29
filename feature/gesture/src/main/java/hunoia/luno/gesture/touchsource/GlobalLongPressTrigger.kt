package hunoia.luno.gesture.touchsource

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.ScreenPickGesture
import hunoia.luno.core.LOG_TAG
import hunoia.luno.gesture.BuildConfig
import hunoia.luno.gesture.touch.TouchAction
import kotlin.math.hypot

/** 可替换的定时器：生产实现挂在主线程，JVM 测试用假时钟手动推进。 */
fun interface LongPressTimer {
    fun schedule(delayMs: Long, task: () -> Unit): Cancelable
}

fun interface Cancelable {
    fun cancel()
}

/** 主线程 Handler 定时器：手势会话的 job/震动/面板都按主线程写，这里不另起线程。 */
class MainThreadTimer : LongPressTimer {
    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(delayMs: Long, task: () -> Unit): Cancelable {
        val runnable = Runnable { task() }
        handler.postDelayed(runnable, delayMs)
        return Cancelable { handler.removeCallbacks(runnable) }
    }
}

/**
 * 全屏长按触发：内核触摸流每帧喂进来，按住到阈值且位移未超 slop 才回调一次。
 *
 * 为什么必须是**定时器**而不是“等下一帧再算时间差”：手指按住不动时内核流没有新帧，
 * 帧驱动判定会一直等不到下一次回调，长按永远不触发（旧实现的 bug）。
 *
 * 触发后本对象继续转发后续 MOVE / UP 给 [onDrag] / [onRelease]：幽灵卡要靠同一段手势
 * 跟随手指并判断“松手落在哪个动作上”。不消费触摸——底层应用照常收到这段按下。
 *
 * @param config 每次按下时取一次快照；中途改配置不影响进行中的会话。
 */
class GlobalLongPressTrigger(
    private val config: () -> ScreenPickGesture,
    private val now: () -> Long = SystemClock::uptimeMillis,
    private val timer: LongPressTimer = MainThreadTimer(),
    private val onTrigger: (x: Int, y: Int) -> Unit,
    private val onDrag: (x: Int, y: Int) -> Unit = { _, _ -> },
    private val onRelease: (x: Int, y: Int) -> Unit = { _, _ -> },
) {

    private var pending: Cancelable? = null
    private var active = false
    private var fired = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var delayMs = 0L
    private var slopPx = 0f
    private var downAt = 0L

    /** 幽灵卡会话是否仍在进行（已触发、未松手）。 */
    val isFired: Boolean get() = fired

    fun onFrame(point: Offset, action: TouchAction) {
        when (action) {
            TouchAction.Down -> onDown(point)
            TouchAction.Move -> if (fired) onDrag(point.x.toInt(), point.y.toInt()) else onMove(point)
            TouchAction.Up -> onUp(point)
        }
    }

    private fun onDown(point: Offset) {
        // 多指：第二根手指落下不重开会话（内核会给同一个主指再报一次 Down）。
        if (active) return
        reset()
        val snapshot = config()
        if (!snapshot.enabled) return
        active = true
        anchorX = point.x
        anchorY = point.y
        downAt = now()
        delayMs = snapshot.longPressDelayMs.toLong().coerceAtLeast(0L)
        slopPx = snapshot.slopDp * hunoia.luno.bridge.ui.DensityProvider.density
        pending = timer.schedule(delayMs) { fire() }
    }

    private fun onMove(point: Offset) {
        if (!active) return
        val distance = hypot((point.x - anchorX).toDouble(), (point.y - anchorY).toDouble())
        if (distance > slopPx) {
            // 超 slop = 用户在拖动，不是长按；本次会话作废，交给手势管线/底层应用。
            reset()
            return
        }
        if (now() - downAt >= delayMs) fire()
    }

    private fun onUp(point: Offset) {
        val wasFired = fired
        reset()
        if (wasFired) onRelease(point.x.toInt(), point.y.toInt())
    }

    /** 定时器到期：仍处于按下态且未触发过才回调，落点用**按下点**而不是当前点。 */
    private fun fire() {
        if (!active || fired) return
        fired = true
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "screen pick long press fired at (${anchorX.toInt()}, ${anchorY.toInt()})")
        onTrigger(anchorX.toInt(), anchorY.toInt())
    }

    private fun reset() {
        pending?.cancel()
        pending = null
        active = false
        fired = false
        downAt = 0L
    }
}
