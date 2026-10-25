package hunoia.luno.ui.component.sheet
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.theme.EmptyStatePaddingVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.feedback.LunoIconButton
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    onDismissRequest: () -> Unit,
    selectedPackageNames: List<String>,
    onConfirm: (List<String>) -> Unit,
    /** 单选场景：点击应用即确认并关闭，不展示底部取消/确认按钮。 */
    confirmOnSelect: Boolean = false,
) {
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
            QuickLaunchFacade.queryAppsIncludingDisabled(AppContext.get())
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
    LunoSheet(
        onDismissRequest = onDismissRequest,
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
                    LunoIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { scope.launch { loadApps() } },
                    )
                }
            }
            if (!permissionState.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = EmptyStatePaddingVertical),
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
                        .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                    placeholder = stringResource(R.string.search_app_hint),
                )
                if (loading) {
                    LoadingState(message = stringResource(R.string.loading))
                } else {
                    val selectionFlags = filteredApps.map { it.packageName in selected }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = SheetListMaxHeight)
                            .padding(top = CardInnerSpacing),
                        contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = CardInnerSpacing),
                        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                    ) {
                        if (filteredApps.isEmpty()) {
                            item { EmptyState(message = stringResource(R.string.no_matching_results)) }
                        } else {
                            itemsIndexed(filteredApps, key = { _, item -> item.qualifiedName }) { index, item ->
                                LunoSelectableRow(
                                    title = item.label,
                                    subtitle = item.packageName,
                                    iconModel = item.icon,
                                    selected = item.packageName in selected,
                                    onSelectedChange = { isSelected ->
                                        if (confirmOnSelect) {
                                            onConfirm(listOf(item.packageName))
                                            onDismissRequest()
                                        } else {
                                            selected = if (isSelected) {
                                                selected + item.packageName
                                            } else {
                                                selected - item.packageName
                                            }
                                        }
                                    },
                                    shape = selectedSegmentedShape(index, filteredApps.size, selectionFlags),
                                )
                            }
                        }
                    }
                }
                if (!confirmOnSelect) {
                    SheetActionButtons(
                        onDismiss = onDismissRequest,
                        onConfirm = {
                            onConfirm(selectedPackageNames.filter { it in selected } + selected.filter { it !in selectedPackageNames })
                            onDismissRequest()
                        },
                        modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                    )
                }
            }
        }
    }
}