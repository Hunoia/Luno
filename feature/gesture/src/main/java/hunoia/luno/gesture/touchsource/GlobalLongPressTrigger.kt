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
 * 全屏长按触发：先按住到 [ScreenPickGesture.longPressDelayMs]，再在
 * [ScreenPickGesture.dragStartTimeoutMs] 内拖动超过 [ScreenPickGesture.slopDp] 才触发。
 *
 * 为什么必须是**定时器**而不是“等下一帧再算时间差”：手指按住不动时内核流没有新帧，
 * 帧驱动判定会一直等不到下一次回调，长按永远不触发（旧实现的 bug）。
 *
 * 关键语义（2026-10-29 修正）：
 * - 只长按、不拖动 → 到等待窗口结束就作废，**不弹幽灵卡**；
 * - 长按后拖动超过 slop → 触发，并继续转发 MOVE / UP 给 [onDrag] / [onRelease]；
 * - 长按前就开始拖动 → 不是长按，直接作废；
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
) {

    private var pending: Cancelable? = null
    private var dragWindow: Cancelable? = null
    private var active = false
    private var armed = false
    private var fired = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var delayMs = 0L
    private var slopPx = 0f
    private var downAt = 0L
    private var dragStartTimeoutMs = 0L
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
        delayMs = snapshot.longPressDelayMs.toLong().coerceAtLeast(0L)
        slopPx = snapshot.slopDp * hunoia.luno.bridge.ui.DensityProvider.density
        dragStartTimeoutMs = snapshot.dragStartTimeoutMs.toLong().coerceAtLeast(0L)
        pending = timer.schedule(delayMs) { arm() }
    }

    /** 达到长按时间：进入“等待拖动”窗口，本身不弹卡。 */
    private fun arm() {
        if (!active || fired || armed) return
        armed = true
        if (dragStartTimeoutMs <= 0L) {
            // 0 = 长按即触发（保留旧手感，供不想等待拖动的用户选择）。
            fire()
            return
        }
        dragWindow = timer.schedule(dragStartTimeoutMs) {
            // 窗口内没拖动：本次长按作废，不触发。
            if (active && !fired) reset()
        }
    }

    private fun onMove(point: Offset) {
        if (!active) return
        if (!armed && now() - downAt >= delayMs) arm()
        // dragStartTimeoutMs=0 时 arm() 会直接触发；本次 MOVE 仍要转发。
        if (fired) {
            onDrag(point.x.toInt(), point.y.toInt())
            return
        }
        val distance = distanceFromAnchor(point)
        if (distance > slopPx) {
            if (armed) {
                fire()
                onDrag(point.x.toInt(), point.y.toInt())
            } else {
                // 长按窗口已过 / 长按前就拖动：不是“长按后拖”，作废。
                reset()
            }
        }
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

    /** 真正触发：落点用按下点；后续 MOVE 继续转发。 */
    private fun fire() {
        if (!active || fired || !armed) return
        fired = true
        lastFireAt = now()
        dragWindow?.cancel()
        dragWindow = null
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, "screen pick long press+drag fired at (${anchorX.toInt()}, ${anchorY.toInt()})")
        }
        onTrigger(anchorX.toInt(), anchorY.toInt())
    }

    private fun distanceFromAnchor(point: Offset): Double =
        hypot((point.x - anchorX).toDouble(), (point.y - anchorY).toDouble())

    private fun reset() {
        pending?.cancel()
        pending = null
        dragWindow?.cancel()
        dragWindow = null
        active = false
        armed = false
        fired = false
        downAt = 0L
    }
}
