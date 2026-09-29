package hunoia.luno.action.definition

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.config.model.Action

data class ActionDefinition(
    val actionId: String,
    val category: ActionCategory,
    @StringRes val titleResId: Int,
    val icon: ImageVector,
    val isDisplayed: Boolean = true
) {
    fun toAction(): Action = Action(actionId)
}
