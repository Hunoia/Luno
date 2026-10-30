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
 * 全屏长按触发：按住到 [ScreenPickGesture.longPressDelayMs] 即触发。
 *
 * 关键语义：
 * - 长按时间到达即弹幽灵卡，不再要求“长按后再拖动”；
 * - 长按前移动超过 [ScreenPickGesture.slopDp] 视为滚动，本次作废；
 * - 触发后的 MOVE / UP 继续转发给 [onDrag] / [onRelease]，用于移动卡片；
 * - 两次触发之间按 [ScreenPickGesture.triggerCooldownMs] 冷却。
 *
 * 不消费触摸——底层应用照常收到这段按下。
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
    /** DOWN 时启动“菜单前快照”；默认空实现，保持纯状态机可测。 */
    private val onPreCapture: (x: Int, y: Int, downAtMs: Long) -> Unit = { _, _, _ -> },
    /** 会话作废/松手时丢弃快照。 */
    private val onPreCaptureCancel: () -> Unit = {},
) {

    private var pending: Cancelable? = null
    private var active = false
    private var fired = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var delayMs = 0L
    private var slopPx = 0f
    private var downAt = 0L
    private var lastFireAt = Long.MIN_VALUE / 2

    /** 幽灵卡会话是否仍在进行（已触发、未松手）。 */
    val isFired: Boolean get() = fired

    fun onFrame(point: Offset, action: TouchAction) {
        when (action) {
            TouchAction.Down -> onDown(point)
            TouchAction.Move -> if (fired) onDragFrame(point) else onMove(point)
            TouchAction.Up -> onUp(point)
        }
    }

    private fun onDown(point: Offset) {
        // 多指：第二根手指落下不重开会话（内核会给同一个主指再报一次 Down）。
        if (active) return
        reset()
        val snapshot = config()
        if (!snapshot.enabled) return
        // 冷却期内不重新武装，避免松手后轻微再按又弹卡。
        if (now() - lastFireAt < snapshot.triggerCooldownMs.coerceAtLeast(0)) return
        active = true
        anchorX = point.x
        anchorY = point.y
        downAt = now()
        onPreCapture(anchorX.toInt(), anchorY.toInt(), downAt)
        delayMs = snapshot.longPressDelayMs.toLong().coerceAtLeast(0L)
        slopPx = snapshot.slopDp * hunoia.luno.bridge.ui.DensityProvider.density
        pending = timer.schedule(delayMs) { fire() }
    }

    private fun onMove(point: Offset) {
        if (!active) return
        if (fired) {
            onDrag(point.x.toInt(), point.y.toInt())
            return
        }
        // 定时器可能被主线程延迟：MOVE 到达时已过长按时间就直接触发。
        if (now() - downAt >= delayMs) {
            fire()
            onDrag(point.x.toInt(), point.y.toInt())
            return
        }
        // 长按前移动超过容差 = 滚动，不是长按。
        if (distanceFromAnchor(point) > slopPx) reset()
    }

    private fun onDragFrame(point: Offset) {
        if (!active || !fired) return
        onDrag(point.x.toInt(), point.y.toInt())
    }

    private fun onUp(point: Offset) {
        val wasFired = fired
        reset()
        if (wasFired) onRelease(point.x.toInt(), point.y.toInt())
    }

    /** 长按时间到达即触发；落点用按下点。 */
    private fun fire() {
        if (!active || fired) return
        fired = true
        lastFireAt = now()
        pending?.cancel()
        pending = null
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, "screen pick long press fired at (${anchorX.toInt()}, ${anchorY.toInt()})")
        }
        onTrigger(anchorX.toInt(), anchorY.toInt())
    }

    private fun distanceFromAnchor(point: Offset): Double =
        hypot((point.x - anchorX).toDouble(), (point.y - anchorY).toDouble())

    private fun reset() {
        onPreCaptureCancel()
        pending?.cancel()
        pending = null
        active = false
        fired = false
        downAt = 0L
    }
}
