package hunoia.luno.ui.actionselect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.clipToBackground
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
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
    enabled: Boolean = true,
    showSettings: Boolean = false,
    onSettingsClick: (() -> Unit)? = null
) {
    val def = ActionFacade.byId(action.value)
    val settingHintText = def?.let { actionSettingHintResMap[it.configKind]?.let { res -> stringResource(res) } }
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(AnimNormal.toInt())
    )
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .fillMaxWidth()
            .padding(horizontal = Spacing12, vertical = Spacing4),
        onClick = { onSelect(!selected) },
        enabled = enabled,
        shape = CardShape,
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .padding(vertical = Spacing8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val icon = actionIcon(action)
            Surface(
                modifier = Modifier
                    .padding(start = ContentPaddingHorizontal)
                    .size(Spacing40),
                shape = IconBoxShape,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(modifier = Modifier.padding(Spacing8), contentAlignment = Alignment.Center) {
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
                    .padding(horizontal = ItemPadding)
                    .weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ItemPadding)
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
                    if (showSettings) {
                        Box(
                            modifier = Modifier
                                .size(Spacing32)
                                .combinedClickable(
                                    enabled = enabled,
                                    onClick = { onSettingsClick?.invoke() },
                                    onLongClick = if (settingHintText != null) {
                                        { hunoia.luno.bridge.feedback.showToast(settingHintText) }
                                    } else null
                                )
                                .clipToBackground(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                modifier = Modifier.size(Spacing20),
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

@Composable
fun SelectedBar(
    selectedItems: List<Any>,
    maxSelectCount: Int,
    showMaxSelectCount: Boolean,
    itemLabel: (Any) -> String,
    onRemoveItem: (Any) -> Unit,
    onClearAll: () -> Unit,
) {
    if (selectedItems.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (showMaxSelectCount) {
                stringResource(R.string.selected_count, selectedItems.size, maxSelectCount)
            } else {
                stringResource(R.string.selected_count_no_limit, selectedItems.size)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onClearAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = Spacing2)
        ) {
            Text(stringResource(R.string.clear_all))
        }
    }
}
