package hunoia.luno.gesture.touchsource

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.GlobalGestureKind
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.GlobalGestureTrigger
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.allowsFingers
import hunoia.luno.config.model.isDirectional
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gesture.GestureConfig
import hunoia.luno.gesture.GestureDecision
import hunoia.luno.gesture.GestureRecognizer
import hunoia.luno.gesture.GestureThresholds
import hunoia.luno.gesture.touch.TouchAction
import kotlin.math.hypot

/**
 * 一次点火：哪条区、哪条触发、执行什么、落点在哪。
 *
 * [anchor] 是按下点（识屏幽灵卡的锚点），[position] 是点火瞬间的手指位置。
 */
data class GlobalGestureHit(
    val zone: GlobalGestureZone,
    val trigger: GlobalGestureTrigger,
    val action: StoredAction,
    val anchor: Offset,
    val position: Offset,
    /** 本次手势会话 id：执行侧用它取回按下瞬间的预捕获快照。 */
    val sessionId: String,
)

/**
 * 识别区手势会话：内核触摸流 → 区命中 → 触发判定。
 *
 * 与触钮的关系：**复用同一套识别策略**（[GestureRecognizer] / `GestureStrategyRegistry`），
 * 但会话是独立的 —— 触钮用窗口坐标与按钮命中，识别区用屏幕绝对坐标与区命中，
 * 两者的输入源、生命周期与动作绑定都不同，不共用管线。
 *
 * 关键语义：
 * - 一次按下只点火一条触发（区内顺序靠前的优先），点火后整条会话冻结到抬手；
 * - 冷却按触发独立计；
 * - 长按类用 [GlobalGestureTrigger.fireOnHold] 区分「到时即点火」与「到时后拖动才点火」，
 *   后者在 [GlobalGestureTrigger.dragStartTimeoutMs] 内没拖动就作废；
 * - 方向类动作只认 [GlobalGestureTrigger.angle] 的扇区，未配置的扇区不响应（没有兜底）；
 * - 按住不动时没有新帧，所有时间判定都走定时器，不靠下一帧。
 */
