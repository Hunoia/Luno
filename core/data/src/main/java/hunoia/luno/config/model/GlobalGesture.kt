package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 识别区：全屏手势的生效范围，顺序即重叠时的优先级（第一条命中的区生效）。
 *
 * 一个识别区像一枚触钮：有自己的边界、自己的触发列表、自己的可见性条目
 * （`VisibilityProfile.targetIds` 里直接存 [id]）。
 */
@Serializable
@Keep
data class GlobalGestureZone(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val bounds: ZoneBounds = ZoneBounds.Full,
    /** 触发条目：顺序即区内优先级。 */
    val triggers: List<GlobalGestureTrigger> = emptyList(),
) {
    companion object {
        fun create(name: String = ""): GlobalGestureZone = GlobalGestureZone(
            // 与 GestureButton 同口径：时间戳前缀保序，UUID 后缀防同毫秒撞 id。
            id = "${System.currentTimeMillis()}-${UUID.randomUUID()}",
            name = name,
        )
    }
}

/** 识别区边界：屏幕比例（0..1），横竖屏共用一套（横屏一般不用全屏手势）。 */
@Serializable
@Keep
data class ZoneBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
) {
    fun contains(px: Float, py: Float, screenWidth: Float, screenHeight: Float): Boolean {
        if (screenWidth <= 0f || screenHeight <= 0f) return false
        val left = x * screenWidth
        val top = y * screenHeight
        val right = left + width * screenWidth
        val bottom = top + height * screenHeight
        return px >= left && px < right && py >= top && py < bottom
    }

    companion object {
        val Full = ZoneBounds()
    }
}

/** 全屏手势形态。词表与触钮一致（[GestureTriggerType]），Tap 只对多指开放。 */
@Serializable
@Keep
enum class GlobalGestureKind {
    Tap,
    DoubleTap,
    LongPress,
    Slide,
    LongSlide,
    SlideHold,
    LongSlideHold,
}

/** 该形态是否按方向扇区取动作。 */
val GlobalGestureKind.isDirectional: Boolean
    get() = this == GlobalGestureKind.Slide ||
        this == GlobalGestureKind.LongSlide ||
        this == GlobalGestureKind.SlideHold ||
        this == GlobalGestureKind.LongSlideHold

/**
 * 该形态是否允许这么多手指。
 *
 * 单指只允许按住类：长按/长按后拖/双击。单指点按与单指滑动会与底层应用、系统手势
 * 直接抢同一段触摸（内核流只读、不能消费），配置出来只会互相打架，UI 与运行时都挡掉。
 */
fun GlobalGestureKind.allowsFingers(fingers: Int): Boolean = when (this) {
    GlobalGestureKind.Tap,
    GlobalGestureKind.Slide,
    GlobalGestureKind.LongSlide,
    GlobalGestureKind.SlideHold,
    GlobalGestureKind.LongSlideHold,
    -> fingers >= 2
    GlobalGestureKind.DoubleTap,
    GlobalGestureKind.LongPress,
    -> fingers >= 1
}

/**
 * 识别区里的一条触发。
 *
 * - [fingers]：1..3；单指允许范围见 [allowsFingers]。
 * - [fireOnHold]：仅长按类使用。`true` = 按住到阈值即点火；`false` = 到阈值后进入
 *   “等待拖动”窗口，拖动超过 [slopDp] 才点火（默认数据用这条，保持原长按识屏的手感）。
 * - [action]：无方向形态的动作（Tap / DoubleTap / LongPress 即点火）。
 * - [directionActions]：方向形态的动作；[angle] 的扇区数 = 1 时表示“任意方向”。
 *   未配置的扇区不响应（没有兜底动作）。
 * - 阈值都按触发条目独立：两个触发可以各有各的长按延迟与冷却。
 */
