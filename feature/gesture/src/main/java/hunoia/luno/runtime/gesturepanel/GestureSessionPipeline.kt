package hunoia.luno.runtime.gesturepanel

import androidx.compose.ui.geometry.Offset
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.volumeScrubConfigOrDefault
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.GestureButton
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
import hunoia.luno.gesture.R
import hunoia.luno.gesture.SubGestureEntry
import hunoia.luno.gesture.SubGestureResolvedActions
import hunoia.luno.gesture.SubGestureState
import hunoia.luno.gesture.VolumeScrubState
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.pointer.PointerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val SUB_GESTURE_DIRECT_ACTION_DELAY_MS = 50L

/** 面板收起主 overlay 前的等待：盖住 ActionPanel 的淡出动画，不等窗口会先缩成 1x1 导致内容跳位。 */
const val ACTION_PANEL_FADE_OUT_MS = 400L

/** 一次手势配置快照：由无障碍按钮窗路径与设置页共用同一份。 */
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
 * 唯一投喂方是无障碍按钮窗（`MotionEventInputSource`）。Shizuku 内核触摸流只服务
 * 全屏长按识屏，不进入本管线，也不存在“两条链路各触发一次”的问题。
 *
 * 本类不持有窗口、不执行动作：动作经 [GestureOverlayCallbacks.onAction] 交给 Trigger 侧，
 * 最终仍走 `Dispatcher` 单口。
 */
