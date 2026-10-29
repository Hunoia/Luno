package hunoia.luno.runtime.gesturepanel

import androidx.compose.ui.geometry.Offset
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.volumeScrubConfigOrDefault
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.isPlainSlideType
import hunoia.luno.core.BallTapActionEvent
import hunoia.luno.core.Events
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.gesture.GestureDecision
import hunoia.luno.gesture.GestureResolvedActions
import hunoia.luno.gesture.GestureState
import hunoia.luno.gesture.SubGestureResolvedActions
import hunoia.luno.gesture.SubGestureState
import hunoia.luno.gesture.VolumeScrubState
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.pointer.PointerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SUB_GESTURE_DIRECT_ACTION_DELAY_MS = 50L

/** 面板收起主 overlay 前的等待：盖住 ActionPanel 的淡出动画，不等窗口会先缩成 1x1 导致内容跳位。 */
const val ACTION_PANEL_FADE_OUT_MS = 400L

/** 一次手势配置快照：输入源无关，窗口与内核触摸流共用同一份。 */
data class GesturePipelineSettings(
    val buttons: List<GestureButton> = emptyList(),
    val actionPanels: List<ActionPanel> = emptyList(),
    val advancedSettings: AdvancedSettings = AdvancedSettings(),
    val gestureSettings: GestureSettings = GestureSettings(),
    val subGestureSettings: SubGestureSettings = SubGestureSettings(),
    val actionPanelStyle: ActionPanelStyle = ArcStyle(),
)

/**
 * 手势会话管线：DOWN/MOVE/UP 进来，动作意图出去。
 *
 * 这里承载原先散在 Compose 容器里的会话状态机（主手势、子手势、动作面板、音量刮擦、指针），
 * 只是把「谁来投喂」抽走了：
 * - 无障碍按钮窗（`MotionEventInputSource`）；
 * - 内核触摸流（`ShizukuTouchInputSource`）。
 *
 * 两者共用同一个实例，所以**不会**出现两份识别器各自触发一次的问题；
 * 谁是合法来源由 [hunoia.luno.gesture.touchsource.TouchInputRouter] 按区域判定。
 *
 * 本类不持有窗口、不执行动作：动作经 [GestureOverlayCallbacks.onAction] 交给 Trigger 侧，
 * 最终仍走 `Dispatcher` 单口。
 */
