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
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    subtitle: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    val icon = actionIcon(action)
    LunoSelectableRow(
        title = actionLabel,
        subtitle = subtitle,
        selected = selected,
        onSelectedChange = onSelect,
        modifier = modifier,
        icon = icon as? ImageVector,
        textIcon = icon as? TextIcon,
        iconModel = icon?.takeIf { it !is ImageVector && it !is TextIcon },
        singleSelect = true,
        marquee = true,
        shape = shape,
        trailing = trailing,
    )
}