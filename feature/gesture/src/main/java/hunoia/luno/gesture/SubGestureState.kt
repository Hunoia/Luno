package hunoia.luno.gesture

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hunoia.luno.action.model.Action

private const val MAX_SUB_GESTURE_DEPTH = 3

data class SubGestureResolvedActions(
    val subGesture: SubGesture,
    val direction: Int,
    val action: StoredAction?,
    val triggerType: GestureTriggerType,
    val touchPosition: Offset,
    /** 合成的识别结果（子手势无独立识别机，方向即动作方向）。 */
    val decision: GestureDecision.Trigger,
)

/**
 * 进入子手势会话的结果。
 *
 * `Disabled` / `Missing` 不能静默：两者在配置里都「看得见」——禁用是用户自己关的开关，
 * 悬空引用是目标被删——送到执行层只会被当成 `Action.SubGesture` 空操作，表现为点了没反应。
 */
enum class SubGestureEntry {
    /** 已进入会话。 */
    Entered,
    /** 目标子手势被禁用（开关关掉，但仍可选、可被引用）。 */
    Disabled,
    /** 引用的子手势已不存在。 */
    Missing,
    /** 不是子手势动作，或超过嵌套深度：静默忽略。 */
    Ignored,
}

class SubGestureState(
    private val scope: CoroutineScope,
    private val subGestureSettings: SubGestureSettings,
    /**
     * 子手势模式切换通知：`(进入, 初始点, 子手势)`。
     * [subGesture] 为 null = 退出模式；进入时给整个子手势，overlay 据此以初始点为
     * 圆心画扇区边界线 + 短/长滑范围圆（[SubGesture.showAngleGuide] 关闭时不画）。
     */
    private val onModeChanged: (Boolean, Offset, SubGesture?) -> Unit,
    /** 单调时间源；默认 `SystemClock.uptimeMillis`，单测注入假时钟以验证按住类触发。 */
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
) {
    var activeSubGesture by mutableStateOf<SubGesture?>(null)
        private set
    var subGestureAccum by mutableStateOf(Offset.Zero)
        private set
    var origin by mutableStateOf(Offset.Unspecified)
        private set
    var finger by mutableStateOf(Offset.Unspecified)
        private set
    var subGestureDepth by mutableIntStateOf(0)
        private set
    var lastResolvedActionSubGesture by mutableStateOf<SubGesture?>(null)
        private set
    private var timeoutJob by mutableStateOf<Job?>(null)
    /** null = 未开始计时（不用 0 作哨兵：注入时钟可能从 0 开始）。 */
    private var slideHoldFirstTriggerMs: Long? = null
    private var longSlideHoldFirstTriggerMs: Long? = null
    private var directTriggered = false

    val isActive: Boolean get() = activeSubGesture != null

    /**
     * 进入子手势会话。
     * @param holdFire 进入时**手指是否还按着**：true = 同一只手指继续拖（触钮停留/滑动进入、
     *   面板项长按点火），触摸窗只做视觉；false = 手指已抬/无手指（面板项点选、悬浮球点按），
     *   触摸窗才是触摸源。两种入口的振动各自可关（[SubGesture.holdFireVibrate] / [SubGesture.overlayVibrate]）。
     */
    fun tryEnterSubGesture(
        action: StoredAction,
        touchPosition: Offset,
        holdFire: Boolean = false,
    ): SubGestureEntry {
        if (action.typeId != "internal.subGesture") return SubGestureEntry.Ignored
        val id = (action.params["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?: return SubGestureEntry.Ignored
        val target = subGestureSettings.subGestures.firstOrNull { it.id == id }
            ?: return SubGestureEntry.Missing
        // 禁用 = 仍可选、可被引用，只在实际触发时说清楚（开关语义取「禁用」而不是「不存在」）。
        if (!target.enabled) return SubGestureEntry.Disabled
        if (subGestureDepth >= MAX_SUB_GESTURE_DEPTH) return SubGestureEntry.Ignored
        activeSubGesture = target
        origin = validOrUnspecified(touchPosition)
        finger = origin
        subGestureAccum = Offset.Zero
        slideHoldFirstTriggerMs = null
        longSlideHoldFirstTriggerMs = null
        directTriggered = false
        subGestureDepth += 1
        scheduleTimeout()
        if (holdFire) target.tryVibrateHoldFire() else target.tryVibrateOverlay()
        onModeChanged(true, origin, target)
        return SubGestureEntry.Entered
    }

    fun onDragStart(offset: Offset): Boolean {
        if (!isActive) return false
        // 触摸窗里再按下：会话级反馈（与方向动作的四个振动开关分开）。
        activeSubGesture?.tryVibratePress()
        origin = validOrUnspecified(offset)
        finger = origin
        subGestureAccum = Offset.Zero
        slideHoldFirstTriggerMs = null
        longSlideHoldFirstTriggerMs = null
        directTriggered = false
        restartTimeout()
        return true
    }

    fun onDrag(dragAmount: Offset): SubGestureResolvedActions? {
        if (!isActive) return null
        finger = if (finger.hasFiniteCoordinates()) finger + dragAmount else dragAmount
        subGestureAccum = if (origin.hasFiniteCoordinates() && finger.hasFiniteCoordinates()) finger - origin else subGestureAccum + dragAmount
        val sg = activeSubGesture ?: return null
        val distance = kotlin.math.hypot(subGestureAccum.x, subGestureAccum.y)
        val canReachLongSlide = distance >= sg.effectiveLongSlideTriggerDistance
        val canReachSlide = distance >= sg.triggerDistance
        if (canReachLongSlide) {
            if (longSlideHoldFirstTriggerMs == null) {
                longSlideHoldFirstTriggerMs = nowMs()
            } else if (!directTriggered && nowMs() - longSlideHoldFirstTriggerMs!! >= sg.longSlideHoldTriggerDelayMs) {
                val direction = sg.angle.directionOf(subGestureAccum)
                val (action, actualType) = actionsFor(
                    sg,
                    GestureTriggerType.LongSlideHold,
                    direction,
                )
                if (action.hasMeaningfulAction()) {
                    directTriggered = true
                    return resolve(sg, direction, actualType)
                }
            }
        } else {
            longSlideHoldFirstTriggerMs = null
        }
        if (canReachSlide && !canReachLongSlide) {
            if (slideHoldFirstTriggerMs == null) {
                slideHoldFirstTriggerMs = nowMs()
            } else if (!directTriggered && nowMs() - slideHoldFirstTriggerMs!! >= sg.slideHoldTriggerDelayMs) {
                val direction = sg.angle.directionOf(subGestureAccum)
                val (action, actualType) = actionsFor(
                    sg,
                    GestureTriggerType.SlideHold,
                    direction,
                )
                if (action.hasMeaningfulAction()) {
                    directTriggered = true
                    return resolve(sg, direction, actualType)
                }
            }
        } else {
            slideHoldFirstTriggerMs = null
        }
        return null
    }

    fun onDragEnd(): SubGestureResolvedActions? {
        if (!isActive) return null
        if (directTriggered) {
            clear()
            return null
        }
        val sg = activeSubGesture ?: return null
        val distance = kotlin.math.hypot(subGestureAccum.x, subGestureAccum.y)
        val direction = sg.angle.directionOf(subGestureAccum)
        val nominalType = when {
            distance >= sg.effectiveLongSlideTriggerDistance -> GestureTriggerType.LongSlide
            distance >= sg.triggerDistance -> GestureTriggerType.Slide
            else -> null
        }
        if (nominalType != null) {
            val (action, actualType) = actionsFor(sg, nominalType, direction)
            if (action.hasMeaningfulAction()) {
                return resolve(sg, direction, actualType)
            }
        }
        subGestureAccum = Offset.Zero
        return null
    }

    fun onDragCancel() {
        if (!isActive) return
        clear()
    }

    fun clear(notifyService: Boolean = true) {
        activeSubGesture = null
        lastResolvedActionSubGesture = null
        origin = Offset.Unspecified
        subGestureAccum = Offset.Zero
        finger = Offset.Unspecified
        slideHoldFirstTriggerMs = null
        longSlideHoldFirstTriggerMs = null
        directTriggered = false
        subGestureDepth = 0
        timeoutJob?.cancel()
        timeoutJob = null
        if (notifyService) onModeChanged(false, Offset.Unspecified, null)
    }

    private fun scheduleTimeout() {
        timeoutJob?.cancel()
        val ms = activeSubGesture?.timeoutMs ?: return
        timeoutJob = scope.launch {
            delay(ms)
            // 超时自动收会话：先把子手势留下（clear 会清掉 activeSubGesture），再清。
            val expired = activeSubGesture
            clear()
            expired?.tryVibrateTimeout()
        }
    }

    private fun restartTimeout() {
        scheduleTimeout()
    }

    private fun resolve(
        subGesture: SubGesture,
        direction: Int,
        triggerType: GestureTriggerType,
    ): SubGestureResolvedActions {
        val action = actionByType(subGesture, triggerType, direction)
        lastResolvedActionSubGesture = subGesture
        activeSubGesture = null
        timeoutJob?.cancel()
        timeoutJob = null
        val touchPosition = when {
            finger.hasFiniteCoordinates() -> finger
            origin.hasFiniteCoordinates() -> origin
            else -> Offset.Unspecified
        }
        origin = Offset.Unspecified
        subGestureAccum = Offset.Zero
        finger = Offset.Unspecified
        slideHoldFirstTriggerMs = null
        longSlideHoldFirstTriggerMs = null
        subGesture.tryVibrateFor(triggerType)
        directTriggered = false
        onModeChanged(false, Offset.Unspecified, null)
        return SubGestureResolvedActions(
        subGesture,
        direction,
        action,
        triggerType,
        touchPosition,
        GestureDecision.Trigger(triggerType, direction, direction),
    )
    }

    /**
     * 同方向回退与主手势一致：返回（动作，实际类型）。
     * 震动与解析结果按实际类型走，不按名义类型。
     */
    private fun actionsFor(
        subGesture: SubGesture,
        primary: GestureTriggerType,
        direction: Int,
    ): Pair<StoredAction?, GestureTriggerType> {
        for (type in primary.sameDirectionFallbackOrder()) {
            val action = actionByType(subGesture, type, direction)
            if (action.hasMeaningfulAction()) return action to type
        }
        return null to primary
    }

    private fun actionByType(
        subGesture: SubGesture,
        type: GestureTriggerType,
        direction: Int,
    ): StoredAction? = when (type) {
        GestureTriggerType.Slide -> subGesture.slideActionFor(direction)
        GestureTriggerType.SlideHold -> subGesture.slideHoldActionFor(direction)
        GestureTriggerType.LongSlide -> subGesture.longSlideActionFor(direction)
        GestureTriggerType.LongSlideHold -> subGesture.longSlideHoldActionFor(direction)
        else -> null
    }

    private fun Offset.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite()

    private fun validOrUnspecified(offset: Offset): Offset {
        return if (offset.hasFiniteCoordinates()) offset else Offset.Unspecified
    }

    private fun StoredAction?.hasMeaningfulAction(): Boolean =
        this != null && typeId != Action.NONE_TYPE_ID
}
