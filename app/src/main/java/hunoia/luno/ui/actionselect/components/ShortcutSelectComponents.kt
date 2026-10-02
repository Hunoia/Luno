package hunoia.luno.ui.actionselect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.onClick
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.ShapeSmall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherInfoItem(
    canLauncherInfoEnabled: (LauncherInfo) -> Boolean,
    canShortcutInfoEnabled: (LauncherInfo.ShortcutInfo) -> Boolean,
    isShortcutInfoSelected: (LauncherInfo.ShortcutInfo) -> Boolean,
    onClick: () -> Unit,
    onSelect: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    launcherInfo: LauncherInfo,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val launcherEnabled = canLauncherInfoEnabled(launcherInfo)
    val total = 1 + launcherInfo.shortcuts.size
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        ListItem(
            modifier = Modifier
                .alpha(if (launcherEnabled) 1f else SettingsUiDefaults.DisabledAlpha)
                .padding(horizontal = PageGutter)
                .then(
                    if (launcherEnabled) Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ) else Modifier,
                )
                .clip(segmentedShape(0, total)),
            headlineContent = {
                Text(
                    text = launcherInfo.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text = launcherInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            leadingContent = {
                AsyncImage(
                    modifier = Modifier.size(RowIconSize).clip(RoundedCornerShape(ShapeSmall)),
                    model = launcherInfo.icon,
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                    contentScale = ContentScale.Crop,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = colorScheme.surfaceBright,
                headlineColor = colorScheme.onSurface,
                leadingIconColor = colorScheme.onSurfaceVariant,
                supportingColor = colorScheme.onSurfaceVariant,
            ),
        )
        launcherInfo.shortcuts.fastForEachIndexed { index, shortcutInfo ->
            key(shortcutInfo) {
                SelectableListItem(
                    title = shortcutInfo.label,
                    selected = isShortcutInfoSelected(shortcutInfo),
                    onSelect = { onSelect(shortcutInfo, it) },
                    enabled = canShortcutInfoEnabled(shortcutInfo),
                    iconModel = shortcutInfo.icon,
                    showCheckbox = !selectSingle,
                    shape = segmentedShape(index + 1, total),
                    verticalGap = 0.dp,
                )
            }
        }
    }
}
