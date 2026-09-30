package hunoia.luno.action.definition

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.config.model.Action

data class ActionCatalogEntry(
    val actionId: String,
    val category: ActionCategory,
    @param:StringRes val titleResId: Int,
    val typeId: String,
    val isDisplayed: Boolean = true
) {
    val icon: ImageVector? get() = ActionDefinitions.byTypeId(typeId)?.icon
    fun toAction(): Action = Action(actionId)
}
