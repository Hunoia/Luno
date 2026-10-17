package hunoia.luno.runtime.gesturepanel

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
import hunoia.luno.action.model.actionPanelId
import hunoia.luno.action.model.volumeScrubConfigOrDefault
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.isPlainSlideType
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.gesture.GestureDecision
import hunoia.luno.gesture.GestureResolvedActions
import hunoia.luno.runtime.gesturepanel.ActionPanel
import hunoia.luno.runtime.gesturepanel.rememberActionPanelState
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
    onAction: (StoredAction, GestureButton?, Offset, GestureDecision.Trigger?) -> Unit,
    buttons: List<GestureButton>,
    modifier: Modifier = Modifier,
    imePadding: Int = 0,
    actionPanelStyle: ActionPanelStyle = ArcStyle(),
    advancedSettings: AdvancedSettings = AdvancedSettings(),
    gestureSettings: GestureSettings = GestureSettings(),
    subGestureSettings: SubGestureSettings = SubGestureSettings(),
    /** 全局动作面板列表：把 `internal.actionPanel` 展开成面板项。 */
    actionPanels: List<ActionPanel> = emptyList(),
    onSubGestureModeChanged: (Boolean, Offset, Int) -> Unit = { _, _, _ -> },
    onActionPanelOverlayChanged: (Boolean) -> Unit = {},
    onActionPanelVisibilityChanged: (Boolean) -> Unit = {},
    onVolumeScrubOverlayRequested: (VolumeScrubConfig) -> Unit = {},
) {
    val context = LocalContext.current
    val curOnAction by rememberUpdatedState(newValue = onAction)
    val curOnSubGestureModeChanged by rememberUpdatedState(newValue = onSubGestureModeChanged)
    val curOnPanelVisibilityChanged by rememberUpdatedState(newValue = onActionPanelVisibilityChanged)
    val curOnVolumeScrubOverlayRequested by rememberUpdatedState(newValue = onVolumeScrubOverlayRequested)
    val coroutineScope = rememberCoroutineScope()
    val sideGestureState = rememberGestureState(buttons, advancedSettings, gestureSettings)
    val actionPanelState = rememberActionPanelState()

    // 面板展开后主手势会话会被 reset() 清空 button，选中时必须用捕获值，
    // 否则按触钮 id 限定的 gesturePerformed 自动化规则永远不中、面板内 hideGestureButton 空跑。
    var actionPanelSourceButton by remember { mutableStateOf<GestureButton?>(null) }
    val subGestureState = remember(subGestureSettings, coroutineScope) {
        SubGestureState(coroutineScope, subGestureSettings, curOnSubGestureModeChanged)
    }
    val volumeScrubState = remember(context) { VolumeScrubState(context) }

    /**
     * 当前是否处在一次拖动过程里（DOWN 已来、UP 还没来）。
     * volumeScrubState 的清理时机就是 UP，所以在 onDragEnd 里激活的状态没有清理点，
     * 会残留下来吞掉下一次触钮手势（滑动只调音量、不触发动作）。凡是此时激活的，
     * 一律改由全屏音量窗接管整段刮擦。
     */
    var dragInFlight by remember { mutableStateOf(false) }

    // 收起方向只在这里处理:放大的调用点必须早于 visible=true,见 runGestureActions。
    // LaunchedEffect 以 visible 为 key,面板在这段等待里重新打开会自动取消这次收缩。
    LaunchedEffect(actionPanelState.visible) {
        if (!actionPanelState.visible) {
            delay(ACTION_PANEL_FADE_OUT_MS)
            curOnPanelVisibilityChanged(false)
        }
    }

    fun handleResolvedAction(
        action: StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Offset,
        decision: GestureDecision.Trigger? = null,
    ): Boolean {
        if (subGestureState.tryEnterSubGesture(action, touchPosition)) return true
        curOnAction(action, sourceButton, touchPosition, decision)
        return false
    }

    fun runGestureActions(
        resolvedAction: StoredAction?,
        direction: GestureDirection,
        touchPosition: Offset,
        sourceButton: GestureButton?,
        panelStyle: ActionPanelStyle = actionPanelStyle,
        panelColor: Int = sourceButton?.color ?: android.graphics.Color.TRANSPARENT,
        onPanelStarted: () -> Unit = {},
        needsPanelOverlay: Boolean = false,
        decision: GestureDecision.Trigger? = null,
        onDirectComplete: (enteredSubGesture: Boolean) -> Unit = {},
    ) {
        val action = resolvedAction?.takeIf { it.typeId != "internal.none" }
            ?: StoredAction.of("internal.none")
        // 动作面板：显式动作类型，不再是「槽里存了 ≥2 个动作」的副作用。
        // 保持类触发（按住到阈值）也在 onMove 里 resolve，onResolved 统一接住，
        // 所以这里不需要区分按住/非按住 —— 阈值到了就弹。
        val panelId = action.actionPanelId()
        if (panelId != null) {
            val items = actionPanels.firstOrNull { it.id == panelId }?.items.orEmpty()
            if (items.isNotEmpty()) {
                if (needsPanelOverlay) onActionPanelOverlayChanged(true)
                // 必须在 onDragStart(它把 visible 置 true)之前把主 overlay 撑回全屏,
                // 否则 ActionPanel 的头几帧会按收起时的尺寸定位。
                curOnPanelVisibilityChanged(true)
                actionPanelState.onDragStart(touchPosition)
                actionPanelState.ready(direction, items, panelStyle, panelColor)
                actionPanelSourceButton = sourceButton
                onPanelStarted()
                return
            }
            // 面板被删或内容为空：按无动作处理，不弹空面板、不崩。
        }
        val fromSubGesture = subGestureState.subGestureDepth > 0
        val shouldDelayDirectSubGestureAction = fromSubGesture &&
            action.typeId != "internal.subGesture" &&
            action.typeId != "internal.volumeScrub" &&
            action.typeId != "internal.none"
        if (shouldDelayDirectSubGestureAction) {
            subGestureState.clear(notifyService = true)
            coroutineScope.launch {
                delay(SUB_GESTURE_DIRECT_ACTION_DELAY_MS)
                handleResolvedAction(action, sourceButton, touchPosition, decision)
            }
            onDirectComplete(false)
            return
        }
        val enteredSubGesture = when (action.typeId) {
            "internal.volumeScrub" -> {
                val config = action.volumeScrubConfigOrDefault()
                if (dragInFlight) {
                    // 滑动途中确认：Compose 状态服务这次手势的剩余部分，UP 时会清。
                    volumeScrubState.activate(config)
                    // 短滑/长滑：这次拖动调完松手后，任意位置再拖一次仍能调节。
                    if (decision?.triggerType?.isPlainSlideType == true) curOnVolumeScrubOverlayRequested(config)
                } else {
                    // 这次激活已经不会有 UP 来清状态（例如面板点选、抬手才判定的滑动），
                    // 交给全屏音量窗接管整段刮擦。
                    curOnVolumeScrubOverlayRequested(config)
                }
                false
            }
            "internal.none" -> false
            else -> handleResolvedAction(action, sourceButton, touchPosition, decision)
        }
        onDirectComplete(enteredSubGesture)
    }

    fun handleSubGestureResolvedActions(resolvedActions: hunoia.luno.gesture.SubGestureResolvedActions) {
        val panelColor = resolvedActions.subGesture.color.takeUnless { it == android.graphics.Color.TRANSPARENT }
            ?: sideGestureState.button?.color
            ?: android.graphics.Color.TRANSPARENT
        val touchPosition = resolvedActions.touchPosition.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: sideGestureState.finger.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: Offset.Zero
        // 需要把主 overlay 撑全屏的唯一场景就是弹面板，且判定在 runGestureActions 内部
        // （按动作类型），这里恒 false。
        runGestureActions(
            resolvedAction = resolvedActions.action,
            direction = resolvedActions.direction,
            touchPosition = touchPosition,
            sourceButton = sideGestureState.button,
            decision = resolvedActions.decision,
            panelStyle = actionPanelStyle,
            panelColor = panelColor,
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
            resolvedAction = resolvedActions.action,
            direction = resolvedActions.actionDirection,
            touchPosition = resolvedActions.touchPosition,
            sourceButton = resolvedActions.button,
            decision = resolvedActions.decision,
            panelStyle = actionPanelStyle,
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
        onDragStart = onDragStart@{ offset, hint ->
            dragInFlight = true
            if (actionPanelState.visible) {
                actionPanelState.onSelectStart(offset)
                return@onDragStart
            }
            if (subGestureState.isActive) {
                subGestureState.onDragStart(offset)
                return@onDragStart
            }
            // 必须早退：否则这里的 DOWN 会给主手势起长按 job，而后续 MOVE 全被音量分支
            // 吞掉（isLongPressCanceled 永不置位），抬手时 onDragEnd 又先返回不 cancel，
            // 于是 job 在 250ms 后凭空触发默认长按动作。
            if (volumeScrubState.isActive) return@onDragStart
            sideGestureState.onDragStart(offset, imePadding, hint)
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
            dragInFlight = false
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
                val panelSourceButton = actionPanelSourceButton
                actionPanelSourceButton = null
                onActionPanelOverlayChanged(false)
                val fromSubGesturePanel = subGestureState.subGestureDepth > 0
                if (fromSubGesturePanel) {
                    subGestureState.clear(notifyService = true)
                }
                if (hitAction.typeId != "internal.none") {
                    val actionToRun: () -> Unit = {
                        when (hitAction.typeId) {
                            // 面板点选同样发生在 onDragEnd 内，没有后续 UP 可清理状态，
                            // 一律交给全屏音量窗（它自己拖一次就关）。
                            "internal.volumeScrub" ->
                                curOnVolumeScrubOverlayRequested(hitAction.volumeScrubConfigOrDefault())
                            else -> handleResolvedAction(hitAction, panelSourceButton, touchPosition)
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
                // 单一路径：面板判定在 runGestureActions 内（按动作类型），
                // 抬手时不再有「长度 > 1」的特殊分支。
                sideGestureState.onDragEnd()?.let { handleGestureResolvedActions(it) }
            }
        },
        onDragCancel = onDragCancel@{
            dragInFlight = false
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
                actionPanelSourceButton = null
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
