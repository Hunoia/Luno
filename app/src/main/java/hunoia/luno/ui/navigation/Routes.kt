package hunoia.luno.ui.navigation

import androidx.annotation.Keep
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import kotlinx.serialization.Serializable



@Keep
@Serializable
data class ActionSelect(
    val gestureButtonId: String = "",
    val direction: GestureDirection = GestureDirection.Right,
    val triggerType: GestureTriggerType = GestureTriggerType.Slide,
    val subGestureId: String = "",
    val automationRuleId: String = "",
    /** 自动化步骤路径（"0" / "2.then.0"），空=整条规则级 legacy。 */
    val automationStepPath: String = "",
    /** 非空 = 本次不配触钮/子手势，而是在这个动作面板里挑一个面板项。 */
    val actionPanelId: String = "",
    /**
     * >=0 = 在改 `actionPanelId` 面板里第 N 项的**长按变体**，否则是新增面板项。
     * 长按变体本来就是面板专属机制（面板自带 500ms 长按判定），见 ActionPanelState。
     */
    val actionPanelItemIndex: Int = -1,
    /**
     * 非空 = 本次在配网格面板（docs/grid-panel.md）：
     * 空索引是加项；`gridPanelItemIndex >= 0` 时按 [gridPanelReplaceItem] 分流——
     * true 是换掉整项（丢弃旧长按变体），false 是设该项的长按变体。
     */
    val gridPanelId: String = "",
    val gridPanelItemIndex: Int = -1,
    val gridPanelReplaceItem: Boolean = false,
    /**
     * 非空 = 本次在配悬浮球，且改的是这个槽（见 [FloatBallSlot]）。
     * 与上面那些 id 字段互斥：走它就不会落进触钮/子手势/面板/自动化任何一条分支。
     */
    val floatBallSlot: String = "",
)

/** 悬浮球的动作槽。目前只有「单击」一个（滑动固定读面板引用，长按固定是拖球）。 */
object FloatBallSlot {
    const val TAP = "tap"

    fun isValid(slot: String): Boolean = slot == TAP
}

@Serializable
@Keep
data class GestureButtonSettings(
    val buttonId: String,
)

@Keep
@Serializable
data object Home

@Keep
@Serializable
data object ActionSettings

@Keep
@Serializable
data object ActionPanelList

@Keep
@Serializable
data class ActionPanelEdit(
    val panelId: String,
)

@Keep
@Serializable
data object GridPanelList

/** 网格面板属性编辑（名称/列数/宽高比）；内容（项）在面板内编辑，见 docs/grid-panel.md。 */
@Keep
@Serializable
data class GridPanelEdit(
    val panelId: String,
)

@Keep
@Serializable
data object SearchEngineSettings

/**
 * 小窗位置/大小调整页。独立页面而非全屏 overlay：这是纯配置任务，不需要看见真实屏幕，
 * 页内画示意图即可（见 ui/settings/action/MiniWindowAdjustScreen.kt）。
 */
@Keep
@Serializable
data object MiniWindowAdjust

/** 悬浮球设置页。 */
@Keep
@Serializable
data object FloatBallEdit

@Keep
@Serializable
data class SubGestureEditor(
    val subGestureId: String
)

@Keep
@Serializable
data class AutomationEdit(
    val ruleId: String,
)

@Keep
@Serializable
data class AutomationHistory(
    val ruleId: String,
)

@Keep
@Serializable
data class NewActionLibraryEdit(
    val entryId: String,
    val typeId: String? = null,
)


const val NEW_AUTOMATION_RULE_ID = "new"

const val NEW_ACTION_LIBRARY_ENTRY_ID = "new"
