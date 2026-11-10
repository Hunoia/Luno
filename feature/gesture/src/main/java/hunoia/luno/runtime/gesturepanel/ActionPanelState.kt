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
     * 长按结算出的是动作面板时的提前触发：面板导航不用等抬手，按住到点火就直接进下一层；
     * 普通动作仍等抬手结算（抬手前还可能移开手指）。由管线注册。
     */
    var onLongPressPanel: ((StoredAction) -> Unit)? = null

    /** 进入长按变体的等待时长（ms），由管线按 [GestureSettings][hunoia.luno.config.model.GestureSettings] 同步。 */
    var longPressMs: Long = GestureSettingsDefaults.ActionPanelLongPressMs

    override fun onDragStart(offset: Offset) {
        super.onDragStart(offset)
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
        reset()
    }

    fun onSelectStart(offset: Offset) {
        finger = offset
    }

    fun done(): StoredAction {
        val action = selectedAction
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
            // 长按结算出来的是动作面板引用（变体或没有变体的本体）时直接触发：
            // 点火即确定，再等抬手只是白等一段，而且按住就能往下一层选。
            val resolved = selectedAction
            if (resolved.actionPanelId() != null) onLongPressPanel?.invoke(resolved)
        }
    }

    override fun reset() {
        visible = false
        actionPanelStyle = null
        buttonColor = ColorSpec.Unset
        selectedIndex = -1
        selectedBaseAction = StoredAction.of(Action.NONE_TYPE_ID)
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        delayTriggerTypeChangedJob?.cancel()
        triggerType = TriggerType.Press
    }

    /**
     * 用于实现短按和长按
     */
}
