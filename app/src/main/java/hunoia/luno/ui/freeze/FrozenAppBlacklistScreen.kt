package hunoia.luno.ui.freeze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.component.LoadingComponent
import com.aaron.compose.component.UDFComponent
import com.aaron.compose.component.UiBaseEvent
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.bridge.intent.gotoAppDetailSettings
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.*

@Composable
fun FrozenAppBlacklistContent(
    onDismiss: () -> Unit,
    vm: FrozenAppBlacklistVM = viewModel()
) {
    UDFComponent(
        component = vm.udfComponent,
        onEvent = { },
        onBaseEvent = { baseEvent ->
            when (baseEvent) {
                is UiBaseEvent.Finish -> { onDismiss(); true }
                is UiBaseEvent.ResToast -> { showToast(baseEvent.res); true }
                is UiBaseEvent.StringToast -> { showToast(baseEvent.text); true }
                else -> false
            }
        }
    ) { uiState ->
        val permissionState = rememberGetInstalledAppsPermissionState { granted ->
            if (granted) vm.reloadApps()
        }
        LaunchedEffect(vm, permissionState) {
            if (!permissionState.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                vm.reloadApps()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (permissionState.isGranted) {
                LoadingComponent(
                    modifier = Modifier.fillMaxSize(),
                    component = vm.loadingComponent
                ) {
                    var searchQuery by remember { mutableStateOf("") }
                    var filterType by remember { mutableStateOf<String?>(null) }
                    var excludeType by remember { mutableStateOf(ExcludeType.Gesture) }
                    var showResetDialog by remember { mutableStateOf(false) }

                    val allApps = remember(searchQuery, uiState.selectedAppInfos, uiState.unselectedAppInfos) {
                        val combined = uiState.selectedAppInfos + uiState.unselectedAppInfos
                        if (searchQuery.isBlank()) combined
                        else combined.filter {
                            it.label.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    val activeExcludeApps = when (excludeType) {
                        ExcludeType.Gesture -> uiState.excludeApps
                        ExcludeType.PreviousApp -> uiState.previousAppExcludeApps
                    }
                    val filteredApps = when (filterType) {
                        "selected" -> allApps.filter { it.packageName in activeExcludeApps }
                        else -> allApps
                    }
                    val excludedCount = activeExcludeApps.size
                    val hasAnyMatch = (searchQuery.isBlank() && filteredApps.isNotEmpty()) || (searchQuery.isNotBlank() && allApps.isNotEmpty())

                    if (showResetDialog) {
                        AlertDialog(
                            onDismissRequest = { showResetDialog = false },
                            title = { Text(stringResource(R.string.reset)) },
                            text = { Text(stringResource(R.string.reset_exclude_apps_warning_desc)) },
                            confirmButton = {
                                TextButton(onClick = { vm.reset(); showResetDialog = false }) {
                                    Text(stringResource(R.string.confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    Scaffold(topBar = {
                        TopBar(
                            onBack = onDismiss,
                            title = stringResource(R.string.exclude_app),
                            actions = {
                                IconButton(onClick = { showResetDialog = true }) {
                                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.reset))
                                }
                                IconButton(onClick = { vm.reloadApps() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }) { padding ->
                        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                            AppSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal),
                                placeholder = stringResource(R.string.search_app_hint),
                            )

                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ContentPaddingHorizontal, vertical = Spacing4),
                                horizontalArrangement = Arrangement.spacedBy(Spacing8)
                            ) {
                                item {
                                    FilterChip(
                                        selected = excludeType == ExcludeType.Gesture,
                                        onClick = { excludeType = ExcludeType.Gesture },
                                        label = { Text(stringResource(R.string.gesture_exclude_apps)) },
                                        leadingIcon = if (excludeType == ExcludeType.Gesture) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                        } else null
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = excludeType == ExcludeType.PreviousApp,
                                        onClick = { excludeType = ExcludeType.PreviousApp },
                                        label = { Text(stringResource(R.string.previous_app_exclude_apps)) },
                                        leadingIcon = if (excludeType == ExcludeType.PreviousApp) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                        } else null
                                    )
                                }
                                item {
                                    val isAll = filterType == null
                                    FilterChip(
                                        selected = isAll,
                                        onClick = { filterType = null },
                                        label = { Text(stringResource(R.string.all_categories)) },
                                        leadingIcon = if (isAll) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                        } else null
                                    )
                                }
                                item {
                                    val isSelected = filterType == "selected"
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { filterType = if (isSelected) null else "selected" },
                                        label = { Text(stringResource(R.string.tab_selected)) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                        } else null
                                    )
                                }
                            }

                            if (excludedCount > 0) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing4),
                                    shape = SheetTopShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (excludeType == ExcludeType.Gesture) R.string.excluded_count else R.string.previous_app_excluded_count,
                                            excludedCount,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = Spacing16, vertical = Spacing10)
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = ScrollBottomPadding)
                            ) {
                                if (!hasAnyMatch && (searchQuery.isNotBlank() || filterType != null)) {
                                    item {
                                        EmptyState(message = stringResource(R.string.no_matching_results))
                                    }
                                } else if (filteredApps.isNotEmpty()) {
                                    items(filteredApps, key = { it.qualifiedName }) { item ->
                                        AppBlacklistItem(
                                            appInfo = item,
                                            selected = item.packageName in activeExcludeApps,
                                            onSelect = { selected ->
                                                when (excludeType) {
                                                    ExcludeType.Gesture -> vm.selectApp(item, selected)
                                                    ExcludeType.PreviousApp -> vm.selectPreviousApp(item, selected)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val context = LocalContext.current
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = {
                            if (permissionState.deniedForever) {
                                context.gotoAppDetailSettings()
                            } else {
                                permissionState.launchPermissionRequest()
                            }
                        }
                    ) {
                        Text(text = stringResource(id = R.string.request_get_apps_permission))
                    }
                }
            }
        }
    }
}

private enum class ExcludeType { Gesture, PreviousApp }

@Composable
private fun AppBlacklistItem(
    onSelect: (Boolean) -> Unit,
    selected: Boolean,
    appInfo: AppInfo
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing12, vertical = Spacing4),
        onClick = { onSelect(!selected) },
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = appInfo.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = appInfo.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Checkbox(
                modifier = Modifier.padding(end = TopBarPaddingExtra),
                checked = selected,
                onCheckedChange = onSelect
            )
        }
    }
}
