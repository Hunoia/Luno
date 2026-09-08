package hunoia.luno.ui.actionselect
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.combinedClickable
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.bridge.intent.gotoAppDetailSettings
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ContentPaddingVertical
import hunoia.luno.ui.theme.IconTextPadding
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.TopBarPaddingExtra
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult

@Composable
internal fun AppPage(
    onLongClick: (AppInfo) -> Unit,
    onSelect: (AppInfo, Boolean) -> Unit,
    appInfos: List<AppInfo>,
    selectedRecord: SelectedRecord,
    snackbarHostState: SnackbarHostState,
    permissionState: hunoia.luno.ui.permission.PermissionState,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    maxSelectCount: Int = MAX_SELECT_COUNT
) {
    val selectedApps = remember(selectedRecord.list) {
        selectedRecord.list.filterIsInstance<AppInfo>()
    }
    Box(modifier = modifier) {
        if (permissionState.isGranted) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                if (appInfos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing32),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_available_apps),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (!selectSingle && selectedApps.isNotEmpty()) {
                        item(key = "selected_bar") {
                            SelectedBar(
                                selectedItems = selectedApps,
                                maxSelectCount = maxSelectCount,
                                showMaxSelectCount = selectSingle,
                                itemLabel = { (it as AppInfo).label },
                                onRemoveItem = { appInfo -> onSelect(appInfo as AppInfo, false) },
                                onClearAll = { selectedApps.toList().forEach { onSelect(it, false) } }
                            )
                        }
                    }
                    items(
                        items = appInfos,
                        key = { it.qualifiedName }
                    ) { item ->
                        AppItem(
                            appInfo = item,
                            selected = selectedRecord.isSelected(item),
                            selectSingle = selectSingle,
                            enabled = canAppInfoEnabled(selectedRecord, item, maxSelectCount),
                            onSelect = { selected ->
                                onSelect(item, selected)
                            },
                            onLongClick = {
                                onLongClick(item)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppItem(
    onLongClick: () -> Unit,
    onSelect: (Boolean) -> Unit,
    selected: Boolean,
    appInfo: AppInfo,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .fillMaxWidth()
            .padding(horizontal = Spacing12, vertical = Spacing4),
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = enabled,
                    onLongClick = onLongClick,
                    onClick = { onSelect(!selected) }
                )
                .padding(vertical = ContentPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            AsyncImage(
                modifier = Modifier
                    .padding(start = ContentPaddingHorizontal)
                    .size(MinInteractiveSize),
                model = appInfo.icon,
                contentDescription = null,
                imageLoader = context.imageLoader,
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .padding(start = IconTextPadding, end = ItemPadding)
                    .weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing8)
                ) {
                    if (appInfo.miniWindow) {
                        Icon(
                            modifier = Modifier.size(Spacing16),
                            imageVector = Icons.Default.Window,
                            contentDescription = null
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
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = appInfo.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
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
internal fun PermissionPage(
    snackbarHostState: SnackbarHostState,
    permissionState: hunoia.luno.ui.permission.PermissionState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        TextButton(
            onClick = {
                if (permissionState.deniedForever) {
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.goto_grant_get_apps_permission),
                            actionLabel = context.getString(R.string.goto_enable_settings),
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            context.gotoAppDetailSettings()
                        }
                    }
                } else {
                    permissionState.launchPermissionRequest()
                }
            }
        ) {
            Text(text = stringResource(id = R.string.request_get_apps_permission))
        }
    }
}
