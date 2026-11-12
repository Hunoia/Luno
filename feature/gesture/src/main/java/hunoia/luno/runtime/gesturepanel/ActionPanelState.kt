package hunoia.luno.runtime.gesturepanel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import hunoia.luno.action.model.TriggerType
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.ColorSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hunoia.luno.action.model.Action
import hunoia.luno.config.defaults.GestureSettingsDefaults

class ActionPanelState(private val coroutineScope: CoroutineScope) : LongSlideState() {

    var visible: Boolean by mutableStateOf(false)
        private set
    var actions: List<StoredAction> by mutableStateOf(emptyList())
        private set
    /** 面板扇区中心角（度）：由触钮/子手势的方向布局算出来，不再用名义方向枚举。 */
    var centerDegrees: Float by mutableStateOf(0f)
        private set
    var actionPanelStyle: ActionPanelStyle? by mutableStateOf(null)
        private set
    var buttonColor: ColorSpec by mutableStateOf(ColorSpec.Unset)
        private set
    var parentSize: Size by mutableStateOf(Size.Zero)
        private set
    var itemSizePx: Float by mutableStateOf(0f)
        private set
    var selectedIndex: Int by mutableStateOf(-1)
        private set
    private var selectedBaseAction: StoredAction by mutableStateOf(StoredAction.of(Action.NONE_TYPE_ID))
    val selectedAction: StoredAction
        get() = when (triggerType) {
            TriggerType.Press -> selectedBaseAction.withoutVariantKey()
            TriggerType.LongPress -> selectedBaseAction.longPressVariant()?.withoutVariantKey()
                ?: selectedBaseAction.withoutVariantKey()
        }
    var triggerType: TriggerType by mutableStateOf(TriggerType.Press)
        private set
    private var delayTriggerTypeChangedJob: Job? = null

    /**
     * 长按结算出「导航类」动作（面板引用 / 子手势）时的提前触发：两者都是一次性导航，
     * 点火即确定，不用等抬手；普通动作仍等抬手结算（抬手前还可能移开手指）。由管线注册。
     */
    var onLongPressCommit: ((StoredAction) -> Unit)? = null

    /** 进入长按变体的等待时长（ms），由管线按 [GestureSettings][hunoia.luno.config.model.GestureSettings] 同步。 */
    var longPressMs: Long = GestureSettingsDefaults.ActionPanelLongPressMs

    override fun onDragStart(offset: Offset) {
        super.onDragStart(offset)
        // 新会话：上一次退场留下的选中项 / 长按态不延续（几何已被 super 覆盖）。
        selectedIndex = -1
        selectedBaseAction = StoredAction.of(Action.NONE_TYPE_ID)
        delayTriggerTypeChangedJob?.cancel()
        triggerType = TriggerType.Press
        visible = true
    }

    fun ready(centerDegrees: Float, actions: List<StoredAction>, actionPanelStyle: ActionPanelStyle, buttonColor: ColorSpec) {
        this.centerDegrees = centerDegrees
        this.actions = actions
        this.actionPanelStyle = actionPanelStyle
        this.buttonColor = buttonColor
    }

    fun setLayoutInfo(parentSize: Size, itemSizePx: Float) {
        this.parentSize = parentSize
        this.itemSizePx = itemSizePx
    }

    fun cancel() {
        release()
    }

    fun onSelectStart(offset: Offset) {
        finger = offset
    }

    fun done(): StoredAction {
        val action = selectedAction
        // 只收起，不清几何：退场动画还要用 origin / style / actions / 选中项画环、光晕和标签，
        // 同帧清掉的话第一帧就只剩一个空容器在退（真机表现「动作面板没有关闭动画」）。
        // 真正清理在退场播完后由 [release] 做。
        reset()
        return action
    }

    fun isSelected(action: StoredAction): Boolean {
        return selectedBaseAction == action
    }

    fun select(index: Int, action: StoredAction) {
        selectedIndex = if (action.typeId == Action.NONE_TYPE_ID) -1 else index
        selectedBaseAction = action

        delayTriggerTypeChangedJob?.cancel()
        triggerType = TriggerType.Press
        delayTriggerTypeChangedJob = coroutineScope.launch {
            delay(
                longPressMs.coerceIn(
                    GestureSettingsDefaults.MinActionPanelLongPressMs,
                    GestureSettingsDefaults.MaxActionPanelLongPressMs,
                ),
            )
            triggerType = TriggerType.LongPress
            // 长按结算出来的是导航类动作（面板引用 / 子手势）时直接触发：
            // 点火即确定，再等抬手只是白等一段，而且按住就能继续往下一层走。
            val resolved = selectedAction
            if (resolved.actionPanelId() != null || resolved.typeId == "internal.subGesture") {
                onLongPressCommit?.invoke(resolved)
            }
        }
    }

    /**
     * 会话结束 = 只收起（[visible] 置 false）。**保留** origin / finger / style / actions /
     * 选中项：退场动画（`ActionPanelMenu` 的 `panelAppear` + 环形项 + 光晕）全部从这些字段
     * 算出来，必须活到退场播完。丢掉内容的动作在 [release]。
     */
    override fun reset() {
        visible = false
        delayTriggerTypeChangedJob?.cancel()
    }

    /**
     * 退场真正播完后丢弃会话内容（`GestureSessionPipeline.notifyActionPanelHidden` 调用），
     * 或强制收口时直接调。语义上与基类 `reset()` 一致：会话结束，几何置空，等下次 [ready]。
     * 顺手补上原来漏清的 [actions]——否则面板删空后下次开会先画一帧旧条目。
     */
    fun release() {
        reset()
        actions = emptyList()
        actionPanelStyle = null
        buttonColor = ColorSpec.Unset
        selectedIndex = -1
        selectedBaseAction = StoredAction.of(Action.NONE_TYPE_ID)
        triggerType = TriggerType.Press
        origin = Offset.Unspecified
        finger = Offset.Unspecified
    }

    /**
     * 用于实现短按和长按
     */
}
