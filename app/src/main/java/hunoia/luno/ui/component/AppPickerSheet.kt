package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.CardShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.TopBarPaddingExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    onDismissRequest: () -> Unit,
    selectedPackageNames: List<String>,
    onConfirm: (List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf(selectedPackageNames.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    val permissionState = rememberGetInstalledAppsPermissionState { granted ->
        if (granted) loaded = false
    }

    suspend fun loadApps() {
        loading = true
        apps = withContext(Dispatchers.IO) {
            try {
                val context = AppContext.get()
                val appInfos = QuickLaunchFacade
                    .queryApps(context)
                    .filter { it.packageName != context.packageName }
                val disabledApps = DisabledAppQuery.queryDisabledApplicationsOnIo(context)
                val normalPackageNames = appInfos.map { it.packageName }.toSet()
                val filteredDisabledApps = disabledApps.filter {
                    it.packageName !in normalPackageNames && it.packageName != context.packageName
                }
                (appInfos + filteredDisabledApps)
            } catch (_: Exception) {
                emptyList()
            }
        }
        loading = false
    }

    LaunchedEffect(permissionState.isGranted, loaded) {
        if (!permissionState.isGranted) {
            permissionState.launchPermissionRequest()
        } else if (!loaded) {
            loadApps()
            loaded = true
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.condition_app_select_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (permissionState.isGranted) {
                    IconButton(onClick = { scope.launch { loadApps() } }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                }
            }

            if (!permissionState.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { permissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.request_get_apps_permission))
                    }
                }
            } else {
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = stringResource(R.string.search_app_hint),
                )

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(top = 8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        if (filteredApps.isEmpty()) {
                            item { EmptyState(message = stringResource(R.string.no_matching_results)) }
                        } else {
                            items(filteredApps, key = { it.qualifiedName }) { item ->
                                AppPickerItem(
                                    appInfo = item,
                                    selected = item.packageName in selected,
                                    onSelect = { isSelected ->
                                        selected = if (isSelected) {
                                            selected + item.packageName
                                        } else {
                                            selected - item.packageName
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfirm(selectedPackageNames.filter { it in selected } + selected.filter { it !in selectedPackageNames })
                            onDismissRequest()
                        },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerItem(
    appInfo: AppInfo,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { onSelect(!selected) },
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val context = LocalContext.current
            AsyncImage(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(MinInteractiveSize),
                model = appInfo.icon,
                contentDescription = null,
                imageLoader = context.imageLoader,
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 16.dp)
                    .weight(1f),
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
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Checkbox(
                modifier = Modifier.padding(end = TopBarPaddingExtra),
                checked = selected,
                onCheckedChange = onSelect,
            )
        }
    }
}
