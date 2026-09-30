package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.liquidGlassBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.SegmentedSettingsRow

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
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(entryId, typeId) {
        vm.load(entryId, typeId)
    }

    val draft = state.draft ?: return
    val definition = draft.definition ?: return

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(if (draft.isNew) R.string.action_library_add else R.string.action_library_edit),
                actions = {
                    if (!draft.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
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
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding)
                .padding(horizontal = PageGutter),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
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
                text = stringResource(
                    R.string.action_type_meta_format,
                    stringResource(definition.category.displayNameRes),
                    definition.parameters.size,
                ),
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.action_library_delete_title)) },
            text = { Text(stringResource(R.string.action_editor_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete()
                        showDeleteConfirm = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
    SegmentedSettingsRow(
        title = definition.name,
        subtitle = stringResource(definition.category.displayNameRes),
        onClick = onClick,
    )
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
        Column(modifier = Modifier.padding(vertical = CardInnerSpacing)) {
            Text(
                text = stringResource(R.string.select_activity_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = PageGutter)
                    .padding(bottom = ListItemVerticalPadding),
            )
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_activity_hint),
                modifier = Modifier
                    .padding(horizontal = PageGutter)
                    .padding(bottom = CardInnerSpacing),
            )
            if (filtered.isEmpty()) {
                EmptyState(message = stringResource(R.string.no_matching_results))
            } else {
                filtered.forEach { activity ->
                    SelectableListItem(
                        title = QuickLaunchFacade.formatActivityOptionText(activity, packageName),
                        subtitle = activity.className,
                        selected = activity.className == selectedActivity,
                        onSelect = { onSelect(activity.className) },
                        showCheckbox = true,
                    )
                }
            }
        }
    }
}
