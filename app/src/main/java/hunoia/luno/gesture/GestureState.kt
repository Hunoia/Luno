package hunoia.luno.gesture

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.isHoldType
import hunoia.luno.core.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class GestureResolvedActions(
    val button: GestureButton,
    val direction: GestureDirection,
    val actionDirection: GestureDirection,
    val action: StoredAction?,
    val triggerType: GestureTriggerType,
    val touchPosition: Offset,
    /** 触发这次解析的原始识别结果：透传给 Coordinator 做仲裁/记录，不再就地截留。 */
    val decision: GestureDecision.Trigger,
)

class GestureState(
    private val coroutineScope: CoroutineScope,
    private val buttons: List<GestureButton>,
    advancedSettings: AdvancedSettings = AdvancedSettings(),
    gestureSettings: GestureSettings = GestureSettings(),
    private val touchTargetProvider: (List<GestureButton>, Offset, Int, TouchWindowHint?) -> GestureTouchTarget? = { buttons, offset, imePadding, hint ->
        buttons.findTouchTarget(offset, imePadding, hint)
    },
    private val thresholdsProvider: (GestureButton) -> GestureThresholds = { button ->
        GestureThresholds(
            touchSlop = ViewConfiguration.get(AppContext.get()).scaledTouchSlop.toFloat(),
            longPressDelayMs = button.longPressTriggerDelayMs,
            doubleTapDelayMs = button.doubleTapTriggerDelayMs,
            slideHoldDelayMs = button.slideHoldTriggerDelayMs,
            longSlideHoldDelayMs = button.longSlideHoldTriggerDelayMs,
            slideTriggerDistance = button.slideTriggerDistance.toFloat(),
            longSlideTriggerDistance = button.effectiveLongSlideTriggerDistance.toFloat(),
        )
    },
    private val timeProvider: () -> Long = { SystemClock.uptimeMillis() },
) {

    var isCanceled: Boolean by mutableStateOf(false)
        private set

    /** 只在 onDragStart 构建一次：onDrag 每个 ACTION_MOVE 都会走，而内容整个会话不变。 */
    private var sessionConfig: GestureConfig? = null

    var button: GestureButton? by mutableStateOf(null)
        private set
    var effectiveButton: GestureButton? by mutableStateOf(null)
        private set
    var triggerDirection: GestureDirection by mutableStateOf(GestureDirection.Right)
        private set
    var actionDirection: GestureDirection by mutableStateOf(GestureDirection.Right)
        private set

    var origin = Offset.Unspecified
        private set
    var finger = Offset.Unspecified
        private set

    var onResolved: (GestureResolvedActions) -> Unit = {}

    private var isMirrorTouchTarget = false
    private var longPressCheckJob: Job? = null
    private var pendingDoubleTapJob: Job? = null
    private var pendingTapResult: GestureResolvedActions? = null

    private val recognizer = GestureRecognizer()

    fun onDragStart(offset: Offset, imePadding: Int, hint: TouchWindowHint? = null) {
        isCanceled = false
        val touchTarget = touchTargetProvider(buttons, offset, imePadding, hint)
        val nextButton = touchTarget?.sourceButton
        val previousPendingTap = pendingTapResult
        if (previousPendingTap != null) {
            pendingDoubleTapJob?.cancel()
            pendingDoubleTapJob = null
            if (nextButton?.id != previousPendingTap.button.id) {
                settlePendingTap()
            }
        }


        origin = offset
        finger = offset
        button = nextButton
        effectiveButton = touchTarget?.effectiveButton
        isMirrorTouchTarget = touchTarget?.isMirror == true

        val b = button ?: run {
            effectiveButton = null
            sessionConfig = null
            recognizer.clearPendingDoubleTap()
            return
        }

        if (previousPendingTap?.button?.id == b.id) {
            pendingTapResult = null
        }

        val config = buildRecognizerConfig(b).also { sessionConfig = it }
        recognizer.onDown(timeProvider(), offset, config)

        if (config.hasLongPressActions) {
            longPressCheckJob?.cancel()
            longPressCheckJob = coroutineScope.launch {
                delay(config.thresholds.longPressDelayMs)
                val decision = recognizer.checkTime(timeProvider(), config)
                if (decision is GestureDecision.Trigger &&
                    decision.triggerType == GestureTriggerType.LongPress
                ) {
                    b.tryVibrateFor(GestureTriggerType.LongPress)
                    onResolved(
                        GestureResolvedActions(
                            button = b,
                            direction = GestureDirection.Right,
                            actionDirection = GestureDirection.Right,
                            action = b.longPressActions,
                            triggerType = GestureTriggerType.LongPress,
                            touchPosition = finger,
                            decision = decision,
                        )
                    )
                }
            }
        }
    }

    fun onDrag(dragAmount: Offset): GestureResolvedActions? {
        finger += dragAmount

        val config = sessionConfig ?: return null
        val b = button ?: return null

        val decision = recognizer.onMove(timeProvider(), dragAmount, config)

        triggerDirection = recognizer.triggerDirection
        actionDirection = recognizer.actionDirection

        val distance = kotlin.math.hypot(
            (finger.x - origin.x).toDouble(),
            (finger.y - origin.y).toDouble(),
        ).toFloat()
        if (distance > config.thresholds.touchSlop) {
            pendingDoubleTapJob?.cancel()
            pendingDoubleTapJob = null
        }

        if (decision is GestureDecision.Trigger && decision.triggerType.isHoldType) {
            val (action, actualType) = actionsFor(b, decision)
            if (action.hasMeaningfulAction()) {
                vibrateFor(b, actualType)
                return resolved(b, decision.actionDirection, actualType, action, decision)
            }
        }

        return null
    }

    fun onDragEnd(): GestureResolvedActions? {
        longPressCheckJob?.cancel()
        longPressCheckJob = null

        val config = sessionConfig ?: return null
        val b = button ?: return null

        val decision = recognizer.onUp(timeProvider(), config)

        when (decision) {
            is GestureDecision.Noop -> {
                reset()
                return null
            }
            is GestureDecision.Trigger -> {
                val (action, actualType) = actionsFor(b, decision)
                vibrateFor(b, actualType)
                // 传实际回退类型（不是 decision 的名义类型）：面板样式、hold 判定
                // 都按真正执行的动作类型走。
                val result = resolved(b, decision.actionDirection, actualType, action, decision)
                reset()
                return result
            }
            is GestureDecision.PendingDoubleTap -> {
                val (tapActions, _) = actionsFor(b, decision.tapDecision)
                val tapResult = resolved(
                    b,
                    decision.tapDecision.actionDirection,
                    decision.tapDecision.triggerType,
                    tapActions,
                    decision.tapDecision,
                )
                pendingTapResult = tapResult
                pendingDoubleTapJob = coroutineScope.launch {
                    delay(config.thresholds.doubleTapDelayMs)
                    recognizer.clearPendingDoubleTap()
                    if (pendingTapResult == tapResult) settlePendingTap()
                }
                resetSession()
                return null
            }
            is GestureDecision.Cancel -> {
                reset()
                return null
            }
        }
    }

    fun cancel() {
        if (isCanceled) return
        reset()
        isCanceled = true
    }

    fun onDragCancel() {
        recognizer.onCancel()
        pendingTapResult = null
        pendingDoubleTapJob?.cancel()
        pendingDoubleTapJob = null
        reset()
    }

    private fun reset() {
        longPressCheckJob?.cancel()
        longPressCheckJob = null
        pendingDoubleTapJob?.cancel()
        pendingDoubleTapJob = null
        pendingTapResult = null
        resetSession()
        isCanceled = false
    }

    private fun settlePendingTap() {
        val result = pendingTapResult
        pendingTapResult = null
        pendingDoubleTapJob?.cancel()
        pendingDoubleTapJob = null
        recognizer.clearPendingDoubleTap()
        if (result != null && result.action.hasMeaningfulAction()) {
            result.button.tryVibrateFor(GestureTriggerType.Tap)
            onResolved(result)
        }
    }

    private fun resetSession() {
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        button = null
        effectiveButton = null
        sessionConfig = null
        isMirrorTouchTarget = false
        triggerDirection = GestureDirection.Right
        actionDirection = GestureDirection.Right
    }

    private fun resolved(
        button: GestureButton,
        actionDirection: GestureDirection,
        triggerType: GestureTriggerType,
        action: StoredAction?,
        decision: GestureDecision.Trigger,
    ): GestureResolvedActions {
        return GestureResolvedActions(
            button = button,
            direction = triggerDirection,
            actionDirection = actionDirection,
            action = action,
            triggerType = triggerType,
            touchPosition = finger,
            decision = decision,
        )
    }

    private fun buildRecognizerConfig(button: GestureButton): GestureConfig {
        val thresholds = thresholdsProvider(button)
        return GestureConfig(
            buttonId = button.id,
            angle = button.angle,
            thresholds = thresholds,
            isMirrorHorizontal = isMirrorTouchTarget,
            hasTapActions = button.tapActions.hasMeaningfulAction(),
            hasDoubleTapActions = button.doubleTapActions.hasMeaningfulAction(),
            hasLongPressActions = button.longPressActions.hasMeaningfulAction(),
            hasActionInDirection = { type, direction ->
                // 回退资格与触发一致：非按住只看滑动俩，按住看四取一（同方向）。
                val types = if (type.isHoldType) {
                    listOf(
                        GestureTriggerType.Slide,
                        GestureTriggerType.LongSlide,
                        GestureTriggerType.SlideHold,
                        GestureTriggerType.LongSlideHold,
                    )
                } else {
                    listOf(GestureTriggerType.Slide, GestureTriggerType.LongSlide)
                }
                types.any { t -> actionByType(button, t, direction).hasMeaningfulAction() }
            },
        )
    }

    /**
     * 同方向回退：非按住只在滑动俩之间（Slide↔LongSlide），按住先 hold 后滑动。
     * 返回（动作，实际类型）：震动与面板按实际类型走，不按 decision 类型。
     */
    private fun fallbackOrder(primary: GestureTriggerType): List<GestureTriggerType> = when (primary) {
        GestureTriggerType.Slide -> listOf(GestureTriggerType.Slide, GestureTriggerType.LongSlide)
        GestureTriggerType.LongSlide -> listOf(GestureTriggerType.LongSlide, GestureTriggerType.Slide)
        GestureTriggerType.SlideHold -> listOf(
            GestureTriggerType.SlideHold,
            GestureTriggerType.LongSlideHold,
            GestureTriggerType.Slide,
            GestureTriggerType.LongSlide,
        )
        GestureTriggerType.LongSlideHold -> listOf(
            GestureTriggerType.LongSlideHold,
            GestureTriggerType.SlideHold,
            GestureTriggerType.Slide,
            GestureTriggerType.LongSlide,
        )
        else -> listOf(primary)
    }

    private fun actionsFor(
        button: GestureButton,
        decision: GestureDecision.Trigger,
    ): Pair<StoredAction?, GestureTriggerType> {
        for (type in fallbackOrder(decision.triggerType)) {
            val action = actionByType(button, type, decision.actionDirection)
            if (action.hasMeaningfulAction()) return action to type
        }
        return null to decision.triggerType
    }

    private fun actionByType(
        button: GestureButton,
        type: GestureTriggerType,
        direction: GestureDirection,
    ): StoredAction? {
        return when (type) {
            GestureTriggerType.Tap -> button.tapActions
            GestureTriggerType.DoubleTap -> button.doubleTapActions
            GestureTriggerType.LongPress -> button.longPressActions
            GestureTriggerType.Slide -> button.slideActions.actionBy(direction)
            GestureTriggerType.SlideHold -> button.slideHoldActions.actionBy(direction)
            GestureTriggerType.LongSlide -> button.longSlideActions.actionBy(direction)
            GestureTriggerType.LongSlideHold -> button.longSlideHoldActions.actionBy(direction)
        }
    }

    private fun vibrateFor(button: GestureButton, type: GestureTriggerType) {
        button.tryVibrateFor(type)
    }

    private fun StoredAction?.hasMeaningfulAction(): Boolean =
        this != null && typeId != "internal.none"
}
