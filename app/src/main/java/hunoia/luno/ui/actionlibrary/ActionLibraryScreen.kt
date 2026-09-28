package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.core.AppContext
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.navigation.ActionLibraryEdit
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap

@Composable
fun ActionLibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    vm: ActionLibraryVM = viewModel(),
    onNavToEdit: (ActionLibraryEdit) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val uiState by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDeleteEntry by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    var resetCounter by remember { mutableStateOf(0) }

    val filtered = remember(uiState.entries, uiState.referenceCounts, query) {
        uiState.entries
            .filter { it.matchesQuery(query) }
            .sortedWith(compareBy<ActionLibraryEntry> { it.type.sortIndex() }.thenBy { it.createdAt })
    }
    val grouped = remember(filtered) { filtered.groupBy { it.type } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_hint_all),
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState(stringResource(R.string.action_library_empty)) }
                } else {
                    grouped.forEach { (type, entries) ->
                        stickyHeader(key = "header_${type.name}") {
                            Text(
                                text = stringResource(type.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp),
                            )
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                            ActionLibrarySwipeRow(
                                modifier = Modifier.animateItem(),
                                shape = segmentedShape(index, entries.size),
                                entry = entry,
                                referenceCount = uiState.referenceCounts[entry.id] ?: 0,
                                onNavToEdit = {
                                    onNavToEdit(ActionLibraryEdit(entry.id, entry.type))
                                },
                                onDismiss = { pendingDeleteEntry = entry },
                                resetKey = resetCounter,
                            )
                        }
                    }
                }
            }
        }

        pendingDeleteEntry?.let { entry ->
            val refCount = uiState.referenceCounts[entry.id] ?: 0
            AlertDialog(
                onDismissRequest = {
                    pendingDeleteEntry = null
                    resetCounter++
                },
                title = { Text(stringResource(R.string.action_library_delete_title)) },
                text = {
                    Text(stringResource(
                        if (refCount > 0) R.string.action_library_delete_desc
                        else R.string.action_library_delete_unused_desc,
                        refCount,
                    ))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.remove(entry)
                            pendingDeleteEntry = null
                        },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteEntry = null
                            resetCounter++
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionLibrarySwipeRow(
    modifier: Modifier = Modifier,
    shape: Shape,
    entry: ActionLibraryEntry,
    referenceCount: Int,
    onNavToEdit: () -> Unit,
    onDismiss: () -> Unit,
    resetKey: Int = 0,
) {
    key(entry.id, resetKey) {
        val state = rememberSwipeToDismissBoxState(
            confirmValueChange = { true },
        )

        SwipeToDismissBox(
            modifier = modifier.clip(shape),
            state = state,
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = false,
            gesturesEnabled = true,
            onDismiss = { _ -> onDismiss() },
            backgroundContent = {
                val progress = state.progress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (progress > 0f) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) },
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
            content = {
                ActionLibraryItem(
                    shape = shape,
                    entry = entry,
                    referenceCount = referenceCount,
                    onClick = onNavToEdit,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionLibraryItem(
    shape: Shape,
    entry: ActionLibraryEntry,
    referenceCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clickable(onClick = onClick).clip(shape),
        headlineContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.summary(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Icon(
                entry.type.icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Text(
                text = stringResource(R.string.action_library_reference_count, referenceCount),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = colorScheme.surfaceBright,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = colorScheme.onSurfaceVariant,
            trailingIconColor = colorScheme.onSurfaceVariant,
        ),
    )
}

internal val ActionLibraryType.titleRes: Int get() = when (this) {
    ActionLibraryType.Shell -> R.string.action_library_shell
    ActionLibraryType.Url -> R.string.action_library_url
    ActionLibraryType.Activity -> R.string.action_library_activity
    ActionLibraryType.SystemTemplate -> R.string.action_library_system_function
    ActionLibraryType.SystemApi -> R.string.action_library_custom_system_api
}

private val ActionLibraryType.icon: ImageVector get() = when (this) {
    ActionLibraryType.Shell -> Icons.Default.Terminal
    ActionLibraryType.Url -> Icons.AutoMirrored.Filled.OpenInNew
    ActionLibraryType.Activity -> Icons.Default.Android
    ActionLibraryType.SystemTemplate -> Icons.Default.Build
    ActionLibraryType.SystemApi -> Icons.Default.Code
}

@Composable
private fun ActionLibraryEntry.summary(): String = when (type) {
    ActionLibraryType.Shell -> listOf(
        shellCommand.command.lineSequence().firstOrNull().orEmpty().ifBlank { "Shell" },
        stringResource(if (shellCommand.showToast) R.string.action_library_shell_toast_on else R.string.action_library_shell_toast_off),
    ).joinToString(" · ")
    ActionLibraryType.Url -> buildList {
        add(openAppOrUrl.url.ifBlank { "URL" })
        if (openAppOrUrl.miniWindow) add(stringResource(R.string.open_url_mini_window))
        val enabledParameters = openAppOrUrl.queryParameters.count { it.enabled && it.name.isNotBlank() }
        if (enabledParameters > 0) add(stringResource(R.string.action_library_url_parameter_count, enabledParameters))
    }.joinToString(" · ")
    ActionLibraryType.Activity -> listOf(openAppOrUrl.packageName, openAppOrUrl.activityClassName)
        .filter { it.isNotBlank() }
        .joinToString("/")
        .ifBlank { stringResource(R.string.action_library_activity_empty) }
    ActionLibraryType.SystemTemplate -> systemTemplate.templateId.ifBlank { "Template" }
    ActionLibraryType.SystemApi -> listOf(
        systemApi.command.lineSequence().firstOrNull().orEmpty().ifBlank { "API" },
        stringResource(if (systemApi.showToast) R.string.action_library_shell_toast_on else R.string.action_library_shell_toast_off),
    ).joinToString(" · ")
}

internal fun defaultActionLibraryName(type: ActionLibraryType, entries: List<ActionLibraryEntry>): String {
    val count = entries.count { it.type == type } + 1
    val res = when (type) {
        ActionLibraryType.Shell -> R.string.action_library_default_shell
        ActionLibraryType.Url -> R.string.action_library_default_url
        ActionLibraryType.Activity -> R.string.action_library_default_activity
        ActionLibraryType.SystemTemplate -> R.string.action_library_default_system_function
        ActionLibraryType.SystemApi -> R.string.action_library_default_custom_api
    }
    return AppContext.get().getString(res, count)
}