class GlobalGestureRuntime(
    private val config: () -> GlobalGestureSettings,
    private val now: () -> Long = SystemClock::uptimeMillis,
    private val timer: LongPressTimer = MainThreadTimer(),
    private val density: () -> Float = { DensityProvider.density },
    private val screenWidth: () -> Float = { DensityProvider.screenWidthPx.toFloat() },
    private val screenHeight: () -> Float = { DensityProvider.screenHeightPx.toFloat() },
    /** 起点过滤（自家界面 / 自家窗口 / 面板在屏）：true = 整条会话不参与。 */
    private val shouldIgnoreStart: (x: Int, y: Int) -> Boolean = { _, _ -> false },
    /** 区可见性（场景显隐）：false = 该区不生效。 */
    private val zoneVisible: (zoneId: String) -> Boolean = { true },
    /** 这个区是否需要在按下时启动 a11y 预捕获（只有绑了识屏动作的区需要）。 */
    private val wantsPreCapture: (GlobalGestureZone) -> Boolean = { false },
    /** 点火。返回 true 表示需要继续转发拖动 / 抬手（识屏幽灵卡跟随手指）。 */
    private val onFire: (GlobalGestureHit) -> Boolean,
    private val onDrag: (x: Int, y: Int) -> Unit = { _, _ -> },
    private val onRelease: (x: Int, y: Int) -> Unit = { _, _ -> },
    private val onPreCapture: (x: Int, y: Int, downAtMs: Long) -> Unit = { _, _, _ -> },
    private val onPreCaptureCancel: () -> Unit = {},
    private val onVibrate: (GlobalGestureTrigger) -> Unit = {},
) {

    private class Candidate(
        val trigger: GlobalGestureTrigger,
        val recognizer: GestureRecognizer,
        val config: GestureConfig,
        var armed: Boolean = false,
        var armedAt: Long = 0L,
        var expired: Boolean = false,
        var fired: Boolean = false,
    )

    private companion object {
        /** 多指先后落下的聚合窗：窗口内点数增加可以升级为多指手势。 */
        const val FingerAggregationMs = 120L
    }

    /** 识别器按触发常驻：双击的「等第二击」状态必须跨会话保留。 */
    private val recognizers = mutableMapOf<String, GestureRecognizer>()

    /** 双击待定过期定时器按触发保留：会话结束不能把它们一起收掉。 */
    private val doubleTapTimers = mutableMapOf<String, Cancelable>()

    /** 每触发的上次点火时间：冷却独立计。 */
    private val lastFireAt = mutableMapOf<String, Long>()

    private var sessionSeq = 0L
    private var sessionId: String = ""

    private var active = false
    private var zone: GlobalGestureZone? = null
    private var candidates: MutableList<Candidate> = mutableListOf()
    private var anchor = Offset.Unspecified
    private var lastPoint = Offset.Unspecified
    private var downAt = 0L
    private var fingers = 1
    private var fingersLocked = false
    private var fired = false
    private var trackingDrag = false

    private var tick: Cancelable? = null
    private var fingerLockTimer: Cancelable? = null

    /** 本会话是否真的启动过预捕获：只有启动过才需要在取消时回收。 */
    private var preCaptureStarted = false

    /** 滑出后停住的最近到期时间：每次真实 Move 重算，tick 后清空（避免空转轮询）。 */
    private var holdDeadline = Long.MAX_VALUE

    /** 本次会话的按下点是否属于某条触发的锚点（会话是否在跑）。 */
    val isActive: Boolean get() = active

    fun onFrame(points: List<Offset>, action: TouchAction) {
        val primary = points.firstOrNull()
        when (action) {
            TouchAction.Down -> if (active) onExtraDown(points) else onDown(points)
            TouchAction.Move -> if (active) onMove(primary ?: lastPoint)
            TouchAction.Up -> onUp(primary ?: lastPoint)
        }
    }

    private fun onDown(points: List<Offset>) {
        val point = points.firstOrNull() ?: return
        val settings = config()
        if (shouldIgnoreStart(point.x.toInt(), point.y.toInt())) return

        val width = screenWidth()
        val height = screenHeight()
        val hit = settings.zones.firstOrNull { zone ->
            zone.enabled && zoneVisible(zone.id) && zone.bounds.contains(point.x, point.y, width, height)
        } ?: return

        // 不属于本次会话的识别器要清掉双击待定：否则 A 区点一下、B 区点一下会被配成双击。
        val candidateIds = hit.triggers.map { it.id }.toSet()
        recognizers.forEach { (id, recognizer) ->
            if (id !in candidateIds) recognizer.clearPendingDoubleTap()
        }

        anchor = point
        lastPoint = point
        downAt = now()
        sessionId = "global:${++sessionSeq}"
        fingers = points.size.coerceAtLeast(1)
        fingersLocked = false
        zone = hit
        fired = false
        trackingDrag = false
        holdDeadline = Long.MAX_VALUE
        preCaptureStarted = false

        val next = buildCandidates(hit, fingers)
        if (next.isEmpty()) {
            resetSessionState()
            return
        }

        candidates = next.toMutableList()
        active = true
        candidates.forEach { it.recognizer.onDown(downAt, anchor, it.config) }
        if (wantsPreCapture(hit)) {
            preCaptureStarted = true
            onPreCapture(anchor.x.toInt(), anchor.y.toInt(), downAt)
        }
        fingerLockTimer = timer.schedule(FingerAggregationMs) { fingersLocked = true }
        scheduleTick()
    }

    /** 聚合窗内又落下一根手指：整组候选升级成与最终手指数匹配的触发。 */
    private fun onExtraDown(points: List<Offset>) {
        if (fired) return
        val count = points.size
        if (count <= fingers) return
        val target = zone ?: return
        // 聚合窗结束后点数再变化：说明不是「双指先后落下」，整条会话作废。
        if (fingersLocked || now() - downAt > FingerAggregationMs) {
            abortSession()
            return
        }
        fingers = count
        // 单指候选不再参与：否则两指按下时一指长按仍可能先点火。
        candidates.forEach { it.recognizer.onCancel() }
        candidates = buildCandidates(target, count).toMutableList()
        if (candidates.isEmpty()) {
            abortSession()
            return
        }
        candidates.forEach { it.recognizer.onDown(downAt, anchor, it.config) }
        scheduleTick()
    }

    private fun onMove(point: Offset) {
        if (fired) {
            lastPoint = point
            if (trackingDrag) onDrag(point.x.toInt(), point.y.toInt())
            return
        }
        val delta = point - lastPoint
        lastPoint = point
        val time = now()
        holdDeadline = Long.MAX_VALUE

        for (candidate in candidates) {
            if (candidate.fired || candidate.expired) continue
            // 定时器可能晚于真实时间到达（主线程忙）：先补一次时间判定，
            // 再吃这一帧位移，避免「长按已就绪的第一次拖动」被吞掉。
            val timeDecision = candidate.recognizer.checkTime(time, candidate.config)
            handleDecision(candidate, timeDecision, point, time)
            if (fired) return
            val decision = candidate.recognizer.onMove(time, delta, candidate.config)
            handleDecision(candidate, decision, point, time)
            if (fired) return
            if (checkDragFire(candidate, point)) return
            updateHoldDeadline(candidate, time)
        }
        scheduleTick()
    }

    /** 长按后的拖动点火：方向形态按扇区取动作，未配置的扇区不响应。 */
    private fun checkDragFire(candidate: Candidate, point: Offset): Boolean {
        val trigger = candidate.trigger
        if (trigger.kind != GlobalGestureKind.LongPress) return false
        if (trigger.fireOnHold || !candidate.armed || candidate.expired) return false
        val offset = point - anchor
        if (hypot(offset.x.toDouble(), offset.y.toDouble()) <= trigger.slopDp * density()) return false
        val slot = trigger.angle.directionOf(offset)
        val action = trigger.directionActions.actionBy(slot) ?: return false
        fire(candidate, action, point)
        return true
    }

    private fun updateHoldDeadline(candidate: Candidate, time: Long) {
        val trigger = candidate.trigger
        val thresholdDp = when (trigger.kind) {
            GlobalGestureKind.SlideHold -> trigger.slideTriggerDistanceDp
            GlobalGestureKind.LongSlideHold -> trigger.effectiveLongSlideTriggerDistanceDp
            else -> return
        }
        val distance = hypot(
            (lastPoint.x - anchor.x).toDouble(),
            (lastPoint.y - anchor.y).toDouble(),
        ).toFloat()
        if (distance < thresholdDp * density()) return
        val deadline = time + trigger.holdTriggerDelayMs
        if (deadline < holdDeadline) holdDeadline = deadline
    }

    private fun handleDecision(
        candidate: Candidate,
        decision: GestureDecision,
        point: Offset,
        time: Long,
    ) {
        when (decision) {
            GestureDecision.Noop -> Unit
            GestureDecision.Cancel -> candidate.expired = true
            is GestureDecision.PendingDoubleTap -> {
                // 第一击待定：等第二击或超时清掉，避免下一次单击被当成双击。
                // 定时器按触发保留，不能被 endSession 收掉（会话已经结束）。
                val triggerId = candidate.trigger.id
                doubleTapTimers.remove(triggerId)?.cancel()
                doubleTapTimers[triggerId] = timer.schedule(candidate.trigger.doubleTapDelayMs.toLong()) {
                    candidate.recognizer.clearPendingDoubleTap()
                    doubleTapTimers.remove(triggerId)
                }
            }
            is GestureDecision.Trigger -> {
                val trigger = candidate.trigger
                if (trigger.kind == GlobalGestureKind.LongPress && !trigger.fireOnHold) {
                    // 到时只是「就绪」，真正点火要等拖动。
                    if (!candidate.armed) {
                        candidate.armed = true
                        candidate.armedAt = time
                    }
                } else {
                    val action = if (trigger.kind.isDirectional) {
                        trigger.directionActions.actionBy(decision.actionDirection)
                    } else {
                        // 即点火：单动作优先，旧配置的「单扇区任意方向」写法也兼容。
                        trigger.action ?: trigger.directionActions.actionBy(0)
                    } ?: return
                    fire(candidate, action, point)
                }
            }
        }
    }

    private fun fire(candidate: Candidate, action: StoredAction, position: Offset) {
        val currentZone = zone ?: return
        candidate.fired = true
        fired = true
        lastFireAt[candidate.trigger.id] = now()
        stopTimers()
        holdDeadline = Long.MAX_VALUE
        if (candidate.trigger.vibrate) onVibrate(candidate.trigger)
        trackingDrag = onFire(
            GlobalGestureHit(
                zone = currentZone,
                trigger = candidate.trigger,
                action = action,
                anchor = anchor,
                position = position,
                sessionId = sessionId,
            ),
        )
    }

    private fun onUp(point: Offset) {
        if (!active) return
        val time = now()
        if (!fired) {
            for (candidate in candidates) {
                if (candidate.fired || candidate.expired) continue
                val decision = candidate.recognizer.onUp(time, candidate.config)
                handleDecision(candidate, decision, point, time)
                if (fired) break
            }
        }
        val wasTrackingDrag = trackingDrag
        endSession(abort = false)
        if (wasTrackingDrag) onRelease(point.x.toInt(), point.y.toInt())
    }

    private fun onTick() {
        tick = null
        if (!active || fired) return
        val time = now()
        holdDeadline = Long.MAX_VALUE
        for (candidate in candidates) {
            if (candidate.fired || candidate.expired) continue
            // 按住不动时内核流没有新帧：补一帧零位移让「滑出后停住」也能结算。
            val moveDecision = candidate.recognizer.onMove(time, Offset.Zero, candidate.config)
            handleDecision(candidate, moveDecision, lastPoint, time)
            if (fired) return
            val tickDecision = candidate.recognizer.checkTime(time, candidate.config)
            handleDecision(candidate, tickDecision, lastPoint, time)
            if (fired) return
            val trigger = candidate.trigger
            if (candidate.armed && !trigger.fireOnHold && !candidate.expired) {
                val window = trigger.dragStartTimeoutMs
                if (window > 0 && time - candidate.armedAt >= window) {
                    candidate.expired = true
                }
            }
        }
        if (candidates.all { it.fired || it.expired }) {
            // 所有候选都作废（如长按后等拖动超时）：会话已经无意义，收掉预捕获。
            endSession(abort = false)
            return
        }
        scheduleTick()
    }

    private fun scheduleTick() {
        tick?.cancel()
        tick = null
        if (!active || fired) return
        val time = now()
        var next = holdDeadline
        for (candidate in candidates) {
            if (candidate.fired || candidate.expired) continue
            val deadline = deadlineOf(candidate, time) ?: continue
            if (deadline < next) next = deadline
        }
        if (next == Long.MAX_VALUE) return
        tick = timer.schedule((next - time).coerceAtLeast(1L)) { onTick() }
    }

    private fun deadlineOf(candidate: Candidate, time: Long): Long? {
        val trigger = candidate.trigger
        if (trigger.kind != GlobalGestureKind.LongPress) return null
        if (!candidate.armed) return downAt + trigger.longPressDelayMs
        if (trigger.fireOnHold) return null
        return if (trigger.dragStartTimeoutMs > 0) candidate.armedAt + trigger.dragStartTimeoutMs else null
    }

    private fun buildCandidates(zone: GlobalGestureZone, fingers: Int): List<Candidate> {
        val time = now()
        return zone.triggers
            .filter { it.enabled && it.hasAnyAction && it.kind.allowsFingers(fingers) && it.fingers == fingers }
            .filter { trigger ->
                val last = lastFireAt[trigger.id] ?: Long.MIN_VALUE / 2
                time - last >= trigger.triggerCooldownMs.coerceAtLeast(0)
            }
            .map { trigger ->
                Candidate(
                    trigger = trigger,
                    recognizer = recognizers.getOrPut(trigger.id) { GestureRecognizer() },
                    config = buildConfig(trigger),
                )
            }
    }

    private fun buildConfig(trigger: GlobalGestureTrigger): GestureConfig {
        val density = density()
        val kindType = trigger.kind.triggerType()
        return GestureConfig(
            buttonId = trigger.id,
            angle = trigger.angle,
            thresholds = GestureThresholds(
                touchSlop = trigger.slopDp * density,
                longPressDelayMs = trigger.longPressDelayMs.toLong(),
                doubleTapDelayMs = trigger.doubleTapDelayMs.toLong(),
                slideHoldDelayMs = trigger.holdTriggerDelayMs.toLong(),
                longSlideHoldDelayMs = trigger.holdTriggerDelayMs.toLong(),
                slideTriggerDistance = trigger.slideTriggerDistanceDp * density,
                longSlideTriggerDistance = trigger.effectiveLongSlideTriggerDistanceDp * density,
            ),
            // 识别区不按左右半屏镜像：需要左右差异时用更多扇区自己表达。
            isMirrorHorizontal = false,
            hasTapActions = trigger.kind == GlobalGestureKind.Tap && trigger.action != null,
            hasDoubleTapActions = trigger.kind == GlobalGestureKind.DoubleTap && trigger.action != null,
            hasLongPressActions = trigger.kind == GlobalGestureKind.LongPress,
            hasActionInDirection = { type, slot ->
                type == kindType && trigger.directionActions.actionBy(slot) != null
            },
        )
    }

    private fun abortSession() {
        endSession(abort = true)
    }

    private fun endSession(abort: Boolean) {
        stopTimers()
        if (abort) candidates.forEach { it.recognizer.onCancel() }
        if (preCaptureStarted) onPreCaptureCancel()
        resetSessionState()
    }

    private fun resetSessionState() {
        active = false
        zone = null
        candidates = mutableListOf()
        anchor = Offset.Unspecified
        lastPoint = Offset.Unspecified
        downAt = 0L
        fingersLocked = false
        fired = false
        trackingDrag = false
        preCaptureStarted = false
        holdDeadline = Long.MAX_VALUE
    }

    private fun stopTimers() {
        tick?.cancel()
        tick = null
        fingerLockTimer?.cancel()
        fingerLockTimer = null
    }
}

/** 全屏手势的词表与触钮的 [GestureTriggerType] 一一对应。 */
internal fun GlobalGestureKind.triggerType(): GestureTriggerType = when (this) {
    GlobalGestureKind.Tap -> GestureTriggerType.Tap
    GlobalGestureKind.DoubleTap -> GestureTriggerType.DoubleTap
    GlobalGestureKind.LongPress -> GestureTriggerType.LongPress
    GlobalGestureKind.Slide -> GestureTriggerType.Slide
    GlobalGestureKind.LongSlide -> GestureTriggerType.LongSlide
    GlobalGestureKind.SlideHold -> GestureTriggerType.SlideHold
    GlobalGestureKind.LongSlideHold -> GestureTriggerType.LongSlideHold
}