@Serializable
@Keep
data class GlobalGestureTrigger(
    val id: String,
    val enabled: Boolean = true,
    val kind: GlobalGestureKind = GlobalGestureKind.LongPress,
    val fingers: Int = 1,
    val fireOnHold: Boolean = false,
    val action: StoredAction? = null,
    val directionActions: DirectionActions = DirectionActions(),
    val angle: GestureButtonAngle = GestureButtonAngle(),
    val longPressDelayMs: Int = 400,
    /** 长按就绪后的“取消窗口”：窗口内没拖动就作废；0 = 不启用。 */
    val dragStartTimeoutMs: Int = 3_000,
    val slopDp: Float = 10f,
    val triggerCooldownMs: Int = 800,
    /** 近档滑动距离：Slide / SlideHold 的门槛。 */
    val slideTriggerDistanceDp: Float = 30f,
    /** 远档滑动距离：LongSlide / LongSlideHold 的门槛。 */
    val longSlideTriggerDistanceDp: Float = 100f,
    /** 滑出后停住多久算 hold（SlideHold / LongSlideHold）。 */
    val holdTriggerDelayMs: Int = 120,
    /** 双击间隔上限。 */
    val doubleTapDelayMs: Int = 300,
    val vibrate: Boolean = false,
) {
    init {
        require(fingers in 1..3) { "fingers must be 1..3" }
        require(doubleTapDelayMs >= 0) { "doubleTapDelayMs must not be negative" }
    }

    val effectiveLongSlideTriggerDistanceDp: Float
        get() = longSlideTriggerDistanceDp.coerceAtLeast(slideTriggerDistanceDp)

    /** 实际生效的动作：方向形态与「长按后拖」按扇区取，按住即触发取单动作。 */
    fun actionFor(slot: Int): StoredAction? = when {
        kind.isDirectional -> directionActions.actionBy(slot)
        kind == GlobalGestureKind.LongPress ->
            if (fireOnHold) action ?: directionActions.actionBy(0)
            else directionActions.actionBy(slot)
        else -> action
    }

    /** 是否配了任何动作（决定该触发是否值得参与识别）。 */
    val hasAnyAction: Boolean
        get() = when {
            kind.isDirectional -> directionActions.actions.isNotEmpty()
            kind == GlobalGestureKind.LongPress ->
                if (fireOnHold) {
                    action != null || directionActions.actions.isNotEmpty()
                } else {
                    directionActions.actions.isNotEmpty()
                }
            else -> action != null
        }

    companion object {
        fun create(kind: GlobalGestureKind = GlobalGestureKind.LongPress): GlobalGestureTrigger =
            GlobalGestureTrigger(
                id = "${System.currentTimeMillis()}-${UUID.randomUUID()}",
                kind = kind,
                fingers = if (kind == GlobalGestureKind.LongPress ||
                    kind == GlobalGestureKind.DoubleTap
                ) {
                    1
                } else {
                    2
                },
                // 长按后拖默认「任意方向」；方向类默认四向，让用户自己绑各自动作。
                angle = if (kind == GlobalGestureKind.LongPress) {
                    GestureButtonAngle(listOf(0.25f))
                } else {
                    GestureButtonAngle()
                },
            )
    }
}

/** 全屏手势配置：识别区列表；每区自带启用开关，不再设全局总开关。 */
@Serializable
@Keep
data class GlobalGestureSettings(
    val zones: List<GlobalGestureZone> = GlobalGestureDefaults.Zones,
)

object GlobalGestureDefaults {
    /**
     * 默认一个全屏识别区 + 一条「长按后拖 → 识别屏幕内容」。
     *
     * 扇区数 = 1 表示任意方向，等价于旧实现「长按后往哪儿拖都弹识屏卡」；
     * 阈值也沿用旧默认值，升级后手感不变。
     */
    val Zones: List<GlobalGestureZone> = listOf(
        GlobalGestureZone(
            id = "1",
            name = "",
            enabled = false,
            bounds = ZoneBounds.Full,
            triggers = listOf(
                GlobalGestureTrigger(
                    id = "1-1",
                    kind = GlobalGestureKind.LongPress,
                    fingers = 1,
                    fireOnHold = false,
                    directionActions = DirectionActions(
                        mapOf(0 to StoredAction.of("accessibility.screenPick")),
                    ),
                    angle = GestureButtonAngle(listOf(0.25f)),
                    longPressDelayMs = 400,
                    dragStartTimeoutMs = 3_000,
                    slopDp = 10f,
                    triggerCooldownMs = 800,
                ),
            ),
        ),
    )
}
