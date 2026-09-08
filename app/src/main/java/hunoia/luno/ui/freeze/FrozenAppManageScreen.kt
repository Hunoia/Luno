package hunoia.luno.ui.freeze

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.aaron.compose.component.UDFComponent
import com.aaron.compose.component.UiBaseEvent
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.theme.*

@Composable
fun FrozenAppManageContent(
    onDismiss: () -> Unit,
    vm: FrozenAppManageVM = viewModel()
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
        LaunchedEffect(Unit) { vm.reloadApps() }

        var searchQuery by remember { mutableStateOf("") }
        var filterType by remember { mutableStateOf<String?>(null) }

        val allApps = remember(searchQuery, uiState.oneKeyApps, uiState.otherApps) {
            val combined = uiState.oneKeyApps + uiState.otherApps
            if (searchQuery.isBlank()) combined
            else combined.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        val filteredApps = when (filterType) {
            "one_key" -> allApps.filter { it.packageName in uiState.oneKeyPackageNames }
            "other" -> allApps.filter { it.packageName !in uiState.oneKeyPackageNames }
            else -> allApps
        }
        val selectedCount = uiState.oneKeyPackageNames.size
        val frozenCount = uiState.frozenStateByPackage.count { it.value }

        Scaffold(topBar = {
            TopBar(
                onBack = onDismiss,
                title = stringResource(R.string.frozen_app_manage),
                actions = {
                    IconButton(onClick = { vm.clearSelections() }) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.reset))
                    }
                    IconButton(onClick = { vm.onOneKeySelectFrozen() }) {
                        Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.frozen_one_key_select_frozen))
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
                    onQueryChange = { vm.onQueryChange(it); searchQuery = it },
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
                        val isSelected = filterType == "one_key"
                        FilterChip(
                            selected = isSelected,
                            onClick = { filterType = if (isSelected) null else "one_key" },
                            label = { Text(stringResource(R.string.tab_selected)) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                    item {
                        val isUnselected = filterType == "other"
                        FilterChip(
                            selected = isUnselected,
                            onClick = { filterType = if (isUnselected) null else "other" },
                            label = { Text(stringResource(R.string.tab_unselected)) },
                            leadingIcon = if (isUnselected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing4),
                    shape = SheetTopShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.frozen_app_count_info, selectedCount, frozenCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing16, vertical = Spacing10)
                    )
                }

                val hasAnyMatch = (searchQuery.isBlank() && filteredApps.isNotEmpty()) || (searchQuery.isNotBlank() && allApps.isNotEmpty())

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ScrollBottomPadding)
                ) {
                    if (!hasAnyMatch && (searchQuery.isNotBlank() || filterType != null)) {
                        item {
                            EmptyState(message = stringResource(R.string.no_matching_results))
                        }
                    } else if (filteredApps.isNotEmpty()) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isFrozen = uiState.frozenStateByPackage[app.packageName] == true
                            val checked = app.packageName in uiState.pendingOneKeyPackageNames
                            FrozenAppItem(
                                app = app,
                                isFrozen = isFrozen,
                                checked = checked,
                                onCheckedChange = { vm.onOneKeyChecked(app.packageName, it) },
                                onLongClick = { vm.onToggleFrozen(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrozenAppItem(
    app: AppInfo,
    isFrozen: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing12, vertical = Spacing4),
        shape = CardShape,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onCheckedChange(!checked) },
                    onLongClick = onLongClick
                )
                .padding(vertical = ContentPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                modifier = Modifier
                    .padding(start = ContentPaddingHorizontal)
                    .size(MinInteractiveSize),
                model = app.icon,
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
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (isFrozen) {
                Icon(
                    modifier = Modifier.padding(end = Spacing4),
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                modifier = Modifier.padding(end = TopBarPaddingExtra),
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
