package hunoia.luno.ui.actionselect
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.onClick
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.theme.TopBarPaddingExtra
import androidx.compose.ui.util.fastForEach

@Composable
internal fun LauncherInfoItem(
    canLauncherInfoEnabled: (LauncherInfo) -> Boolean,
    canShortcutInfoEnabled: (LauncherInfo.ShortcutInfo) -> Boolean,
    isShortcutInfoSelected: (LauncherInfo.ShortcutInfo) -> Boolean,
    onClick: () -> Unit,
    onSelect: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    launcherInfo: LauncherInfo,
    selectSingle: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .alpha(if (canLauncherInfoEnabled(launcherInfo)) 1f else SettingsUiDefaults.DisabledAlpha)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onClick(enabled = canLauncherInfoEnabled(launcherInfo)) { onClick() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                AsyncImage(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(MinInteractiveSize),
                    model = launcherInfo.icon,
                    contentDescription = null,
                    imageLoader = context.imageLoader,
                )
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = launcherInfo.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = launcherInfo.packageName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                launcherInfo.shortcuts.fastForEach { shortcutInfo ->
                    key(shortcutInfo) {
                        val selected = isShortcutInfoSelected(shortcutInfo)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            onClick = { onSelect(shortcutInfo, !selected) },
                            enabled = canShortcutInfoEnabled(shortcutInfo),
                            shape = IconBoxShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val context = LocalContext.current
                                AsyncImage(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(SubMinInteractiveSize),
                                    model = shortcutInfo.icon,
                                    contentDescription = null,
                                    imageLoader = context.imageLoader
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(start = 8.dp, end = 16.dp)
                                        .weight(1f)
                                ) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = shortcutInfo.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                if (!selectSingle) {
                                    Checkbox(
                                        modifier = Modifier.padding(end = TopBarPaddingExtra),
                                        enabled = canShortcutInfoEnabled(shortcutInfo),
                                        checked = selected,
                                        onCheckedChange = { newSelected ->
                                            onSelect(shortcutInfo, newSelected)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
