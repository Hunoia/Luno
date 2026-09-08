package hunoia.luno.ui.actionselect
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.onClick
import androidx.compose.foundation.ExperimentalFoundationApi
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ContentPaddingVertical
import hunoia.luno.ui.theme.IconTextPadding
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.theme.TopBarPaddingExtra
import androidx.compose.foundation.background
import androidx.compose.ui.util.fastForEach

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutPage(
    onClick: (LauncherInfo) -> Unit,
    onSelect: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    createShortcuts: List<LauncherInfo>,
    launchShortcuts: List<LauncherInfo>,
    selectedRecord: SelectedRecord,
    snackbarHostState: SnackbarHostState,
    permissionState: hunoia.luno.ui.permission.PermissionState,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    maxSelectCount: Int = MAX_SELECT_COUNT
) {
    val selectedShortcuts = remember(selectedRecord.list) {
        selectedRecord.list.filterIsInstance<LauncherInfo.ShortcutInfo>()
    }
    Box(modifier = modifier) {
        if (permissionState.isGranted) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                if (createShortcuts.isEmpty() && launchShortcuts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing32),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_available_shortcuts),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (!selectSingle && selectedShortcuts.isNotEmpty()) {
                        item(key = "selected_bar") {
                            SelectedBar(
                                selectedItems = selectedShortcuts,
                                maxSelectCount = maxSelectCount,
                                showMaxSelectCount = selectSingle,
                                itemLabel = { (it as LauncherInfo.ShortcutInfo).label },
                                onRemoveItem = { shortcutInfo -> onSelect(shortcutInfo as LauncherInfo.ShortcutInfo, false) },
                                onClearAll = { selectedShortcuts.toList().forEach { onSelect(it, false) } }
                            )
                        }
                    }
                    if (createShortcuts.isNotEmpty()) {
                        stickyHeader {
                            Text(
                                modifier = Modifier
                                    .background(color = MaterialTheme.colorScheme.background)
                                    .fillMaxWidth()
                                    .padding(vertical = ContentPaddingVertical)
                                    .padding(horizontal = ContentPaddingHorizontal * 2),
                                text = stringResource(R.string.create_shortcut),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    items(
                        items = createShortcuts,
                        key = { it.qualifiedName }
                    ) { item ->
                        LauncherInfoItem(
                            launcherInfo = item,
                            selectSingle = selectSingle,
                            canLauncherInfoEnabled = { canLauncherInfoEnabled(selectedRecord, it, maxSelectCount) },
                            canShortcutInfoEnabled = { canShortcutInfoEnabled(selectedRecord, it, maxSelectCount) },
                            isShortcutInfoSelected = { shortcutInfo ->
                                selectedRecord.isSelected(shortcutInfo)
                            },
                            onSelect = { shortcutInfo, selected ->
                                onSelect(shortcutInfo, selected)
                            },
                            onClick = {
                                onClick(item)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (launchShortcuts.isNotEmpty()) {
                        stickyHeader {
                            Text(
                                modifier = Modifier
                                    .background(color = MaterialTheme.colorScheme.background)
                                    .fillMaxWidth()
                                    .padding(vertical = ContentPaddingVertical)
                                    .padding(horizontal = ContentPaddingHorizontal * 2),
                                text = stringResource(R.string.launch_shortcut),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    items(
                        items = launchShortcuts,
                        key = { it.qualifiedName }
                    ) { item ->
                        LauncherInfoItem(
                            launcherInfo = item,
                            selectSingle = selectSingle,
                            canLauncherInfoEnabled = { canLauncherInfoEnabled(selectedRecord, it, maxSelectCount) },
                            canShortcutInfoEnabled = { canShortcutInfoEnabled(selectedRecord, it, maxSelectCount) },
                            isShortcutInfoSelected = { shortcutInfo ->
                                selectedRecord.isSelected(shortcutInfo)
                            },
                            onSelect = { shortcutInfo, selected ->
                                onSelect(shortcutInfo, selected)
                            },
                            onClick = {
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        } else {
            PermissionPage(
                snackbarHostState = snackbarHostState,
                permissionState = permissionState
            )
        }
    }
}

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
            .padding(horizontal = Spacing12, vertical = Spacing4),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onClick(enabled = canLauncherInfoEnabled(launcherInfo)) { onClick() }
                    .padding(vertical = ContentPaddingVertical),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                AsyncImage(
                    modifier = Modifier
                        .padding(start = ContentPaddingHorizontal)
                        .size(MinInteractiveSize),
                    model = launcherInfo.icon,
                    contentDescription = null,
                    imageLoader = context.imageLoader,
                )
                Column(
                    modifier = Modifier
                        .padding(start = IconTextPadding, end = ItemPadding)
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

            Column(modifier = Modifier.padding(start = Spacing8, end = Spacing8, bottom = Spacing8)) {
                launcherInfo.shortcuts.fastForEach { shortcutInfo ->
                    key(shortcutInfo) {
                        val selected = isShortcutInfoSelected(shortcutInfo)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing4),
                            onClick = { onSelect(shortcutInfo, !selected) },
                            enabled = canShortcutInfoEnabled(shortcutInfo),
                            shape = IconBoxShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing8),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val context = LocalContext.current
                                AsyncImage(
                                    modifier = Modifier
                                        .padding(start = ContentPaddingHorizontal)
                                        .size(SubMinInteractiveSize),
                                    model = shortcutInfo.icon,
                                    contentDescription = null,
                                    imageLoader = context.imageLoader
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(start = IconTextPadding, end = ItemPadding)
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