class GestureSessionPipeline(
    private val scope: CoroutineScope,
    private val callbacks: GestureOverlayCallbacks,
    initial: GesturePipelineSettings = GesturePipelineSettings(),
    /** 键盘遮挡高度：无障碍窗口路径一直是 0，内核触摸流自己按当前 insets 传。 */
    private val imePaddingProvider: () -> Int = { 0 },
) {

    private var settings: GesturePipelineSettings = initial

    val currentSettings: GesturePipelineSettings get() = settings

    var sideGestureState: GestureState = createGestureState(initial)
        private set

    var subGestureState: SubGestureState = createSubGestureState(initial)
        private set

    val actionPanelState: ActionPanelState = ActionPanelState(scope)
    private val volumeScrubState = VolumeScrubState(hunoia.luno.core.AppContext.get())
    val pointer: PointerSession = PointerSession(
        initialSettings = initial.gestureSettings.pointer,
        onStart = { s -> callbacks.onPointerStart(s) },
        onEnd = { callbacks.onPointerEnd() },
        onActionAt = { x, y -> callbacks.onPointerActionAtPosition(x, y) },
    )

    /** 面板展开后主手势会话会被 reset() 清空 button，选中时必须用捕获值。 */
    private var actionPanelSourceButton: GestureButton? = null

    /** 指针从按住类触发启动时为 true：手势会话收尾要等指针松手/取消。 */
    private var pointerStartedFromSubGesture = false

    /** 是否处在一次拖动中（DOWN 已来、UP 未至）。 */
    var dragInFlight: Boolean = false
        private set

    private val ballTapSubscriber: (BallTapActionEvent) -> Unit = { event -> runBallTapActions(event) }

    init {
        Events.subscribe(BallTapActionEvent::class, ballTapSubscriber)
    }

    /** @return 主手势状态是否被重建（重建后旧会话作废，调用方要复位来源仲裁）。 */
    fun update(next: GesturePipelineSettings): Boolean {
        val previous = settings
        settings = next
        pointer.onSettingsChanged(next.gestureSettings.pointer)
        var recreated = false
        if (previous.buttons != next.buttons ||
            previous.advancedSettings != next.advancedSettings ||
            previous.gestureSettings != next.gestureSettings
        ) {
            sideGestureState = createGestureState(next)
            recreated = true
        }
        if (previous.subGestureSettings != next.subGestureSettings) {
            subGestureState = createSubGestureState(next)
        }
        return recreated
    }

    fun release() {
        Events.unsubscribe(BallTapActionEvent::class, ballTapSubscriber)
    }

    /** 面板淡出结束（容器侧 LaunchedEffect 计完 400ms）：把“已退场”播出去。 */
    fun notifyActionPanelHidden() {
        callbacks.onActionPanelVisibilityChanged(false)
    }

    // ---- 输入入口（两个输入源共用） ----

    fun onDown(offset: Offset, hint: hunoia.luno.gesture.TouchWindowHint? = null) {
        dragInFlight = true
        if (actionPanelState.visible) {
            actionPanelState.onSelectStart(offset)
            return
        }
        if (subGestureState.isActive) {
            subGestureState.onDragStart(offset)
            return
        }
        // 必须早退：否则这里的 DOWN 会给主手势起长按 job，而后续 MOVE 全被音量分支
        // 吞掉（isLongPressCanceled 永不置位），抬手时 onUp 又先返回不 cancel，
        // 于是 job 在 250ms 后凭空触发默认长按动作。
        if (volumeScrubState.isActive) return
        sideGestureState.onDragStart(offset, imePaddingProvider(), hint)
    }

    fun onMove(dragAmount: Offset) {
        if (subGestureState.isActive) {
            val resolvedActions = subGestureState.onDrag(dragAmount)
            if (resolvedActions != null) {
                handleSubGestureResolvedActions(resolvedActions)
            }
            if (resolvedActions == null && !subGestureState.isActive) {
                sideGestureState.cancel()
                subGestureState.clear()
            }
            return
        }
        if (pointer.isActive) {
            // 指针会话吞掉整段拖动：光标映射，不参与手势识别。
            pointer.onDrag(dragAmount)
            return
        }
        if (volumeScrubState.isActive) {
            volumeScrubState.onDrag(dragAmount)
            return
        }
        if (actionPanelState.visible) {
            actionPanelState.onDrag(dragAmount)
            return
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
    }

    fun onUp() {
        dragInFlight = false
        if (subGestureState.isActive) {
            val resolvedActions = subGestureState.onDragEnd()
            if (resolvedActions != null) {
                handleSubGestureResolvedActions(resolvedActions)
            } else {
                sideGestureState.cancel()
                subGestureState.clear()
            }
            return
        }
        if (pointer.isActive) {
            val fromSubGesture = pointerStartedFromSubGesture
            pointerStartedFromSubGesture = false
            // 松手即点击：光标落点经桥回调，runtime 延迟 80ms 派发无障碍点击。
            pointer.onDragEnd()
            if (fromSubGesture) subGestureState.clear(notifyService = true)
            return
        }
        if (volumeScrubState.isActive) {
            volumeScrubState.onDragEnd()
            return
        }
        if (actionPanelState.visible) {
            val touchPosition = actionPanelState.finger
            val hitAction = actionPanelState.done()
            val panelSourceButton = actionPanelSourceButton
            actionPanelSourceButton = null
            callbacks.onActionPanelOverlayChanged(false)
            val fromSubGesturePanel = subGestureState.subGestureDepth > 0
            if (fromSubGesturePanel) {
                subGestureState.clear(notifyService = true)
            }
            if (hitAction.typeId != Action.NONE_TYPE_ID) {
                val actionToRun: () -> Unit = {
                    when (hitAction.typeId) {
                        // 面板点选同样发生在 onUp 内，没有后续 UP 可清理状态，
                        // 一律交给全屏音量窗（它自己拖一次就关）。
                        "internal.volumeScrub" ->
                            callbacks.onVolumeScrubOverlayRequested(hitAction.volumeScrubConfigOrDefault())
                        // 面板点选指针同理：全屏指针窗接管（拖一次、松手点击、自动关）。
                        "internal.pointer" ->
                            callbacks.onPointerShow(settings.gestureSettings.pointer)
                        else -> handleResolvedAction(hitAction, panelSourceButton, touchPosition)
                    }
                }
                if (fromSubGesturePanel) {
                    scope.launch {
                        delay(SUB_GESTURE_DIRECT_ACTION_DELAY_MS)
                        actionToRun()
                    }
                } else {
                    actionToRun()
                }
            }
            return
        }
        if (!sideGestureState.isCanceled) {
            // 单一路径：面板判定在 runGestureActions 内（按动作类型），
            // 抬手时不再有「长度 > 1」的特殊分支。
            sideGestureState.onDragEnd()?.let { handleGestureResolvedActions(it) }
        }
    }

    fun onCancel() {
        dragInFlight = false
        if (subGestureState.isActive) {
            subGestureState.onDragCancel()
            return
        }
        if (pointer.isActive) {
            // 从子手势会话里启动的指针：系统级取消（如来电弹窗）时保持会话，
            // 等指针真正的收尾；普通路径就地取消并退出子手势模式。
            if (pointerStartedFromSubGesture) return
            pointerStartedFromSubGesture = false
            pointer.onDragCancel()
            return
        }
        if (volumeScrubState.isActive) {
            volumeScrubState.onDragCancel()
            return
        }
        if (actionPanelState.visible) {
            actionPanelState.onDragCancel()
            callbacks.onActionPanelOverlayChanged(false)
            actionPanelSourceButton = null
            if (subGestureState.subGestureDepth > 0) subGestureState.clear(notifyService = true)
        }
        sideGestureState.onDragCancel()
    }

    // ---- 动作解析 ----

    fun handleResolvedAction(
        action: StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Offset,
        decision: GestureDecision.Trigger? = null,
    ): Boolean {
        if (subGestureState.tryEnterSubGesture(action, touchPosition)) return true
        callbacks.onAction(action, sourceButton, touchPosition, decision)
        return false
    }

    fun runGestureActions(
        resolvedAction: StoredAction?,
        direction: GestureDirection,
        touchPosition: Offset,
        sourceButton: GestureButton?,
        panelStyle: ActionPanelStyle = settings.actionPanelStyle,
        panelColor: Int = sourceButton?.color ?: android.graphics.Color.TRANSPARENT,
        onPanelStarted: () -> Unit = {},
        needsPanelOverlay: Boolean = false,
        decision: GestureDecision.Trigger? = null,
        onDirectComplete: (enteredSubGesture: Boolean) -> Unit = {},
    ) {
        val action = resolvedAction?.takeIf { it.typeId != Action.NONE_TYPE_ID }
            ?: StoredAction.of(Action.NONE_TYPE_ID)
        // 动作面板：显式动作类型，不再是「槽里存了 ≥2 个动作」的副作用。
        // 保持类触发（按住到阈值）也在 onMove 里 resolve，onResolved 统一接住，
        // 所以这里不需要区分按住/非按住 —— 阈值到了就弹。
        val panelId = action.actionPanelId()
        if (panelId != null) {
            val items = settings.actionPanels.firstOrNull { it.id == panelId }?.items.orEmpty()
            if (items.isNotEmpty()) {
                if (needsPanelOverlay) callbacks.onActionPanelOverlayChanged(true)
                // 必须在 onDragStart(它把 visible 置 true)之前把主 overlay 撑回全屏,
                // 否则 ActionPanel 的头几帧会按收起时的尺寸定位。
                callbacks.onActionPanelVisibilityChanged(true)
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
            action.typeId != "internal.pointer" &&
            action.typeId != Action.NONE_TYPE_ID
        if (shouldDelayDirectSubGestureAction) {
            subGestureState.clear(notifyService = true)
            scope.launch {
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
                    // 滑动途中确认：状态服务这次手势的剩余部分，UP 时会清。
                    volumeScrubState.activate(config)
                    // 短滑/长滑：这次拖动调完松手后，任意位置再拖一次仍能调节。
                    if (decision?.triggerType?.isPlainSlideType == true) {
                        callbacks.onVolumeScrubOverlayRequested(config)
                    }
                } else {
                    // 这次激活已经不会有 UP 来清状态（例如面板点选、抬手才判定的滑动），
                    // 交给全屏音量窗接管整段刮擦。
                    callbacks.onVolumeScrubOverlayRequested(config)
                }
                false
            }
            "internal.pointer" -> {
                // 与 volumeScrub 同构：按住类触发的拖动途中（decision 非空且 dragInFlight）走面板内光标接管，
                // 其余入口走全屏指针窗口；decision 判空排除悬浮球点按与手势拖动并发的竞态。
                val holdInFlight = dragInFlight && decision != null
                val started = if (holdInFlight) {
                    pointer.start(settings.gestureSettings.pointer, touchPosition)
                } else {
                    callbacks.onPointerShow(settings.gestureSettings.pointer)
                }
                pointerStartedFromSubGesture = started && holdInFlight
                false
            }
            Action.NONE_TYPE_ID -> false
            else -> handleResolvedAction(action, sourceButton, touchPosition, decision)
        }
        onDirectComplete(enteredSubGesture)
    }

    /**
     * 悬浮球点按动作统一走事件总线进来（见 BallTapActionEvent），全部交给 runGestureActions
     * 分类执行，与触钮同链路。
     */
    fun runBallTapActions(event: BallTapActionEvent) {
        val anchor = Offset(event.anchorX.toFloat(), event.anchorY.toFloat())
        runGestureActions(
            resolvedAction = event.action,
            direction = directionFromAnchor(anchor),
            touchPosition = anchor,
            sourceButton = null,
            panelColor = event.color,
            needsPanelOverlay = true,
            onPanelStarted = { actionPanelSourceButton = null },
        )
    }

    fun handleSubGestureResolvedActions(resolvedActions: SubGestureResolvedActions) {
        val panelColor = resolvedActions.subGesture.color
            .takeUnless { it == android.graphics.Color.TRANSPARENT }
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
            panelColor = panelColor,
            onPanelStarted = { sideGestureState.cancel() },
        ) { enteredSubGesture ->
            sideGestureState.cancel()
            if (!enteredSubGesture && subGestureState.subGestureDepth > 0) {
                // 指针从子手势会话里启动时，服务侧的子手势模式要等指针收尾再退
                //（见 onUp 的 pointer 分支），这里只清本地状态。
                subGestureState.clear(notifyService = !pointerStartedFromSubGesture)
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

    private fun createGestureState(state: GesturePipelineSettings): GestureState {
        return GestureState(
            coroutineScope = scope,
            buttons = state.buttons,
            advancedSettings = state.advancedSettings,
            gestureSettings = state.gestureSettings,
        ).also { gestureState ->
            gestureState.onResolved = { resolved -> handleGestureResolvedActions(resolved) }
        }
    }

    private fun createSubGestureState(state: GesturePipelineSettings): SubGestureState {
        return SubGestureState(
            scope,
            state.subGestureSettings,
            callbacks::onSubGestureModeChanged,
        )
    }
}

/** 外部锚点（悬浮球球心）展开面板的方向：取「锚点 → 屏幕中心」连线决定往屏幕内侧铺。 */
internal fun directionFromAnchor(anchor: Offset): GestureDirection {
    val widthPx = hunoia.luno.bridge.ui.DensityProvider.screenWidthPx
    val heightPx = hunoia.luno.bridge.ui.DensityProvider.screenHeightPx
    return when {
        anchor.y < heightPx / 3f -> GestureDirection.Down
        anchor.y > heightPx * 2f / 3f -> GestureDirection.Up
        anchor.x < widthPx / 2f -> GestureDirection.Right
        else -> GestureDirection.Left
    }
}
