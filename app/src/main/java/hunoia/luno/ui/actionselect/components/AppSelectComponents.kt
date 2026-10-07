package hunoia.luno.ui.actionselect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import hunoia.luno.R
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.ui.theme.BadgeIconSize
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.component.row.LunoSelectableRow
@Composable
internal fun AppItem(
    onLongClick: () -> Unit,
    onSelect: (Boolean) -> Unit,
    selected: Boolean,
    appInfo: AppInfo,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ContainerRadius),
) {
    LunoSelectableRow(
        selected = selected,
        onSelectedChange = onSelect,
        modifier = modifier,
        enabled = enabled,
        iconModel = appInfo.icon,
        subtitle = appInfo.packageName,
        singleSelect = selectSingle,
        onLongClick = onLongClick,
        shape = shape,
        headline = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                if (appInfo.miniWindow) {
                    Icon(
                        modifier = Modifier.size(BadgeIconSize),
                        imageVector = Icons.Default.Window,
                        contentDescription = stringResource(R.string.mini_window_position_short),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = appInfo.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    )
}