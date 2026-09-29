package hunoia.luno.ui.actionselect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.config.model.Action
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionItem(
    onSelect: (Boolean) -> Unit,
    selected: Boolean,
    action: Action,
    actionLabel: String,
    selectSingle: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(AnimNormal.toInt())
    )
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = { onSelect(!selected) },
        enabled = enabled,
        shape = CardShape,
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val icon = actionIcon(action)
            Surface(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp),
                shape = IconBoxShape,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    if (icon is ImageVector) {
                        Image(
                            imageVector = icon,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    } else {
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            imageLoader = context.imageLoader,
                            contentScale = ContentScale.Crop,
                            colorFilter = null
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f, false)
                            .basicMarquee(velocity = 50.dp),
                        text = actionLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (!selectSingle) {
                Checkbox(
                    modifier = Modifier.padding(end = TopBarPaddingExtra),
                    enabled = enabled,
                    checked = selected,
                    onCheckedChange = onSelect
                )
            }
        }
    }
}
