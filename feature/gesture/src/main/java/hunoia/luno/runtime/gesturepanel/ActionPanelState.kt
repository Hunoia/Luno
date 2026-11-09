package hunoia.luno.runtime.gesturepanel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import hunoia.luno.action.model.TriggerType
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.ColorSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hunoia.luno.action.model.Action

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
            delay(500)
            triggerType = TriggerType.LongPress
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
