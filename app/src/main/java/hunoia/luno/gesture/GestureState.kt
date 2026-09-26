package hunoia.luno.gesture

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.Action
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
    val actions: List<Action>,
    val triggerType: GestureTriggerType,
    val touchPosition: Offset,
)

class GestureState(
    private val coroutineScope: CoroutineScope,
    private val buttons: List<GestureButton>,
    advancedSettings: AdvancedSettings = AdvancedSettings(),
    gestureSettings: GestureSettings = GestureSettings(),
    private val touchTargetProvider: (List<GestureButton>, Offset, Int) -> GestureTouchTarget? = { buttons, offset, imePadding ->
        buttons.findTouchTarget(offset, imePadding)
    },
    private val thresholdsProvider: (GestureButton) -> GestureThresholds = { button ->
        GestureThresholds(
            touchSlop = ViewConfiguration.get(AppContext.get()).scaledTouchSlop.toFloat(),
            longPressDelayMs = button.longPressTriggerDelayMs,
            doubleTapDelayMs = button.doubleTapTriggerDelayMs,
            slideHoldDelayMs = button.slideHoldTriggerDelayMs,
            longSlideHoldDelayMs = button.longSlideHoldTriggerDelayMs,
            slideTriggerDistance = button.slideTriggerDistance.toFloat(),
            longSlideTriggerDistance = button.longSlideTriggerDistance.toFloat(),
        )
    },
    private val timeProvider: () -> Long = { SystemClock.uptimeMillis() },
) {

    var isCanceled: Boolean by mutableStateOf(false)
        private set

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

    data class AnimState(
        val originX: Float = Float.NaN,
        val originY: Float = Float.NaN,
        val fingerX: Float = Float.NaN,
        val fingerY: Float = Float.NaN,
    )

    private var animState by mutableStateOf(AnimState())

    val originXAnimVal: Float get() = animState.originX
    val originYAnimVal: Float get() = animState.originY
    val fingerXAnimVal: Float get() = animState.fingerX
    val fingerYAnimVal: Float get() = animState.fingerY

    var onResolved: (GestureResolvedActions) -> Unit = {}

    private var isMirrorTouchTarget = false
    private var slideVibrationFlags = false
    private var longPressCheckJob: Job? = null
    private var pendingDoubleTapJob: Job? = null
    private var pendingTapResult: GestureResolvedActions? = null

    private val recognizer = GestureRecognizer()

    fun onDragStart(offset: Offset, imePadding: Int) {
        isCanceled = false
        val touchTarget = touchTargetProvider(buttons, offset, imePadding)
        val nextButton = touchTarget?.sourceButton
        val previousPendingTap = pendingTapResult
        if (previousPendingTap != null) {
            pendingDoubleTapJob?.cancel()
            pendingDoubleTapJob = null
            if (nextButton?.id != previousPendingTap.button.id) {
                settlePendingTap(triggerFallback = true)
            }
        }

        origin = offset
        finger = offset
        button = nextButton
        effectiveButton = touchTarget?.effectiveButton
        isMirrorTouchTarget = touchTarget?.isMirror == true

        val b = button ?: run {
            effectiveButton = null
            animState = AnimState()
            recognizer.clearPendingDoubleTap()
            return
        }

        slideVibrationFlags = false
        if (previousPendingTap?.button?.id == b.id) {
            pendingTapResult = null
        } else {
            pendingDoubleTapJob?.cancel()
            pendingDoubleTapJob = null
        }

        val config = buildRecognizerConfig(b)
        recognizer.onDown(timeProvider(), offset, config)

        if (config.hasLongPressActions) {
            longPressCheckJob?.cancel()
            longPressCheckJob = coroutineScope.launch {
                delay(config.thresholds.longPressDelayMs)
                val decision = recognizer.checkTime(timeProvider(), config)
                if (decision is GestureDecision.Trigger &&
                    decision.triggerType == GestureTriggerType.LongPress
                ) {
                    b.tryVibrateForLongPress()
                    onResolved(
                        GestureResolvedActions(
                            button = b,
                            direction = GestureDirection.Right,
                            actionDirection = GestureDirection.Right,
                            actions = b.longPressActions,
                            triggerType = GestureTriggerType.LongPress,
                            touchPosition = finger,
                        )
                    )
                }
            }
        }

        animState = AnimState(originX = offset.x, originY = offset.y, fingerX = offset.x, fingerY = offset.y)
    }

    fun onDrag(dragAmount: Offset): GestureResolvedActions? {
        finger += dragAmount

        val b = button ?: return null

        val config = buildRecognizerConfig(b)
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

        val prev = animState
        animState = prev.copy(fingerX = prev.fingerX + dragAmount.x, fingerY = prev.fingerY + dragAmount.y)

        if (decision is GestureDecision.Trigger && decision.triggerType.isHoldType) {
            val actions = actionsFor(b, decision)
            if (actions.hasMeaningfulActions()) {
                directTriggeredVibration(decision.triggerType, b)
                return resolved(b, decision.actionDirection, decision.triggerType, actions)
            }
        }

        if (b.vibrateImmediately) {
            val canTriggerSlide = distance >= config.thresholds.slideTriggerDistance
            if (canTriggerSlide && !slideVibrationFlags) {
                slideVibrationFlags = true
                b.tryVibrateForSlide()
            }
        }

        return null
    }

    fun onDragEnd(): GestureResolvedActions? {
        longPressCheckJob?.cancel()
        longPressCheckJob = null

        val b = button ?: return null

        val config = buildRecognizerConfig(b)
        val decision = recognizer.onUp(timeProvider(), config)

        when (decision) {
            is GestureDecision.Noop -> {
                reset()
                return null
            }
            is GestureDecision.Trigger -> {
                val actions = actionsFor(b, decision)
                if (decision.triggerType == GestureTriggerType.DoubleTap) {
                    b.tryVibrateForTap()
                } else if (decision.triggerType == GestureTriggerType.Tap) {
                    if (!slideVibrationFlags) b.tryVibrateForTap()
                } else if (decision.triggerType == GestureTriggerType.LongSlide) {
                    b.tryVibrateForLongSlide()
                } else if (decision.triggerType == GestureTriggerType.Slide) {
                    if (!slideVibrationFlags) b.tryVibrateForSlide()
                }
                val result = resolved(b, decision.actionDirection, decision.triggerType, actions)
                reset()
                return result
            }
            is GestureDecision.PendingDoubleTap -> {
                val tapResult = resolved(
                    b,
                    decision.tapDecision.actionDirection,
                    decision.tapDecision.triggerType,
                    actionsFor(b, decision.tapDecision),
                )
                pendingTapResult = tapResult
                pendingDoubleTapJob = coroutineScope.launch {
                    delay(config.thresholds.doubleTapDelayMs)
                    recognizer.clearPendingDoubleTap()
                    if (pendingTapResult == tapResult) settlePendingTap(triggerFallback = true)
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

    fun cancelPendingTap() {
        recognizer.clearPendingDoubleTap()
        pendingDoubleTapJob?.cancel()
        pendingDoubleTapJob = null
        pendingTapResult = null
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

    private fun settlePendingTap(triggerFallback: Boolean) {
        val result = pendingTapResult
        pendingTapResult = null
        pendingDoubleTapJob?.cancel()
        pendingDoubleTapJob = null
        recognizer.clearPendingDoubleTap()
        if (triggerFallback && result != null && result.actions.hasMeaningfulActions()) {
            result.button.tryVibrateForTap()
            onResolved(result)
        }
    }

    private fun resetSession() {
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        button = null
        effectiveButton = null
        isMirrorTouchTarget = false
        animState = AnimState()
        slideVibrationFlags = false
        triggerDirection = GestureDirection.Right
        actionDirection = GestureDirection.Right
    }

    private fun resolved(
        button: GestureButton,
        actionDirection: GestureDirection,
        triggerType: GestureTriggerType,
        actions: List<Action>,
    ): GestureResolvedActions {
        return GestureResolvedActions(
            button = button,
            direction = triggerDirection,
            actionDirection = actionDirection,
            actions = actions,
            triggerType = triggerType,
            touchPosition = finger,
        )
    }

    private fun buildRecognizerConfig(button: GestureButton): GestureConfig {
        val thresholds = thresholdsProvider(button)
        return GestureConfig(
            buttonId = button.id,
            angle = button.angle,
            thresholds = thresholds,
            isMirrorHorizontal = isMirrorTouchTarget,
            hasTapActions = button.tapActions.hasMeaningfulActions(),
            hasDoubleTapActions = button.doubleTapActions.hasMeaningfulActions(),
            hasLongPressActions = button.longPressActions.hasMeaningfulActions(),
            hasActionInDirection = { type, direction ->
                val actions = when (type) {
                    GestureTriggerType.Slide -> button.slideActions.actionsBy(direction)
                    GestureTriggerType.SlideHold -> button.slideHoldActions.actionsBy(direction)
                    GestureTriggerType.LongSlide -> button.longSlideActions.actionsBy(direction)
                    GestureTriggerType.LongSlideHold -> button.longSlideHoldActions.actionsBy(direction)
                    else -> emptyList()
                }
                actions.any { it != Action.NONE }
            },
        )
    }

    private fun actionsFor(button: GestureButton, decision: GestureDecision.Trigger): List<Action> {
        return when (decision.triggerType) {
            GestureTriggerType.Tap -> button.tapActions
            GestureTriggerType.DoubleTap -> button.doubleTapActions
            GestureTriggerType.LongPress -> button.longPressActions
            GestureTriggerType.Slide -> button.slideActions.actionsBy(decision.actionDirection)
            GestureTriggerType.SlideHold -> button.slideHoldActions.actionsBy(decision.actionDirection)
            GestureTriggerType.LongSlide -> button.longSlideActions.actionsBy(decision.actionDirection)
            GestureTriggerType.LongSlideHold -> button.longSlideHoldActions.actionsBy(decision.actionDirection)
        }
    }

    private fun directTriggeredVibration(triggerType: GestureTriggerType, button: GestureButton) {
        when (triggerType) {
            GestureTriggerType.SlideHold -> {
                if (!slideVibrationFlags) button.tryVibrateForSlide()
            }
            GestureTriggerType.LongSlideHold -> button.tryVibrateForLongSlide()
            else -> {}
        }
    }

    private fun List<Action>.hasMeaningfulActions(): Boolean = any { it != Action.NONE }
}