class GestureSessionPipeline(
    private val scope: CoroutineScope,
    private val callbacks: GestureOverlayCallbacks,
    initial: GesturePipelineSettings = GesturePipelineSettings(),
    /** 键盘遮挡高度：窗口路径一直是 0，保留参数给需要按 insets 校正的输入源。 */
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
        // 长按变体是动作面板：按住到点火就直接重开下一层，不等抬手（手势会话接着用）。
        actionPanelState.onLongPressCommit = { resolved -> commitPanelHold(resolved) }
        actionPanelState.longPressMs = initial.gestureSettings.actionPanelLongPressMs
    }

    /** @return 主手势状态是否被重建（重建后旧会话作废，调用方要复位来源仲裁）。 */
    fun update(next: GesturePipelineSettings): Boolean {
        val previous = settings
        settings = next
        pointer.onSettingsChanged(next.gestureSettings.pointer)
        // 面板长按判定时间是行为参数，随配置即时生效（下次选中就按新值计时）。
        actionPanelState.longPressMs = next.gestureSettings.actionPanelLongPressMs
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

    // ---- 输入入口（无障碍按钮窗单一来源） ----

    /** 面板/子手势等瞬态 UI 在屏：全屏瞬态触摸窗（source=null）的事件流应喂进管线。 */
    val isTransientUiActive: Boolean
        get() = actionPanelState.visible || subGestureState.isActive

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
                // 没出方向就松手：面板路的源按钮在这里就没有下一手了（否则会洩到下一次）。
                actionPanelSourceButton = null
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
            // 重开下一层要沿用当前面板的样式/颜色/扇区中心：这三个字段 done() 会清掉。
            val style = actionPanelState.actionPanelStyle ?: currentSettings.actionPanelStyle
            val colorSpec = actionPanelState.buttonColor
            val centerDegrees = actionPanelState.centerDegrees
            val hitAction = actionPanelState.done()
            val panelSourceButton = actionPanelSourceButton
            actionPanelSourceButton = null
            val fromSubGesturePanel = subGestureState.subGestureDepth > 0
            if (fromSubGesturePanel) {
                subGestureState.clear(notifyService = true)
            }
            // 面板项 = 另一个动作面板：交互窗与主 overlay 都不重建，就在同一触点换内容。
            // 拆窗再重开会在中间帧丢掉触摸窗口（点一下要按两次）。
            val nestedPanel = hitAction.actionPanelId() != null
            if (nestedPanel) {
                if (openActionPanel(hitAction, centerDegrees, touchPosition, style, colorSpec, needsOverlay = false)) {
                    actionPanelSourceButton = panelSourceButton
                    return
                }
                // 目标面板已删/为空：不弹空壳，提示后按无动作收口（下面照常拆窗关面板）。
                showToast(R.string.action_panel_missing)
            }
            callbacks.onActionPanelOverlayChanged(false)
            if (!nestedPanel && hitAction.typeId != Action.NONE_TYPE_ID) {
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
            // 取消（来电/系统抢焦点）：面板路的源按钮一并清，不让它洩到下一次会话。
            actionPanelSourceButton = null
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
        // 按「手指是否还按着」分两档振动：触钮停留/滑动进入时手指还在，触摸窗只是视觉；
        // 抬手/悬浮球点按才让触摸窗真正接管，两者语义不能反。
        when (subGestureState.tryEnterSubGesture(action, touchPosition, holdFire = dragInFlight)) {
            SubGestureEntry.Entered -> return true
            // 禁用 / 引用已删：在配置里都看得见，不能静默落到执行层变空操作。
            SubGestureEntry.Disabled -> {
                showToast(subGestureDisabledToast(action))
                return true
            }
            SubGestureEntry.Missing -> {
                showToast(R.string.sub_gesture_missing_toast)
                return true
            }
            SubGestureEntry.Ignored -> Unit
        }
        callbacks.onAction(action, sourceButton, touchPosition, decision)
        return false
    }

    /** 「子手势「X」已禁用」：名字取当前设置快照，没起名就用「子手势」。 */
    private fun subGestureDisabledToast(action: StoredAction): String {
        val context = hunoia.luno.core.AppContext.get()
        val id = (action.params["id"] as? JsonPrimitive)
            ?.contentOrNull
            .orEmpty()
        val name = settings.subGestureSettings.subGestures
            .firstOrNull { it.id == id }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.sub_gesture)
        return context.getString(R.string.sub_gesture_disabled_toast, name)
    }

    fun runGestureActions(
        resolvedAction: StoredAction?,
        centerDegrees: Float,
        touchPosition: Offset,
        sourceButton: GestureButton?,
        panelStyle: ActionPanelStyle = settings.actionPanelStyle,
        panelColor: ColorSpec = sourceButton?.colorSpec ?: ColorSpec.Unset,
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
            if (openActionPanel(action, centerDegrees, touchPosition, panelStyle, panelColor, needsPanelOverlay)) {
                actionPanelSourceButton = sourceButton
                onPanelStarted()
                return
            }
            // 面板被删或内容为空：不弹空面板、不崩，但也不能静默——
            // 否则从触钮/面板项点过来都只是「什么都没发生」。
            showToast(R.string.action_panel_missing)
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
            centerDegrees = angleDegreesTowardCenter(anchor),
            touchPosition = anchor,
            sourceButton = null,
            panelColor = event.colorSpec,
            needsPanelOverlay = true,
            onPanelStarted = { actionPanelSourceButton = null },
        )
    }

    /**
     * 在 [touchPosition] 展开动作面板（含进场前置：交互窗 / 主 overlay 全屏）。
     * @return false = 面板已被删或内容为空，调用方按无动作收口。
     */
    private fun openActionPanel(
        action: StoredAction,
        centerDegrees: Float,
        touchPosition: Offset,
        style: ActionPanelStyle,
        colorSpec: ColorSpec,
        needsOverlay: Boolean,
    ): Boolean {
        val panelId = action.actionPanelId() ?: return false
        val items = settings.actionPanels.firstOrNull { it.id == panelId }?.items.orEmpty()
        if (items.isEmpty()) return false
        if (needsOverlay) callbacks.onActionPanelOverlayChanged(true)
        // 必须在 onDragStart(它把 visible 置 true)之前把主 overlay 撑回全屏，
        // 否则 ActionPanel 的头几帧会按收起时的尺寸定位。
        callbacks.onActionPanelVisibilityChanged(true)
        actionPanelState.onDragStart(touchPosition)
        actionPanelState.ready(centerDegrees, items, style, colorSpec)
        return true
    }

    /**
     * 面板项的长按变体是动作面板：按住到点火就直接进下一层，不等抬手。
     *
     * 与 onUp 的重开路径同一套：先把当前面板结算掉（等价于抬手），再在同一只手指
     * 的位置重开，手势会话继续用 —— 后面的 MOVE 就能直接选下一层。
     * 失败（面板被删/为空）就按无动作收口，交互窗也必须拆，否则会一直抢触摸。
     */
    private fun openNestedActionPanel(variant: StoredAction) {
        val touchPosition = actionPanelState.finger
        val style = actionPanelState.actionPanelStyle ?: settings.actionPanelStyle
        val colorSpec = actionPanelState.buttonColor
        val centerDegrees = actionPanelState.centerDegrees
        actionPanelState.done()
        if (openActionPanel(variant, centerDegrees, touchPosition, style, colorSpec, needsOverlay = false)) {
            return
        }
        actionPanelSourceButton = null
        showToast(R.string.action_panel_missing)
        callbacks.onActionPanelOverlayChanged(false)
    }

    /**
     * 长按点火（不抬手）结算：面板引用 → 在同一触点重开下一层；子手势 → 直接进会话。
     *
     * 子手势这条路把面板视觉收掉但**不拆交互窗**：它是这次手势的触摸源，拆了就把
     * 后续 MOVE/UP 丢给底层应用。收口交给子手势会话结束（协调器 onSubGestureModeChanged(false)）。
     */
    private fun commitPanelHold(resolved: StoredAction) {
        if (resolved.actionPanelId() != null) {
            openNestedActionPanel(resolved)
            return
        }
        val touchPosition = actionPanelState.finger
        actionPanelState.done()
        val entry = subGestureState.tryEnterSubGesture(resolved, touchPosition, holdFire = true)
        when (entry) {
            SubGestureEntry.Entered -> Unit
            SubGestureEntry.Disabled -> {
                showToast(subGestureDisabledToast(resolved))
                closePanelAfterFailedHold()
            }
            SubGestureEntry.Missing -> {
                showToast(R.string.sub_gesture_missing_toast)
                closePanelAfterFailedHold()
            }
            SubGestureEntry.Ignored -> closePanelAfterFailedHold()
        }
    }

    /** 长按点火失败（禁用/已删/进不去）：面板已经 done，这里把交互窗一并收口。 */
    private fun closePanelAfterFailedHold() {
        actionPanelSourceButton = null
        callbacks.onActionPanelOverlayChanged(false)
    }

    fun handleSubGestureResolvedActions(resolvedActions: SubGestureResolvedActions) {
        // 面板项长按直接进子手势时，面板的源按钮已不在 sideGestureState 里了，接着用捕获值。
        val panelSource = actionPanelSourceButton
        actionPanelSourceButton = null
        val sourceButton = sideGestureState.button ?: panelSource
        val panelColor = resolvedActions.subGesture.colorSpec
            .takeUnless { it.isUnset }
            ?: sourceButton?.colorSpec
            ?: ColorSpec.Unset
        val touchPosition = resolvedActions.touchPosition.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: sideGestureState.finger.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: Offset.Zero
        // 需要把主 overlay 撑全屏的唯一场景就是弹面板，且判定在 runGestureActions 内部
        // （按动作类型），这里恒 false。
        runGestureActions(
            resolvedAction = resolvedActions.action,
            centerDegrees = resolvedActions.subGesture.angle.slotCenterDegrees(resolvedActions.direction),
            touchPosition = touchPosition,
            sourceButton = sourceButton,
            decision = resolvedActions.decision,
            panelColor = panelColor,
            needsPanelOverlay = true,
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
            centerDegrees = resolvedActions.button.angle.slotCenterDegrees(resolvedActions.actionDirection),
            touchPosition = resolvedActions.touchPosition,
            sourceButton = resolvedActions.button,
            decision = resolvedActions.decision,
            panelColor = resolvedActions.button.colorSpec,
            // 挂全屏触摸窗：滑动类触发在抬手后才弹面板，手指已不在触钮窗里 ——
            // 后续点选只能靠全屏窗接（不挂的话点哪都落到下面的应用）。
            needsPanelOverlay = true,
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

/** 外部锚点（悬浮球球心）展开面板的朝向：取「锚点 → 屏幕中心」连线的角度（度）。 */
internal fun angleDegreesTowardCenter(anchor: Offset): Float {
    val widthPx = hunoia.luno.bridge.ui.DensityProvider.screenWidthPx
    val heightPx = hunoia.luno.bridge.ui.DensityProvider.screenHeightPx
    val dx = (widthPx / 2f - anchor.x).toDouble()
    val dy = (anchor.y - heightPx / 2f).toDouble()
    var degrees = Math.toDegrees(kotlin.math.atan2(dy, dx)).toFloat()
    if (degrees < 0f) degrees += 360f
    return degrees
}
