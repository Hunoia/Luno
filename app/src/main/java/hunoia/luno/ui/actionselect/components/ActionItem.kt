package hunoia.luno.ui.actionselect

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.action.model.StoredAction
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.ContainerRadius

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
    verticalGap: Dp = 0.dp,
) {
    val icon = actionIcon(action)
    SelectableListItem(
        title = actionLabel,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
        icon = icon as? ImageVector,
        textIcon = icon as? TextIcon,
        iconModel = icon?.takeIf { it !is ImageVector && it !is TextIcon },
        showCheckbox = !selectSingle,
        marquee = true,
        shape = shape,
        verticalGap = verticalGap,
    )
}
