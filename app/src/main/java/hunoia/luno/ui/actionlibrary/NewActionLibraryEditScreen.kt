package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.ui.component.AppPickerSheet
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.OptimizedBottomSheet
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.core.AppContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewActionLibraryEditScreen(
    entryId: String,
    typeId: String?,
    onBack: () -> Unit,
    vm: NewActionLibraryEditVM = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    var showTypePicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showActivityPicker by remember { mutableStateOf<ParameterDefinition?>(null) }

    LaunchedEffect(entryId, typeId) {
        vm.load(entryId, typeId)
    }

    val draft = state.draft ?: return
    val definition = draft.definition ?: return

    Scaffold(
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(if (draft.isNew) R.string.action_library_add else R.string.action_library_edit),
                actions = {
                    TextButton(
                        enabled = draft.isValid,
                        onClick = {
                            vm.save()
                            onBack()
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaffoldPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypeSelectorField(
                definition = definition,
                onClick = { showTypePicker = true },
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { vm.updateName(it) },
                label = { Text(stringResource(R.string.action_library_entry_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "${definition.category} · ${definition.parameters.size} 个参数",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            definition.parameters.forEach { paramDef ->
                ParameterEditor(
                    definition = paramDef,
                    currentParams = draft.params,
                    onParamChange = vm::updateParam,
                    onPickApp = { showAppPicker = paramDef },
                    onPickActivity = { _, _ -> showActivityPicker = paramDef },
                )
            }
        }
    }

    if (showTypePicker) {
        TypePickerSheet(
            onDismiss = { showTypePicker = false },
            onSelectType = { def ->
                vm.selectType(def.typeId)
                showTypePicker = false
            },
        )
    }

    showAppPicker?.let { paramDef ->
        AppPickerSheet(
            onDismissRequest = { showAppPicker = null },
            selectedPackageNames = listOfNotNull(draft.params[paramDef.key]?.takeIf { it.isNotBlank() }),
            onConfirm = { selected ->
                if (selected.isNotEmpty()) {
                    vm.updateParam(paramDef.key, selected.first())
                }
                showAppPicker = null
            },
        )
    }

    showActivityPicker?.let { paramDef ->
        val packageName = draft.params["packageName"] ?: ""
        if (packageName.isBlank()) {
            showActivityPicker = null
        } else {
            ActivityPickerSheet(
                packageName = packageName,
                selectedActivity = draft.params["activityClassName"] ?: "",
                onDismiss = { showActivityPicker = null },
                onSelect = { className ->
                    vm.updateParam("activityClassName", className)
                    showActivityPicker = null
                },
            )
        }
    }
}

@Composable
private fun TypeSelectorField(
    definition: ActionDefinition,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        Text(
            text = "动作类型",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = definition.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = definition.category,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityPickerSheet(
    packageName: String,
    selectedActivity: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = AppContext.get()
    val activities = remember(packageName) {
        QuickLaunchFacade.queryActivityOptions(
            context = context,
            packageName = packageName,
            selectedActivityClassName = "",
            launcherClassName = QuickLaunchFacade.queryLauncherAppOptions(context)
                .firstOrNull { it.packageName == packageName }?.launcherClassName ?: ""
        )
    }

    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) activities
    else activities.filter {
        it.className.contains(query, ignoreCase = true) ||
            QuickLaunchFacade.formatActivityOptionText(it, packageName).contains(query, ignoreCase = true)
    }

    OptimizedBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "选择 Activity",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "搜索 Activity...",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (filtered.isEmpty()) {
                    EmptyState(message = "未找到匹配项")
                } else {
                    filtered.forEach { activity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(activity.className) }
                                .padding(vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = QuickLaunchFacade.formatActivityOptionText(activity, packageName),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = activity.className,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
