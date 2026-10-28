package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import kotlin.math.hypot

/** 一次手势判定的输入配置。 */
data class GestureConfig(
    val buttonId: String,
    val angle: GestureButtonAngle,
    val thresholds: GestureThresholds,
    val isMirrorHorizontal: Boolean,
    val hasTapActions: Boolean,
    val hasDoubleTapActions: Boolean,
    val hasLongPressActions: Boolean,
    val hasActionInDirection: (triggerType: GestureTriggerType, direction: GestureDirection) -> Boolean,
)

/** 一次手势会话的共享状态；策略只读写这里，不持有跨会话状态。 */
class GestureSession {
    var origin: Offset = Offset.Unspecified
    var finger: Offset = Offset.Unspecified
    var triggerDirection: GestureDirection = GestureDirection.Right
    var actionDirection: GestureDirection = GestureDirection.Right
    var downTimeMs = 0L
    var directTriggered = false
    var isLongPressCanceled = false
    var slideHoldFirstReachedMs = 0L
    var longSlideHoldFirstReachedMs = 0L
    var pendingButtonId: String? = null

    fun distance(): Float = hypot(
        (finger.x - origin.x).toDouble(),
        (finger.y - origin.y).toDouble(),
    ).toFloat()

    fun rawOffset(): Offset = finger - origin

    fun updateDirections(config: GestureConfig) {
        val rawOffset = rawOffset()
        if (rawOffset.x == 0f && rawOffset.y == 0f) return
        triggerDirection = config.angle.directionOf(rawOffset)
        val actionOffset = if (config.isMirrorHorizontal) Offset(-rawOffset.x, rawOffset.y) else rawOffset
        actionDirection = config.angle.directionOf(actionOffset)
    }

    fun reset() {
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        triggerDirection = GestureDirection.Right
        actionDirection = GestureDirection.Right
        downTimeMs = 0L
        directTriggered = false
        isLongPressCanceled = false
        slideHoldFirstReachedMs = 0L
        longSlideHoldFirstReachedMs = 0L
    }
}

/**
 * 手势判定策略：一个 triggerType 的识别规则。
 *
 * 识别器只负责会话状态推进与策略遍历；新增手势类型只需注册一条策略。
 */
interface GestureStrategy {
    fun onDown(timeMs: Long, position: Offset, config: GestureConfig, session: GestureSession) = Unit

    fun onMove(timeMs: Long, delta: Offset, config: GestureConfig, session: GestureSession): GestureDecision =
        GestureDecision.Noop

