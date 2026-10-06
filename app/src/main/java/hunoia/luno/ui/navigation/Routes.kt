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
