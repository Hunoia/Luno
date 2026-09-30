package hunoia.luno.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.ui.theme.AnimNormal
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SmallShape
import hunoia.luno.ui.theme.TrailingPadding

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SelectableListItem(
    title: String = "",
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    headlineContent: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconModel: Any? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    leadingIndent: Dp = 0.dp,
    showCheckbox: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    marquee: Boolean = false,
    verticalGap: Dp = ListItemVerticalPadding,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) colorScheme.primaryContainer else colorScheme.surfaceBright,
        animationSpec = tween(AnimNormal.toInt()),
        label = "selectableListItemBackground",
    )
    val iconTint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
    val checkbox: (@Composable () -> Unit)? = if (showCheckbox) {
        {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(
                    modifier = Modifier.padding(end = TrailingPadding),
                    enabled = enabled,
                    checked = selected,
                    onCheckedChange = onSelect,
                )
            }
        }
    } else {
        null
    }
    ListItem(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .padding(horizontal = PageGutter, vertical = verticalGap)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = { onSelect(!selected) },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = { onSelect(!selected) },
                    )
                },
            )
            .padding(start = leadingIndent)
            .clip(shape),
        headlineContent = headlineContent ?: {
            Text(
                modifier = if (marquee) Modifier.basicMarquee(velocity = 50.dp) else Modifier,
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = subtitle.takeIf { it.isNotEmpty() }?.let { s ->
            { Text(text = s, style = MaterialTheme.typography.bodySmall) }
        },
        leadingContent = leadingContent
            ?: icon?.let { iv ->
                {
                    Icon(
                        modifier = Modifier.size(RowIconSize),
                        imageVector = iv,
                        contentDescription = null,
                        tint = iconTint,
                    )
                }
            }
            ?: iconModel?.let { model ->
                {
                    AsyncImage(
                        modifier = Modifier.size(RowIconSize).clip(SmallShape),
                        model = model,
                        contentDescription = null,
                        imageLoader = LocalContext.current.imageLoader,
                        contentScale = ContentScale.Crop,
                    )
                }
            },
        trailingContent = trailingContent ?: checkbox,
        colors = ListItemDefaults.colors(
            containerColor = background,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = iconTint,
            trailingIconColor = colorScheme.onSurfaceVariant,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
    )
}
