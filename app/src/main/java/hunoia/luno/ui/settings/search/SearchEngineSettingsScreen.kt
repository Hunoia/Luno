package hunoia.luno.ui.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.BuiltinSearchEngineIds
import hunoia.luno.config.model.BuiltinSearchEngines
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.liquidGlassBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by ConfigProvider.quickSearchSettings.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SearchEngine?>(null) }
    var deleteTarget by remember { mutableStateOf<SearchEngine?>(null) }
    val engines = settings.effectiveEngines()

    fun setDefault(id: String) {
        scope.launch { ConfigProvider.updateQuickSearchSettings { it.copy(defaultEngineId = id) } }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = { TopBar(onBack = onBack, title = stringResource(R.string.search_engine_settings), scrollBehavior = scrollBehavior, backdrop = backdrop) }
    ) { padding ->
        MyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topPadding = padding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            SegmentedGroup(contentSpacing = SegmentedGap) {
                engines.forEach { engine ->
                    val isDefault = engine.id == settings.defaultEngineId
                    val locked = engine.id in BuiltinSearchEngineIds
                    SegmentedSettingsRow(
                        title = engine.name,
                        subtitle = engine.urlTemplate,
                        onClick = { setDefault(engine.id) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isDefault,
                                    onClick = { setDefault(engine.id) },
                                )
                                if (!locked) {
                                    IconButton(onClick = { editTarget = engine }) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                    IconButton(onClick = { deleteTarget = engine }) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                }
                            }
                        },
                    )
                }
            }
            SegmentedGroup(contentSpacing = SegmentedGap) {
                SegmentedSettingsRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.search_engine_add),
                    onClick = { showAdd = true },
                )
            }
        }
    }

    if (showAdd || editTarget != null) {
        EngineEditDialog(
            initial = editTarget,
            onDismiss = { showAdd = false; editTarget = null },
            onSave = { name, template ->
                scope.launch {
                    ConfigProvider.updateQuickSearchSettings { current ->
                        val target = editTarget
                        if (target == null) {
                            current.copy(
                                customEngines = current.customEngines +
                                    SearchEngine(name = name, urlTemplate = template),
                            )
                        } else {
                            current.copy(
                                customEngines = current.customEngines.map {
                                    if (it.id == target.id) it.copy(name = name, urlTemplate = template) else it
                                },
                            )
                        }
                    }
                }
                showAdd = false
                editTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.search_engine_delete_title)) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            ConfigProvider.updateQuickSearchSettings { current ->
                                current.copy(
                                    customEngines = current.customEngines.filterNot { it.id == target.id },
                                    defaultEngineId = if (current.defaultEngineId == target.id) {
                                        BuiltinSearchEngines.first().id
                                    } else {
                                        current.defaultEngineId
                                    },
                                )
                            }
                        }
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun EngineEditDialog(
    initial: SearchEngine?,
    onDismiss: () -> Unit,
    onSave: (name: String, template: String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var template by remember(initial) { mutableStateOf(initial?.urlTemplate.orEmpty()) }
    val valid = name.isNotBlank() && template.contains("%s")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.search_engine_add else R.string.search_engine_edit,
                ),
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_engine_name)) },
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_engine_url)) },
                    placeholder = { Text("https://www.bing.com/search?q=%s") },
                    isError = template.isNotEmpty() && !template.contains("%s"),
                    supportingText = {
                        if (template.isNotEmpty() && !template.contains("%s")) {
                            Text(stringResource(R.string.search_engine_url_error))
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name.trim(), template.trim()) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
