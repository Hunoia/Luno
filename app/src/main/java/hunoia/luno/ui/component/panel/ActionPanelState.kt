package hunoia.luno.ui.component.panel

import hunoia.luno.ui.component.container.LongSlideState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import hunoia.luno.action.TriggerType
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.Action
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
    var actions: List<Action> by mutableStateOf(emptyList())
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
    private var selectedBaseAction: Action by mutableStateOf(Action.NONE)
    val selectedAction: Action
        get() = when (triggerType) {
            TriggerType.Press -> selectedBaseAction
            TriggerType.LongPress -> selectedBaseAction.longPressAction ?: selectedBaseAction
        }
    var triggerType: TriggerType by mutableStateOf(TriggerType.Press)
        private set
    private var delayTriggerTypeChangedJob: Job? = null

    override fun onDragStart(offset: Offset) {
        super.onDragStart(offset)
        visible = true
    }

    fun ready(direction: GestureDirection, actions: List<Action>, actionPanelStyle: ActionPanelStyle, buttonColor: Int) {
        this.direction = direction
        this.actions = actions
        this.actionPanelStyle = actionPanelStyle
        this.buttonColor = buttonColor
    }

    fun setLayoutInfo(parentSize: Size, itemSizePx: Float) {
        this.parentSize = parentSize
        this.itemSizePx = itemSizePx
    }

    fun hitTestAction(finger: Offset): Action? {
        if (!visible || parentSize.isEmpty() || itemSizePx <= 0f || origin.x.isNaN()) return null
        val panelOrigin = actionPanelOrigin(parentSize, origin, itemSizePx)
        val sector = actionPanelSector(parentSize, panelOrigin, direction, itemSizePx)
        val spreadSpacing = (actionPanelStyle as? ArcStyle)?.spreadSpacing ?: 1.0f
        actions.forEachIndexed { index, action ->
            val displayIndex = actionPanelDisplayIndex(sector, actions.size, index)
            val targetAnimOffset = actionPanelItemOffset(
                parentSize = parentSize,
                origin = panelOrigin,
                sector = sector,
                actionCount = actions.size,
                index = displayIndex,
                itemSizePx = itemSizePx,
                spreadSpacing = spreadSpacing,
            )
            if (actionPanelHitContains(finger, panelOrigin, targetAnimOffset, itemSizePx)) {
                return action
            }
        }
        return null
    }

    fun cancel() {
        reset()
    }

    fun onSelectStart(offset: Offset) {
        finger = offset
    }

    fun done(): Action {
        val action = selectedAction
        val triggerType = triggerType
        reset()
        return action.copy(extra = triggerType)
    }

    fun isSelected(action: Action): Boolean {
        return selectedBaseAction == action
    }

    fun select(index: Int, action: Action) {
        selectedIndex = if (action == Action.NONE) -1 else index
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
        selectedBaseAction = Action.NONE
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        delayTriggerTypeChangedJob?.cancel()
        triggerType = TriggerType.Press
    }

    /**
     * 用于实现短按和长按
     */
}
