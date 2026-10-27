package hunoia.luno.gesture

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.model.GestureDirection
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
    val direction: GestureDirection,
    val action: StoredAction?,
    val triggerType: GestureTriggerType,
    val touchPosition: Offset,
    /** 合成的识别结果（子手势无独立识别机，方向即动作方向）。 */
    val decision: GestureDecision.Trigger,
)

class SubGestureState(
    private val scope: CoroutineScope,
    private val subGestureSettings: SubGestureSettings,
    private val onModeChanged: (Boolean, Offset, Int) -> Unit,
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
    private var slideHoldFirstTriggerMs = 0L
    private var longSlideHoldFirstTriggerMs = 0L
    private var directTriggered = false

    val isActive: Boolean get() = activeSubGesture != null

    fun tryEnterSubGesture(action: StoredAction, touchPosition: Offset): Boolean {
        if (action.typeId != "internal.subGesture") return false
        val id = (action.params["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?: return false
        val target = subGestureSettings.subGestures.firstOrNull { it.id == id && it.enabled }
            ?: return false
        if (subGestureDepth >= MAX_SUB_GESTURE_DEPTH) return false
        activeSubGesture = target
        origin = validOrUnspecified(touchPosition)
        finger = origin
        subGestureAccum = Offset.Zero
        slideHoldFirstTriggerMs = 0L
        longSlideHoldFirstTriggerMs = 0L
        directTriggered = false
        subGestureDepth += 1
        scheduleTimeout()
        onModeChanged(true, origin, target.captureRadius)
        return true
    }

    fun onDragStart(offset: Offset): Boolean {
        if (!isActive) return false
        origin = validOrUnspecified(offset)
        finger = origin
        subGestureAccum = Offset.Zero
        slideHoldFirstTriggerMs = 0L
        longSlideHoldFirstTriggerMs = 0L
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
            if (longSlideHoldFirstTriggerMs == 0L) {
                longSlideHoldFirstTriggerMs = SystemClock.uptimeMillis()
            } else if (!directTriggered && SystemClock.uptimeMillis() - longSlideHoldFirstTriggerMs >= sg.longSlideHoldTriggerDelayMs) {
                val direction = sg.angle.directionOf(subGestureAccum)
                val action = sg.longSlideHoldActionFor(direction)
                if (action.hasMeaningfulAction()) {
                    directTriggered = true
                    return resolve(sg, direction, GestureTriggerType.LongSlideHold)
                }
            }
        } else {
            longSlideHoldFirstTriggerMs = 0L
        }
        if (canReachSlide && !canReachLongSlide) {
            if (slideHoldFirstTriggerMs == 0L) {
                slideHoldFirstTriggerMs = SystemClock.uptimeMillis()
            } else if (!directTriggered && SystemClock.uptimeMillis() - slideHoldFirstTriggerMs >= sg.slideHoldTriggerDelayMs) {
                val direction = sg.angle.directionOf(subGestureAccum)
                val action = sg.slideHoldActionFor(direction)
                if (action.hasMeaningfulAction()) {
                    directTriggered = true
                    return resolve(sg, direction, GestureTriggerType.SlideHold)
                }
            }
        } else {
            slideHoldFirstTriggerMs = 0L
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
        if (distance >= sg.effectiveLongSlideTriggerDistance && sg.longSlideActionFor(direction).hasMeaningfulAction()) {
            return resolve(sg, direction, GestureTriggerType.LongSlide)
        } else if (distance >= sg.triggerDistance && sg.slideActionFor(direction).hasMeaningfulAction()) {
            return resolve(sg, direction, GestureTriggerType.Slide)
        } else {
            subGestureAccum = Offset.Zero
            return null
        }
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
        slideHoldFirstTriggerMs = 0L
        longSlideHoldFirstTriggerMs = 0L
        directTriggered = false
        subGestureDepth = 0
        timeoutJob?.cancel()
        timeoutJob = null
        if (notifyService) onModeChanged(false, Offset.Unspecified, 0)
    }

    private fun scheduleTimeout() {
        timeoutJob?.cancel()
        val ms = activeSubGesture?.timeoutMs ?: return
        timeoutJob = scope.launch {
            delay(ms)
            clear()
        }
    }

    private fun restartTimeout() {
        scheduleTimeout()
    }

    private fun resolve(
        subGesture: SubGesture,
        direction: GestureDirection,
        triggerType: GestureTriggerType,
    ): SubGestureResolvedActions {
        val action = when (triggerType) {
            GestureTriggerType.Slide -> subGesture.slideActionFor(direction)
            GestureTriggerType.SlideHold -> subGesture.slideHoldActionFor(direction)
            GestureTriggerType.LongSlide -> subGesture.longSlideActionFor(direction)
            GestureTriggerType.LongSlideHold -> subGesture.longSlideHoldActionFor(direction)
            else -> null
        }
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
        slideHoldFirstTriggerMs = 0L
        longSlideHoldFirstTriggerMs = 0L
        subGesture.tryVibrateFor(triggerType)
        directTriggered = false
        onModeChanged(false, Offset.Unspecified, 0)
        return SubGestureResolvedActions(
        subGesture,
        direction,
        action,
        triggerType,
        touchPosition,
        GestureDecision.Trigger(triggerType, direction, direction),
    )
    }

    private fun Offset.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite()

    private fun validOrUnspecified(offset: Offset): Offset {
        return if (offset.hasFiniteCoordinates()) offset else Offset.Unspecified
    }

    private fun StoredAction?.hasMeaningfulAction(): Boolean =
        this != null && typeId != Action.NONE_TYPE_ID
}