    fun onUp(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision =
        GestureDecision.Noop

    fun onTick(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision =
        GestureDecision.Noop
}

/** 手势策略注册表：id 唯一，遍历顺序即判定优先级。 */
object GestureStrategyRegistry {

    private val strategies = LinkedHashMap<String, GestureStrategy>()

    init {
        register("longSlideHold", LongSlideHoldStrategy)
        register("slideHold", SlideHoldStrategy)
        register("longSlide", LongSlideStrategy)
        register("slide", SlideStrategy)
        register("tapDoubleTap", TapDoubleTapStrategy)
        register("longPress", LongPressStrategy)
    }

    fun register(id: String, strategy: GestureStrategy) {
        require(id.isNotBlank()) { "gesture strategy id must not be blank" }
        require(!strategies.containsKey(id)) { "duplicate gesture strategy: $id" }
        strategies[id] = strategy
    }

    fun onDown(timeMs: Long, position: Offset, config: GestureConfig, session: GestureSession) {
        strategies.values.forEach { it.onDown(timeMs, position, config, session) }
    }

    fun onMove(
        timeMs: Long,
        delta: Offset,
        config: GestureConfig,
        session: GestureSession,
    ): GestureDecision {
        for (strategy in strategies.values) {
            val decision = strategy.onMove(timeMs, delta, config, session)
            if (decision !is GestureDecision.Noop) return decision
        }
        return GestureDecision.Noop
    }

    fun onUp(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision {
        for (strategy in strategies.values) {
            val decision = strategy.onUp(timeMs, config, session)
            if (decision !is GestureDecision.Noop) return decision
        }
        return GestureDecision.Noop
    }

    fun onTick(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision {
        for (strategy in strategies.values) {
            val decision = strategy.onTick(timeMs, config, session)
            if (decision !is GestureDecision.Noop) return decision
        }
        return GestureDecision.Noop
    }
}

private object LongSlideHoldStrategy : GestureStrategy {
    override fun onMove(
        timeMs: Long,
        delta: Offset,
        config: GestureConfig,
        session: GestureSession,
    ): GestureDecision {
        if (session.distance() < config.thresholds.longSlideTriggerDistance) {
            session.longSlideHoldFirstReachedMs = 0L
            return GestureDecision.Noop
        }
        if (session.longSlideHoldFirstReachedMs == 0L) {
            session.longSlideHoldFirstReachedMs = timeMs
            return GestureDecision.Noop
        }
        if (session.directTriggered) return GestureDecision.Noop
        if (timeMs - session.longSlideHoldFirstReachedMs < config.thresholds.longSlideHoldDelayMs) {
            return GestureDecision.Noop
        }
        if (!config.hasActionInDirection(GestureTriggerType.LongSlideHold, session.actionDirection)) {
            return GestureDecision.Noop
        }
        session.directTriggered = true
        return GestureDecision.Trigger(
            triggerType = GestureTriggerType.LongSlideHold,
            direction = session.triggerDirection,
            actionDirection = session.actionDirection,
        )
    }
}

private object SlideHoldStrategy : GestureStrategy {
    override fun onMove(
        timeMs: Long,
        delta: Offset,
        config: GestureConfig,
        session: GestureSession,
    ): GestureDecision {
        val distance = session.distance()
        val canTriggerSlide = distance >= config.thresholds.slideTriggerDistance
        val canTriggerLong = distance >= config.thresholds.longSlideTriggerDistance
        if (!canTriggerSlide || canTriggerLong) {
            session.slideHoldFirstReachedMs = 0L
            return GestureDecision.Noop
        }
        if (session.slideHoldFirstReachedMs == 0L) {
            session.slideHoldFirstReachedMs = timeMs
            return GestureDecision.Noop
        }
        if (session.directTriggered) return GestureDecision.Noop
        if (timeMs - session.slideHoldFirstReachedMs < config.thresholds.slideHoldDelayMs) {
            return GestureDecision.Noop
        }
        if (!config.hasActionInDirection(GestureTriggerType.SlideHold, session.actionDirection)) {
            return GestureDecision.Noop
        }
        session.directTriggered = true
        return GestureDecision.Trigger(
            triggerType = GestureTriggerType.SlideHold,
            direction = session.triggerDirection,
            actionDirection = session.actionDirection,
        )
    }
}

private object LongSlideStrategy : GestureStrategy {
    override fun onUp(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision {
        if (session.directTriggered) return GestureDecision.Noop
        if (session.distance() < config.thresholds.longSlideTriggerDistance) return GestureDecision.Noop
        if (!config.hasActionInDirection(GestureTriggerType.LongSlide, session.actionDirection)) {
            return GestureDecision.Noop
        }
        return GestureDecision.Trigger(
            triggerType = GestureTriggerType.LongSlide,
            direction = session.triggerDirection,
            actionDirection = session.actionDirection,
        )
    }
}

private object SlideStrategy : GestureStrategy {
    override fun onUp(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision {
        if (session.directTriggered) return GestureDecision.Noop
        val distance = session.distance()
        if (distance < config.thresholds.slideTriggerDistance) return GestureDecision.Noop
        if (distance >= config.thresholds.longSlideTriggerDistance) return GestureDecision.Noop
        if (!config.hasActionInDirection(GestureTriggerType.Slide, session.actionDirection)) {
            return GestureDecision.Noop
        }
        return GestureDecision.Trigger(
            triggerType = GestureTriggerType.Slide,
            direction = session.triggerDirection,
            actionDirection = session.actionDirection,
        )
    }
}

private object TapDoubleTapStrategy : GestureStrategy {

    override fun onDown(
        timeMs: Long,
        position: Offset,
        config: GestureConfig,
        session: GestureSession,
    ) {
        if (!config.hasDoubleTapActions || session.pendingButtonId != config.buttonId) {
            session.pendingButtonId = null
        }
    }

    override fun onUp(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision {
        if (session.directTriggered) return GestureDecision.Noop
        if (session.distance() > config.thresholds.touchSlop) return GestureDecision.Noop
        if (session.pendingButtonId == config.buttonId && config.hasDoubleTapActions) {
            session.pendingButtonId = null
            return GestureDecision.Trigger(
                triggerType = GestureTriggerType.DoubleTap,
                direction = GestureDirection.Right,
                actionDirection = GestureDirection.Right,
            )
        }
        if (!config.hasTapActions && !config.hasDoubleTapActions) return GestureDecision.Noop
        val tapDecision = GestureDecision.Trigger(
            triggerType = GestureTriggerType.Tap,
            direction = GestureDirection.Right,
            actionDirection = GestureDirection.Right,
        )
        if (config.hasDoubleTapActions) {
            session.pendingButtonId = config.buttonId
            return GestureDecision.PendingDoubleTap(tapDecision)
        }
        return tapDecision
    }
}

private object LongPressStrategy : GestureStrategy {
    override fun onTick(timeMs: Long, config: GestureConfig, session: GestureSession): GestureDecision {
        if (session.directTriggered) return GestureDecision.Noop
        if (session.isLongPressCanceled) return GestureDecision.Noop
        if (!config.hasLongPressActions) return GestureDecision.Noop
        if (timeMs - session.downTimeMs < config.thresholds.longPressDelayMs) return GestureDecision.Noop
        session.directTriggered = true
        return GestureDecision.Trigger(
            triggerType = GestureTriggerType.LongPress,
            direction = GestureDirection.Right,
            actionDirection = GestureDirection.Right,
        )
    }
}
