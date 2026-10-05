package hunoia.luno.ui.component.container

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.volumeScrubConfigOrDefault
import hunoia.luno.config.model.ActionPanelStyle
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
import hunoia.luno.gesture.DragGestureHandler
import hunoia.luno.gesture.GestureDecision
import hunoia.luno.gesture.GestureResolvedActions
import hunoia.luno.ui.component.panel.ActionPanel
import hunoia.luno.ui.component.panel.rememberActionPanelState
import hunoia.luno.gesture.GestureState
import hunoia.luno.gesture.SubGestureState
import hunoia.luno.gesture.VolumeScrubState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SUB_GESTURE_DIRECT_ACTION_DELAY_MS = 50L

/**
 * ActionPanel 收起主 overlay 前的等待时间,用来盖住它的淡出动画
 * (`ActionPanel` 里是 `fadeOut(spring(stiffness = Spring.StiffnessMedium))`)。
 * 不等的话窗口会在面板还在淡出的那几百毫秒里缩成 1x1,内容位置会跳。
 */
private const val ACTION_PANEL_FADE_OUT_MS = 400L

@Composable
fun SideGestureContainer(
    onAction: (StoredAction, GestureButton?, GestureButtonActionSettingsOverride?, Offset, GestureDecision.Trigger?) -> Unit,
    buttons: List<GestureButton>,
    modifier: Modifier = Modifier,
    imePadding: Int = 0,
    actionPanelStyle: ActionPanelStyle = ArcStyle(),
    advancedSettings: AdvancedSettings = AdvancedSettings(),
    gestureSettings: GestureSettings = GestureSettings(),
    subGestureSettings: SubGestureSettings = SubGestureSettings(),
    onSubGestureModeChanged: (Boolean, Offset, Int) -> Unit = { _, _, _ -> },
    onActionPanelOverlayChanged: (Boolean) -> Unit = {},
    onActionPanelVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val curOnAction by rememberUpdatedState(newValue = onAction)
    val curOnSubGestureModeChanged by rememberUpdatedState(newValue = onSubGestureModeChanged)
    val curOnPanelVisibilityChanged by rememberUpdatedState(newValue = onActionPanelVisibilityChanged)
    val coroutineScope = rememberCoroutineScope()
    val sideGestureState = rememberGestureState(buttons, advancedSettings, gestureSettings)
    val actionPanelState = rememberActionPanelState()
    var actionPanelSourceOverride by remember { mutableStateOf<GestureButtonActionSettingsOverride?>(null) }
    val subGestureState = remember(subGestureSettings, coroutineScope) {
        SubGestureState(coroutineScope, subGestureSettings, curOnSubGestureModeChanged)
    }
    val volumeScrubState = remember(context) {
        VolumeScrubState(context) { active ->
            curOnSubGestureModeChanged(active, Offset.Unspecified, 0)
        }
    }

    // 收起方向只在这里处理:放大的调用点必须早于 visible=true,见 runGestureActions。
    // LaunchedEffect 以 visible 为 key,面板在这段等待里重新打开会自动取消这次收缩。
    LaunchedEffect(actionPanelState.visible) {
        if (!actionPanelState.visible) {
            delay(ACTION_PANEL_FADE_OUT_MS)
            curOnPanelVisibilityChanged(false)
        }
    }

    fun exitSubGestureOverlay() {
        curOnSubGestureModeChanged(false, Offset.Unspecified, 0)
    }

    fun handleResolvedAction(
        action: StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Offset,
        sourceOverride: GestureButtonActionSettingsOverride? = sourceButton?.actionSettingsOverride,
        decision: GestureDecision.Trigger? = null,
    ): Boolean {
        if (subGestureState.tryEnterSubGesture(action, touchPosition)) return true
        curOnAction(action, sourceButton, sourceOverride, touchPosition, decision)
        return false
    }

    fun runGestureActions(
        actions: List<StoredAction>,
        direction: GestureDirection,
        touchPosition: Offset,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride? = sourceButton?.actionSettingsOverride,
        panelStyle: ActionPanelStyle = actionPanelStyle,
        panelColor: Int = sourceButton?.color ?: android.graphics.Color.TRANSPARENT,
        onPanelStarted: () -> Unit = {},
        needsPanelOverlay: Boolean = false,
        decision: GestureDecision.Trigger? = null,
        onDirectComplete: (enteredSubGesture: Boolean) -> Unit = {},
    ) {
        val meaningfulActions = actions.filter { it.typeId != "internal.none" }
        if (meaningfulActions.size > 1) {
            if (needsPanelOverlay) onActionPanelOverlayChanged(true)
            // 必须在 onDragStart(它把 visible 置 true)之前把主 overlay 撑回全屏,
            // 否则 ActionPanel 的头几帧会按收起时的尺寸定位。
            curOnPanelVisibilityChanged(true)
            actionPanelState.onDragStart(touchPosition)
            actionPanelState.ready(direction, meaningfulActions, panelStyle, panelColor)
            actionPanelSourceOverride = sourceOverride
            onPanelStarted()
            return
        }
        val action = meaningfulActions.firstOrNull() ?: StoredAction.of("internal.none")
        val fromSubGesture = subGestureState.subGestureDepth > 0
        val shouldDelayDirectSubGestureAction = fromSubGesture &&
            action.typeId != "internal.subGesture" &&
            action.typeId != "internal.volumeScrub" &&
            action.typeId != "internal.none"
        if (shouldDelayDirectSubGestureAction) {
            subGestureState.clear(notifyService = true)
            coroutineScope.launch {
                delay(SUB_GESTURE_DIRECT_ACTION_DELAY_MS)
                handleResolvedAction(action, sourceButton, touchPosition, sourceOverride, decision)
            }
            onDirectComplete(false)
            return
        }
        val enteredSubGesture = when (action.typeId) {
            "internal.volumeScrub" -> {
                volumeScrubState.activate(action.volumeScrubConfigOrDefault())
                false
            }
            "internal.none" -> false
            else -> handleResolvedAction(action, sourceButton, touchPosition, sourceOverride, decision)
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
            resolvedActions.subGesture.longSlideActionPanelStyles.styleBy(resolvedActions.direction).value
        } else {
            actionPanelStyle
        }
        val meaningfulCount = resolvedActions.actions.count { it.typeId != "internal.none" }
        val needsOverlay = meaningfulCount > 1 && !resolvedActions.triggerType.isHoldType
        runGestureActions(
            actions = resolvedActions.actions,
            direction = resolvedActions.direction,
            touchPosition = touchPosition,
            sourceButton = sideGestureState.button,
            sourceOverride = sourceOverride,
            decision = resolvedActions.decision,
            panelStyle = panelStyle,
            panelColor = panelColor,
            needsPanelOverlay = needsOverlay,
            onPanelStarted = {
                sideGestureState.cancel()
            },
        ) { enteredSubGesture ->
            sideGestureState.cancel()
            if (!enteredSubGesture && subGestureState.subGestureDepth > 0) {
                subGestureState.clear(notifyService = true)
            }
        }
    }

    fun handleGestureResolvedActions(resolvedActions: GestureResolvedActions) {
        runGestureActions(
            actions = resolvedActions.actions,
            direction = resolvedActions.actionDirection,
            touchPosition = resolvedActions.touchPosition,
            sourceButton = resolvedActions.button,
            decision = resolvedActions.decision,
            panelStyle = if (resolvedActions.triggerType == GestureTriggerType.LongSlide || resolvedActions.triggerType == GestureTriggerType.LongSlideHold) {
                resolvedActions.button.longSlideActionPanelStyles.styleBy(resolvedActions.actionDirection).value
            } else {
                actionPanelStyle
            },
            panelColor = resolvedActions.button.color,
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
            if (volumeScrubState.isActive) {
                volumeScrubState.onDragEnd()
                return@onDragEnd
            }
            if (actionPanelState.visible) {
                val touchPosition = actionPanelState.finger
                val hitAction = actionPanelState.done()
                val sourceOverride = actionPanelSourceOverride
                actionPanelSourceOverride = null
                onActionPanelOverlayChanged(false)
                val fromSubGesturePanel = subGestureState.subGestureDepth > 0
                if (fromSubGesturePanel) {
                    subGestureState.clear(notifyService = true)
                }
                if (hitAction.typeId != "internal.none") {
                    val actionToRun: () -> Unit = {
                        when (hitAction.typeId) {
                            "internal.volumeScrub" -> volumeScrubState.activate(hitAction.volumeScrubConfigOrDefault())
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
                    val meaningfulActions = resolvedActions.actions.filter { it.typeId != "internal.none" }
                    if (meaningfulActions.size > 1 && !resolvedActions.triggerType.isHoldType) {
                        runGestureActions(
                            actions = resolvedActions.actions,
                            direction = resolvedActions.actionDirection,
                            touchPosition = resolvedActions.touchPosition,
                            sourceButton = resolvedActions.button,
                            decision = resolvedActions.decision,
                            panelStyle = if (resolvedActions.triggerType.isLongSlideType) {
                                resolvedActions.button.longSlideActionPanelStyles.styleBy(resolvedActions.actionDirection).value
                            } else {
                                actionPanelStyle
                            },
                            panelColor = resolvedActions.button.color,
                            needsPanelOverlay = true,
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
            if (volumeScrubState.isActive) {
                volumeScrubState.onDragCancel()
                return@onDragCancel
            }
            if (actionPanelState.visible) {
                actionPanelState.onDragCancel()
                onActionPanelOverlayChanged(false)
                actionPanelSourceOverride = null
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
