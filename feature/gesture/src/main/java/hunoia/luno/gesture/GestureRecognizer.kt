package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureDirection

/**
 * 手势识别器：只推进会话状态并遍历 [GestureStrategyRegistry]。
 *
 * 具体 triggerType 的识别规则在策略里，新增手势类型不再修改本类。
 */
class GestureRecognizer {

    private val session = GestureSession()

    val origin: Offset get() = session.origin
    val finger: Offset get() = session.finger
    val triggerDirection: GestureDirection get() = session.triggerDirection
    val actionDirection: GestureDirection get() = session.actionDirection

    fun onDown(timeMs: Long, position: Offset, config: GestureConfig): GestureDecision {
        session.origin = position
        session.finger = position
        session.downTimeMs = timeMs
        session.directTriggered = false
        session.isLongPressCanceled = false
        session.slideHoldFirstReachedMs = 0L
        session.longSlideHoldFirstReachedMs = 0L
        GestureStrategyRegistry.onDown(timeMs, position, config, session)
        return GestureDecision.Noop
    }

    fun onMove(timeMs: Long, delta: Offset, config: GestureConfig): GestureDecision {
        session.finger = Offset(session.finger.x + delta.x, session.finger.y + delta.y)
        if (session.distance() > config.thresholds.touchSlop) {
            session.isLongPressCanceled = true
            session.pendingButtonId = null
        }
        session.updateDirections(config)
        return GestureStrategyRegistry.onMove(timeMs, delta, config, session)
    }

    fun onUp(timeMs: Long, config: GestureConfig): GestureDecision {
        if (session.directTriggered) {
            session.reset()
            return GestureDecision.Noop
        }
        val result = GestureStrategyRegistry.onUp(timeMs, config, session)
        session.reset()
        return result
    }

    fun checkTime(timeMs: Long, config: GestureConfig): GestureDecision =
        GestureStrategyRegistry.onTick(timeMs, config, session)

    fun onCancel(): GestureDecision {
        session.reset()
        return GestureDecision.Cancel
    }

    /** 双击等待过期：清掉待定按钮，下一次点按按单击处理。 */
    fun clearPendingDoubleTap() {
        session.pendingButtonId = null
    }
}
