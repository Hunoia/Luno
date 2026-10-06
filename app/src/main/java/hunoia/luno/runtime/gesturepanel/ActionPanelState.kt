package hunoia.luno.runtime.gesturepanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import hunoia.luno.action.model.TriggerType
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.GestureDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberActionPanelState(): ActionPanelState {
    val coroutineScope = rememberCoroutineScope()
    return remember {
        ActionPanelState(coroutineScope)
    }
}

class ActionPanelState(private val coroutineScope: CoroutineScope) : LongSlideState() {

    var visible: Boolean by mutableStateOf(false)
        private set
    var actions: List<StoredAction> by mutableStateOf(emptyList())
        private set
    var direction: GestureDirection by mutableStateOf(GestureDirection.Right)
        private set
    var actionPanelStyle: ActionPanelStyle? by mutableStateOf(null)
        private set
    var buttonColor: Int by mutableStateOf(android.graphics.Color.TRANSPARENT)
        private set
    var parentSize: Size by mutableStateOf(Size.Zero)
        private set
    var itemSizePx: Float by mutableStateOf(0f)
        private set
    var selectedIndex: Int by mutableStateOf(-1)
        private set
    private var selectedBaseAction: StoredAction by mutableStateOf(StoredAction.of("internal.none"))
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

    fun ready(direction: GestureDirection, actions: List<StoredAction>, actionPanelStyle: ActionPanelStyle, buttonColor: Int) {
        this.direction = direction
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
        selectedIndex = if (action.typeId == "internal.none") -1 else index
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
        buttonColor = android.graphics.Color.TRANSPARENT
        selectedIndex = -1
        selectedBaseAction = StoredAction.of("internal.none")
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        delayTriggerTypeChangedJob?.cancel()
        triggerType = TriggerType.Press
    }

    /**
     * 用于实现短按和长按
     */
}
