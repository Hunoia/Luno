package hunoia.luno.ui.component.container

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.isHoldType
import hunoia.luno.config.model.isLongSlideType
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.effectiveFor
import hunoia.luno.gesture.DragGestureHandler
import hunoia.luno.gesture.GestureResolvedActions
import hunoia.luno.gesture.GestureFacade
import hunoia.luno.ui.component.panel.ActionPanel
import hunoia.luno.ui.component.panel.rememberActionPanelState
import hunoia.luno.gesture.GestureState
import hunoia.luno.gesture.SubGestureState
import hunoia.luno.gesture.VolumeScrubState
import hunoia.luno.pointer.rememberPointerHandle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SUB_GESTURE_DIRECT_ACTION_DELAY_MS = 50L

@Composable
fun SideGestureContainer(
    onAction: (Action, GestureButton?, GestureButtonActionSettingsOverride?) -> Unit,
    buttons: List<GestureButton>,
    modifier: Modifier = Modifier,
    imePadding: Int = 0,
    actionPanelStyle: ActionPanelStyle = ArcStyle(),
    actionSettings: ActionSettings = ActionSettings(),
    advancedSettings: AdvancedSettings = AdvancedSettings(),
    gestureSettings: GestureSettings = GestureSettings(),
    onPointerStart: (GestureSettings.Pointer) -> Boolean = { false },
    onPointerShow: (GestureSettings.Pointer) -> Boolean = { false },
    onPointerEnd: () -> Unit = {},
    onPointerActionAtPosition: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    subGestureSettings: SubGestureSettings = SubGestureSettings(),
    onSubGestureModeChanged: (Boolean, Offset, Int) -> Unit = { _, _, _ -> },
    onActionPanelOverlayChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val curOnAction by rememberUpdatedState(newValue = onAction)
    val curOnSubGestureModeChanged by rememberUpdatedState(newValue = onSubGestureModeChanged)
    val coroutineScope = rememberCoroutineScope()
    val sideGestureState = rememberGestureState(buttons, advancedSettings, gestureSettings)
    val actionPanelState = rememberActionPanelState()
    var actionPanelSourceOverride by remember { mutableStateOf<GestureButtonActionSettingsOverride?>(null) }
    var actionPanelPointerUsesRuntimeOverlay by remember { mutableStateOf(false) }
    var pointerStartedFromSubGesture by remember { mutableStateOf(false) }
    val pointerHandle = rememberPointerHandle(
        gestureSettings = gestureSettings,
        onPointerStart = onPointerStart,
        shouldPreservePointerCancel = {
            pointerStartedFromSubGesture
        },
        onPointerActionAtPosition = onPointerActionAtPosition,
        onPointerEnd = onPointerEnd,
    )
    val subGestureState = remember(subGestureSettings, coroutineScope) {
        SubGestureState(coroutineScope, subGestureSettings, curOnSubGestureModeChanged)
    }
    val volumeScrubState = remember(context) {
        VolumeScrubState(context) { active ->
            curOnSubGestureModeChanged(active, Offset.Unspecified, 0)
        }
    }

    fun exitSubGestureOverlay() {
        curOnSubGestureModeChanged(false, Offset.Unspecified, 0)
    }

    fun effectiveActionSettings(override: GestureButtonActionSettingsOverride?): ActionSettings = actionSettings.effectiveFor(override)

    fun effectiveActionSettings(button: GestureButton?): ActionSettings = effectiveActionSettings(button?.actionSettingsOverride)

    fun effectivePointerSettings(override: GestureButtonActionSettingsOverride?): GestureSettings.Pointer {
        return gestureSettings.effectiveFor(override).pointer
    }

    fun effectivePointerSettings(button: GestureButton?): GestureSettings.Pointer {
        return effectivePointerSettings(button?.actionSettingsOverride)
    }

    fun handleResolvedAction(
        action: Action,
        sourceButton: GestureButton?,
        touchPosition: Offset,
        sourceOverride: GestureButtonActionSettingsOverride? = sourceButton?.actionSettingsOverride,
    ): Boolean {
        if (subGestureState.tryEnterSubGesture(action, touchPosition)) return true
        val resolvedAction = if (!touchPosition.x.isFinite() || !touchPosition.y.isFinite()) action
        else Action(value = action.value, data = action.data, extra = listOf(touchPosition.x.roundToInt(), touchPosition.y.roundToInt()), longPressAction = action.longPressAction)
        curOnAction(resolvedAction, sourceButton, sourceOverride)
        return false
    }

    fun runGestureActions(
        actions: List<Action>,
        direction: GestureDirection,
        touchPosition: Offset,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride? = sourceButton?.actionSettingsOverride,
        panelStyle: ActionPanelStyle = actionPanelStyle,
        panelColor: Int = sourceButton?.color ?: android.graphics.Color.TRANSPARENT,
        onPanelStarted: () -> Unit = {},
        onPointerStarted: (Boolean) -> Unit = {},
        needsPanelOverlay: Boolean = false,
        pointerUsesRuntimeOverlay: Boolean = false,
        onDirectComplete: (enteredSubGesture: Boolean) -> Unit = {},
    ) {
        val meaningfulActions = actions.filter { it != Action.NONE }
        if (meaningfulActions.size > 1) {
            if (needsPanelOverlay) onActionPanelOverlayChanged(true)
            actionPanelState.onDragStart(touchPosition)
            actionPanelState.ready(direction, meaningfulActions, panelStyle, panelColor)
            actionPanelSourceOverride = sourceOverride
            actionPanelPointerUsesRuntimeOverlay = pointerUsesRuntimeOverlay
            onPanelStarted()
            return
        }
        val action = meaningfulActions.firstOrNull() ?: Action.NONE
        val fromSubGesture = subGestureState.subGestureDepth > 0
        val shouldDelayDirectSubGestureAction = fromSubGesture &&
            action.value != ActionFacade.SUB_GESTURE &&
            action.value != ActionFacade.POINTER &&
            action.value != ActionFacade.VOLUME_SCRUB &&
            action.value != ActionFacade.NONE
        if (shouldDelayDirectSubGestureAction) {
            subGestureState.clear(notifyService = true)
            coroutineScope.launch {
                delay(SUB_GESTURE_DIRECT_ACTION_DELAY_MS)
                handleResolvedAction(action, sourceButton, touchPosition, sourceOverride)
            }
            onDirectComplete(false)
            return
        }
        val enteredSubGesture = when (action.value) {
            ActionFacade.VOLUME_SCRUB -> {
                volumeScrubState.activate(effectiveActionSettings(sourceOverride))
                false
            }
            ActionFacade.POINTER -> {
                val pointerSettings = effectivePointerSettings(sourceOverride)
                val started = if (pointerUsesRuntimeOverlay) {
                    onPointerShow(pointerSettings)
                } else {
                    pointerHandle.start(pointerSettings, touchPosition)
                }
                onPointerStarted(started)
                false
            }
            ActionFacade.NONE -> false
            else -> handleResolvedAction(action, sourceButton, touchPosition, sourceOverride)
        }
        onDirectComplete(enteredSubGesture)
    }

    fun handleSubGestureResolvedActions(resolvedActions: hunoia.luno.gesture.SubGestureResolvedActions) {
        val sourceOverride = resolvedActions.subGesture.actionSettingsOverride
        val panelColor = resolvedActions.subGesture.color.takeUnless { it == android.graphics.Color.TRANSPARENT }
            ?: sideGestureState.button?.color
            ?: android.graphics.Color.TRANSPARENT
        val touchPosition = resolvedActions.touchPosition.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: sideGestureState.finger.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: Offset.Zero
        val panelStyle = if (resolvedActions.triggerType == GestureTriggerType.LongSlide || resolvedActions.triggerType == GestureTriggerType.LongSlideHold) {
            GestureFacade.styleBy(resolvedActions.subGesture.longSlideActionPanelStyles, resolvedActions.direction).value
        } else {
            actionPanelStyle
        }
        val meaningfulCount = resolvedActions.actions.count { it != Action.NONE }
        val needsOverlay = meaningfulCount > 1 && !resolvedActions.triggerType.isHoldType
        runGestureActions(
            actions = resolvedActions.actions,
            direction = resolvedActions.direction,
            touchPosition = touchPosition,
            sourceButton = sideGestureState.button,
            sourceOverride = sourceOverride,
            panelStyle = panelStyle,
            panelColor = panelColor,
            needsPanelOverlay = needsOverlay,
            pointerUsesRuntimeOverlay = !resolvedActions.triggerType.isHoldType,
            onPanelStarted = {
                sideGestureState.cancel()
            },
            onPointerStarted = { started ->
                pointerStartedFromSubGesture = started && resolvedActions.triggerType.isHoldType
            },
        ) { enteredSubGesture ->
            sideGestureState.cancel()
            if (!enteredSubGesture && subGestureState.subGestureDepth > 0) {
                subGestureState.clear(notifyService = pointerStartedFromSubGesture.not())
            }
        }
    }

    fun handleGestureResolvedActions(resolvedActions: GestureResolvedActions) {
        runGestureActions(
            actions = resolvedActions.actions,
            direction = resolvedActions.actionDirection,
            touchPosition = resolvedActions.touchPosition,
            sourceButton = resolvedActions.button,
            panelStyle = if (resolvedActions.triggerType == GestureTriggerType.LongSlide || resolvedActions.triggerType == GestureTriggerType.LongSlideHold) {
                GestureFacade.styleBy(resolvedActions.button.longSlideActionPanelStyles, resolvedActions.actionDirection).value
            } else {
                actionPanelStyle
            },
            panelColor = resolvedActions.button.color,
            pointerUsesRuntimeOverlay = !resolvedActions.triggerType.isHoldType,
            onPanelStarted = {
                if (resolvedActions.triggerType == GestureTriggerType.LongPress ||
                    resolvedActions.triggerType == GestureTriggerType.SlideHold ||
                    resolvedActions.triggerType == GestureTriggerType.LongSlideHold
                ) {
                    sideGestureState.cancel()
                }
            },
        ) {
            sideGestureState.cancel()
        }
    }

    SideEffect {
        sideGestureState.onResolved = { resolvedActions ->
            handleGestureResolvedActions(resolvedActions)
        }
    }

    DragGestureHandler(
        onDragStart = onDragStart@{ offset ->
            if (actionPanelState.visible) {
                actionPanelState.onSelectStart(offset)
                return@onDragStart
            }
            if (subGestureState.isActive) {
                subGestureState.onDragStart(offset)
                return@onDragStart
            }
            sideGestureState.onDragStart(offset, imePadding)
        },
        onDrag = onDrag@{ dragAmount ->
            if (subGestureState.isActive) {
                val resolvedActions = subGestureState.onDrag(dragAmount)
                if (resolvedActions != null) {
                    handleSubGestureResolvedActions(resolvedActions)
                }
                if (resolvedActions == null && !subGestureState.isActive) {
                    sideGestureState.cancel()
                    subGestureState.clear()
                }
                return@onDrag
            }
            if (pointerHandle.isActive) {
                if (!pointerHandle.onDrag(dragAmount)) return@onDrag
                return@onDrag
            }
            if (volumeScrubState.isActive) {
                volumeScrubState.onDrag(dragAmount)
                return@onDrag
            }
            if (actionPanelState.visible) {
                actionPanelState.onDrag(dragAmount)
                return@onDrag
            }
            if (!sideGestureState.isCanceled) {
                val resolvedActions = sideGestureState.onDrag(dragAmount)
                val button = sideGestureState.button
                if (button == null) {
                    sideGestureState.cancel()
                } else if (resolvedActions != null) {
                    handleGestureResolvedActions(resolvedActions)
                }
            }
        },
        onDragEnd = onDragEnd@{
            if (subGestureState.isActive) {
                val resolvedActions = subGestureState.onDragEnd()
                if (resolvedActions != null) {
                    handleSubGestureResolvedActions(resolvedActions)
                } else {
                    sideGestureState.cancel()
                     subGestureState.clear()
                }
                return@onDragEnd
            }
            if (pointerStartedFromSubGesture && !pointerHandle.isActive) {
                pointerStartedFromSubGesture = false
                exitSubGestureOverlay()
                return@onDragEnd
            }
            if (pointerHandle.isActive) {
                val fromSubGesture = pointerStartedFromSubGesture
                pointerHandle.onDragEnd()
                if (fromSubGesture) {
                    pointerStartedFromSubGesture = false
                    exitSubGestureOverlay()
                } else {
                    exitSubGestureOverlay()
                }
                return@onDragEnd
            }
            if (volumeScrubState.isActive) {
                volumeScrubState.onDragEnd()
                return@onDragEnd
            }
            if (actionPanelState.visible) {
                val touchPosition = actionPanelState.finger
                val hitAction = actionPanelState.hitTestAction(touchPosition)
                val sourceOverride = actionPanelSourceOverride
                val pointerUsesRuntimeOverlay = actionPanelPointerUsesRuntimeOverlay
                actionPanelSourceOverride = null
                actionPanelPointerUsesRuntimeOverlay = false
                actionPanelState.cancel()
                onActionPanelOverlayChanged(false)
                val fromSubGesturePanel = subGestureState.subGestureDepth > 0
                if (fromSubGesturePanel) {
                    subGestureState.clear(notifyService = true)
                }
                if (hitAction != null && hitAction != Action.NONE) {
                    val actionToRun: () -> Unit = {
                        when (hitAction.value) {
                            ActionFacade.VOLUME_SCRUB -> volumeScrubState.activate(effectiveActionSettings(sourceOverride))
                            ActionFacade.POINTER -> {
                                val pointerSettings = effectivePointerSettings(sourceOverride)
                                val started = if (pointerUsesRuntimeOverlay) {
                                    onPointerShow(pointerSettings)
                                } else {
                                    pointerHandle.start(pointerSettings, touchPosition)
                                }
                                if (fromSubGesturePanel && started && !pointerUsesRuntimeOverlay) pointerStartedFromSubGesture = true
                            }
                            else -> handleResolvedAction(hitAction, sideGestureState.button, touchPosition, sourceOverride)
                        }
                    }
                    if (fromSubGesturePanel) {
                        coroutineScope.launch {
                            delay(SUB_GESTURE_DIRECT_ACTION_DELAY_MS)
                            actionToRun()
                        }
                    } else {
                        actionToRun()
                    }
                }
                return@onDragEnd
            }
            if (!sideGestureState.isCanceled) {
                sideGestureState.onDragEnd()?.let { resolvedActions ->
                    val meaningfulActions = resolvedActions.actions.filter { it != Action.NONE }
                    if (meaningfulActions.size > 1 && !resolvedActions.triggerType.isHoldType) {
                        runGestureActions(
                            actions = resolvedActions.actions,
                            direction = resolvedActions.actionDirection,
                            touchPosition = resolvedActions.touchPosition,
                            sourceButton = resolvedActions.button,
                            panelStyle = if (resolvedActions.triggerType.isLongSlideType) {
                                GestureFacade.styleBy(resolvedActions.button.longSlideActionPanelStyles, resolvedActions.actionDirection).value
                            } else {
                                actionPanelStyle
                            },
                            panelColor = resolvedActions.button.color,
                            needsPanelOverlay = true,
                            pointerUsesRuntimeOverlay = true,
                        )
                    } else {
                        handleGestureResolvedActions(resolvedActions)
                    }
                }
            }
        },
        onDragCancel = onDragCancel@{
            if (subGestureState.isActive) {
                subGestureState.onDragCancel()
                return@onDragCancel
            }
            if (pointerHandle.isActive) {
                if (pointerStartedFromSubGesture) return@onDragCancel
                pointerStartedFromSubGesture = false
                exitSubGestureOverlay()
                pointerHandle.onDragCancel()
                return@onDragCancel
            }
            if (volumeScrubState.isActive) {
                volumeScrubState.onDragCancel()
                return@onDragCancel
            }
            if (actionPanelState.visible) {
                actionPanelState.onDragCancel()
                onActionPanelOverlayChanged(false)
                actionPanelSourceOverride = null
                actionPanelPointerUsesRuntimeOverlay = false
                if (subGestureState.subGestureDepth > 0) subGestureState.clear(notifyService = true)
            }
            sideGestureState.onDragCancel()
        }
    )
    Box(modifier = modifier) {
        ActionPanel(
            actionPanelStyle = actionPanelStyle,
            actionPanelState = actionPanelState,
            modifier = Modifier.matchParentSize(),
            gestureSettings = gestureSettings
        )
    }
}

@Composable
internal fun rememberGestureState(
    buttons: List<GestureButton>,
    advancedSettings: AdvancedSettings = AdvancedSettings(),
    gestureSettings: GestureSettings = GestureSettings()
): GestureState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope, buttons, advancedSettings, gestureSettings) {
        GestureState(coroutineScope, buttons, advancedSettings, gestureSettings)
    }
}

abstract class LongSlideState {

    var origin: Offset by mutableStateOf(Offset.Unspecified)
        protected set
    var finger: Offset by mutableStateOf(Offset.Unspecified)
        protected set

    open fun onDragStart(offset: Offset) {
        origin = offset
        finger = offset
    }

    open fun onDrag(dragAmount: Offset) {
        finger += dragAmount
    }

    open fun onDragEnd() {
        reset()
    }

    open fun onDragCancel() {
        reset()
    }

    protected open fun reset() {
        origin = Offset.Unspecified
        finger = Offset.Unspecified
    }
}
