package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import hunoia.luno.ui.theme.SheetTopShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
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
        shape = SheetTopShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = PageGutter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
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
                        .padding(horizontal = PageGutter),
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
                            .padding(top = CardInnerSpacing),
                        contentPadding = PaddingValues(bottom = CardInnerSpacing),
                    ) {
                        if (filteredApps.isEmpty()) {
                            item { EmptyState(message = stringResource(R.string.no_matching_results)) }
                        } else {
                            items(filteredApps, key = { it.qualifiedName }) { item ->
                                SelectableListItem(
                                    title = item.label,
                                    subtitle = item.packageName,
                                    iconModel = item.icon,
                                    selected = item.packageName in selected,
                                    onSelect = { isSelected ->
                                        selected = if (isSelected) {
                                            selected + item.packageName
                                        } else {
                                            selected - item.packageName
                                        }
                                    },
                                    showCheckbox = true,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
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
