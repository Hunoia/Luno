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
    /** 自动化步骤路径（"0" / "2.then.0"）；空 = 规则级。 */
    val automationStepPath: String = "",
    /** 非空 = 本次不配触钮/子手势，而是在这个动作面板里挑一个面板项。 */
    val actionPanelId: String = "",
    /** true = 只选动作并复制其 Deep Link，不写入任何配置。 */
    val copyDeepLink: Boolean = false,
    /**
     * >=0 = 在改 `actionPanelId` 面板里第 N 项的**长按变体**，否则是新增面板项。
     * 长按变体是面板专属机制：面板自带 500ms 长按判定，见 ActionPanelState。
     */
    val actionPanelItemIndex: Int = -1,
    /** 非空 = 配网格面板：加项只进不出，想换动作就删了重加，所以没有「改已有项」的分支。 */
    val gridPanelId: String = "",
    /** 加项落到的页（派生页数可为最大页 +1，即编辑态虚页，落项即建页）。 */
    val gridPanelPageIndex: Int = 0,
    /** 非空 = 在配悬浮球的这个槽（见 [FloatBallSlot]），与上面各 id 字段互斥。 */
    val floatBallSlot: String = "",
)

/** 悬浮球的动作槽。只有「单击」一个：滑动固定读面板引用，长按固定是拖球。 */
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
data object RecentAppsBlocklist

/** 链接分发设置（规则列表 + 胶囊/未命中行为）。 */
@Keep
@Serializable
data object LinkDispatchRules

/** 链接分发规则编辑页；ruleId 空 = 新增。 */
@Keep
@Serializable
data class LinkDispatchRuleEdit(
    val ruleId: String = "",
)

@Keep
@Serializable
data object NotificationBlocklist

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

/** 网格面板属性编辑（名称/列数/宽高比）；项在面板内编辑。 */
@Keep
@Serializable
data class GridPanelEdit(
    val panelId: String,
)

@Keep
@Serializable
data object SearchEngineSettings

/** 引擎编辑页（引擎设置页的下级页）；engineId 空 = 新增。 */
@Keep
@Serializable
data class SearchEngineEdit(
    val engineId: String = "",
)

/** 搜索内容源与来源标识设置（引擎设置页的子页）。 */
@Keep
@Serializable
data object SearchSourceSettings

/**
 * 小窗位置/大小调整页。独立页面而非全屏 overlay：纯配置任务，不需看见真实屏幕，
 * 页内画示意图即可（见 ui/settings/action/MiniWindowAdjustScreen.kt）。
 */
@Keep
@Serializable
data object MiniWindowAdjust

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
data class ActionLibraryEdit(
    val entryId: String,
    val typeId: String? = null,
)


const val NEW_AUTOMATION_RULE_ID = "new"

const val ACTION_LIBRARY_ENTRY_ID = "new"
