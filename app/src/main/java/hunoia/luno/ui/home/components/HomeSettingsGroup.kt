package hunoia.luno.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.ContainerRadius

@Composable
fun HomeSettingsGroup(
    onActionSettingsClick: () -> Unit,
    onConditionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedGroup(
        title = stringResource(R.string.home_section_settings),
        modifier = modifier,
    ) {
        SegmentedSettingsRow(
            title = stringResource(R.string.action_settings),
            icon = Icons.TwoTone.Tune,
            shape = segmentedShape(0, 2),
            onClick = onActionSettingsClick,
        )
        SegmentedSettingsRow(
            title = stringResource(R.string.condition_home),
            icon = Icons.TwoTone.FilterAlt,
            shape = segmentedShape(1, 2),
            onClick = onConditionClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    trailingContent: @Composable (() -> Unit)? = {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    },
) {
    val colorScheme = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clickable(onClick = onClick).clip(shape),
        headlineContent = { Text(text = title, maxLines = 1) },
        leadingContent = {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = icon,
                contentDescription = null,
            )
        },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = colorScheme.surfaceBright,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = colorScheme.onSurfaceVariant,
            trailingIconColor = colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
fun DataTransferGroup(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedGroup(
        title = stringResource(R.string.home_section_data),
        modifier = modifier,
    ) {
        SegmentedSettingsRow(
            title = stringResource(R.string.backup),
            icon = Icons.TwoTone.Save,
            shape = segmentedShape(0, 3),
            onClick = onBackupClick,
        )
        SegmentedSettingsRow(
            title = stringResource(R.string.restore),
            icon = Icons.TwoTone.Restore,
            shape = segmentedShape(1, 3),
            onClick = onRestoreClick,
        )
        SegmentedSettingsRow(
            title = stringResource(R.string.reset),
            icon = Icons.TwoTone.RestartAlt,
            shape = segmentedShape(2, 3),
            onClick = onResetClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    leadingContent: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clickable(onClick = onClick ?: { onCheckedChange(!checked) }).clip(shape),
        headlineContent = { Text(text = title, maxLines = 1) },
        leadingContent = leadingContent,
        trailingContent = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = colorScheme.surfaceBright,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = colorScheme.onSurfaceVariant,
            trailingIconColor = colorScheme.onSurfaceVariant,
        ),
    )
}
