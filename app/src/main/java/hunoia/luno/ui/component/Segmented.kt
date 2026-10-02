package hunoia.luno.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap

private val SegmentedHeaderPadding = PaddingValues(start = PageGutter, top = 8.dp, bottom = 16.dp)

fun segmentedShape(index: Int, count: Int): RoundedCornerShape {
    val isSingle = count <= 1
    val top = if (isSingle || index == 0) ContainerRadius else ConnectionRadius
    val bottom = if (isSingle || index == count - 1) ContainerRadius else ConnectionRadius
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

@Composable
fun SegmentedGroup(
    title: String = "",
    subtitle: String = "",
    modifier: Modifier = Modifier,
    contentSpacing: Dp = SegmentedGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(SegmentedHeaderPadding),
            )
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PageGutter).padding(bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSettingsRow(
    title: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    trailingContent: (@Composable () -> Unit)? = null,
    subtitle: String = "",
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowClick: (() -> Unit)? = if (enabled) onClick else null
    val interactionSource = remember { MutableInteractionSource() }
    ListItem(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .then(rowClick?.let { click -> Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = click) } ?: Modifier)
            .clip(shape),
        headlineContent = { Text(text = title, maxLines = 1) },
        supportingContent = subtitle.takeIf { it.isNotEmpty() }?.let { s ->
            {
                Text(
                    text = s,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = leadingContent ?: icon?.let { iv ->
            {
                Icon(
                    modifier = Modifier.size(RowIconSize),
                    imageVector = iv,
                    contentDescription = null,
                )
            }
        },
        trailingContent = trailingContent ?: chevron.takeIf { rowClick != null },
        colors = ListItemDefaults.colors(
            containerColor = colorScheme.surfaceBright,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = colorScheme.onSurfaceVariant,
            trailingIconColor = colorScheme.onSurfaceVariant,
            supportingColor = secondaryTextColor,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowClick: (() -> Unit)? = if (enabled) onClick ?: { onCheckedChange(!checked) } else null
    val interactionSource = remember { MutableInteractionSource() }
    ListItem(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .then(rowClick?.let { click -> Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = click) } ?: Modifier)
            .clip(shape),
        headlineContent = { Text(text = title, maxLines = 1) },
        supportingContent = subtitle.takeIf { it.isNotEmpty() }?.let { s ->
            {
                Text(
                    text = s,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = leadingContent ?: icon?.let { iv ->
            {
                Icon(
                    modifier = Modifier.size(RowIconSize),
                    imageVector = iv,
                    contentDescription = null,
                )
            }
        },
        trailingContent = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(
                    enabled = enabled,
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = colorScheme.surfaceBright,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = colorScheme.onSurfaceVariant,
            trailingIconColor = colorScheme.onSurfaceVariant,
            supportingColor = secondaryTextColor,
        ),
    )
}

private val chevron: @Composable () -> Unit = {
    Icon(
        modifier = Modifier.size(RowIconSize),
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )
}
