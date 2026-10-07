package hunoia.luno.ui.actionselect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.action.model.StoredAction
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.component.row.LunoSelectableRow
@Composable
fun ActionItem(
    onSelect: (Boolean) -> Unit,
    selected: Boolean,
    action: StoredAction,
    actionLabel: String,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ContainerRadius),
) {
    val icon = actionIcon(action)
    LunoSelectableRow(
        title = actionLabel,
        selected = selected,
        onSelectedChange = onSelect,
        modifier = modifier,
        enabled = enabled,
        icon = icon as? ImageVector,
        textIcon = icon as? TextIcon,
        iconModel = icon?.takeIf { it !is ImageVector && it !is TextIcon },
        singleSelect = selectSingle,
        marquee = true,
        shape = shape,
    )
}