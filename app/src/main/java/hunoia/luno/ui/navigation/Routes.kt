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
)

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
data object SearchEngineSettings

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
