package hunoia.luno.ui.navigation

import androidx.annotation.Keep
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.ActionLibraryType
import kotlinx.serialization.Serializable



@Keep
@Serializable
data class ActionSelect(
    val gestureButtonId: String,
    val direction: GestureDirection,
    val triggerType: GestureTriggerType = GestureTriggerType.Slide,
    val subGestureId: String = "",
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
data class SubGestureEditor(
    val subGestureId: String
)

@Keep
@Serializable
data object Condition

@Keep
@Serializable
data class ConditionEdit(
    val ruleId: String,
)

@Keep
@Serializable
data class ActionLibraryEdit(
    val entryId: String,
    val type: ActionLibraryType? = null,
)

@Keep
@Serializable
data class NewActionLibraryEdit(
    val entryId: String,
    val typeId: String? = null,
)

@Keep
@Serializable
data object NewActionLibrary

const val NEW_CONDITION_RULE_ID = "new"

const val NEW_ACTION_LIBRARY_ENTRY_ID = "new"
