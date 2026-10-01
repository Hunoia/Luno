package hunoia.luno.ui.actionlibrary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.ui.theme.liquidGlassBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.toMiniWindowSettings
import hunoia.luno.ui.component.MiniWindowControls
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.query.ActivityOption
import hunoia.luno.ui.component.AppPickerSheet
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.core.AppContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var showAppMultiPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showMiniWindowPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(entryId, typeId) {
        vm.load(entryId, typeId)
    }

    val draft = state.draft
    val definition = draft?.definition
    val guardedBack: () -> Unit = {
        if (state.isDirty) showDiscardConfirm = true else onBack()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopBar(
                onBack = guardedBack,
                title = stringResource(
                    if (draft?.id == null) R.string.action_library_add else R.string.action_library_edit,
                ),
                actions = {
                    if (draft?.id != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                    TextButton(
                        enabled = draft?.isValid == true && !state.isSaving,
                        onClick = { vm.save(onSaved = onBack) },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
            )
        },
    ) { scaffoldPadding ->
        MyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .animateContentSize(),
            topPadding = scaffoldPadding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            when {
                state.notFound -> EmptyState(stringResource(R.string.action_library_entry_not_found))
                draft == null || definition == null -> EmptyState(stringResource(R.string.action_library_empty))
                else -> {
                    TypeSelectorField(
                        definition = definition,
                        onClick = { showTypePicker = true },
                    )

                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { vm.updateName(it) },
                        label = { Text(stringResource(R.string.action_library_entry_name)) },
                        placeholder = { Text(stringResource(R.string.action_library_entry_name_hint)) },
                        singleLine = true,
                        isError = draft.name.isBlank(),
                        shape = LargeShape,
                        supportingText = {
                            if (draft.name.isBlank()) {
                                Text(stringResource(R.string.action_library_entry_name_required))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (definition.parameters.isNotEmpty()) {
                        val cardParams = definition.parameters.filter { it.isCardRowType }
                        val fieldParams = definition.parameters.filter { !it.isCardRowType }

                        if (cardParams.isNotEmpty()) {
                            SegmentedGroup(
                                title = stringResource(R.string.action_param_label),
                                subtitle = stringResource(R.string.action_param_count, definition.parameters.size),
                            ) {
                                cardParams.forEachIndexed { i, paramDef ->
                                    ParameterEditor(
                                        definition = paramDef,
                                        currentParams = draft.params,
                                        onParamChange = vm::updateParam,
                                        onPickApp = { showAppPicker = paramDef },
                                        onPickApps = { showAppMultiPicker = paramDef },
                                        onPickMiniWindow = { showMiniWindowPicker = paramDef },
                                        onPickActivity = { _, _ -> showActivityPicker = paramDef },
                                        shape = segmentedShape(i, cardParams.size),
                                    )
                                }
                            }
                        }

                        fieldParams.forEach { paramDef ->
                            ParameterEditor(
                                definition = paramDef,
                                currentParams = draft.params,
                                onParamChange = vm::updateParam,
                                onPickApp = { showAppPicker = paramDef },
                                onPickApps = { showAppMultiPicker = paramDef },
                                onPickMiniWindow = { showMiniWindowPicker = paramDef },
                                onPickActivity = { _, _ -> showActivityPicker = paramDef },
                            )
                        }
                    }
                }
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

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.action_library_discard_title)) },
            text = { Text(stringResource(R.string.action_library_discard_desc)) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
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
            selectedPackageNames = listOfNotNull(
                draft?.params?.get(paramDef.key)?.scalarText()?.takeIf(String::isNotBlank),
            ),
            onConfirm = { selected ->
                val current = draft?.params?.get(paramDef.key)?.scalarText()
                val picked = selected.lastOrNull { it != current }
                vm.updateParam(
                    paramDef.key,
                    picked ?: if (selected.isEmpty()) "" else current.orEmpty(),
                )
                showAppPicker = null
            },
        )
    }

    showAppMultiPicker?.let { paramDef ->
        val multiSelected = draft?.params?.get(paramDef.key)?.jsonArray?.mapNotNull {
            it.jsonPrimitive?.contentOrNull
        } ?: emptyList()
        AppPickerSheet(
            onDismissRequest = { showAppMultiPicker = null },
            selectedPackageNames = multiSelected,
            onConfirm = { selected ->
                vm.updateParamMulti(paramDef.key, selected)
                showAppMultiPicker = null
            },
        )
    }

    showMiniWindowPicker?.let { paramDef ->
        val initial = draft?.params?.get(paramDef.key)?.toMiniWindowSettings() ?: MiniWindowSettings()
        var localSettings by remember(paramDef.key) { mutableStateOf(initial) }
        ModalBottomSheet(
            onDismissRequest = { showMiniWindowPicker = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = SheetTopShape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
                Text(
                    text = stringResource(R.string.mini_window_position_short),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter),
                    verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                ) {
                    MiniWindowControls(
                        settings = localSettings,
                        onChange = { localSettings = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showMiniWindowPicker = null },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.updateParamMiniWindow(paramDef.key, localSettings)
                            showMiniWindowPicker = null
                        },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }

    showActivityPicker?.let { paramDef ->
        val activityDef = paramDef as? ParameterDefinition.ActivitySelector ?: return@let
        ActivityPickerSheet(
            packageName = draft?.params?.get(activityDef.packageKey)?.scalarText() ?: "",
            selectedActivity = draft?.params?.get(activityDef.key)?.scalarText() ?: "",
            onDismiss = { showActivityPicker = null },
            onSelect = { className ->
                vm.updateParam(activityDef.key, className)
                showActivityPicker = null
            },
        )
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
    LaunchedEffect(packageName) {
        if (packageName.isBlank()) onDismiss()
    }

    val activities by produceState<List<ActivityOption>>(emptyList(), packageName) {
        if (packageName.isBlank()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val context = AppContext.get()
            QuickLaunchFacade.queryActivityOptions(
                context = context,
                packageName = packageName,
                selectedActivityClassName = "",
                launcherClassName = QuickLaunchFacade.queryLauncherAppOptions(context)
                    .firstOrNull { it.packageName == packageName }?.launcherClassName ?: ""
            )
        }
    }

    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) activities
    else activities.filter {
        it.className.contains(query, ignoreCase = true) ||
            QuickLaunchFacade.formatActivityOptionText(it, packageName).contains(query, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            Text(
                text = stringResource(R.string.select_activity_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
            )
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_activity_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
            )
            if (filtered.isEmpty()) {
                EmptyState(message = stringResource(R.string.no_matching_results))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetListMaxHeight)
                        .padding(top = CardInnerSpacing),
                    contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = CardInnerSpacing),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    itemsIndexed(filtered) { index, activity ->
                        SelectableListItem(
                            title = QuickLaunchFacade.formatActivityOptionText(activity, packageName),
                            subtitle = activity.className,
                            selected = activity.className == selectedActivity,
                            onSelect = { onSelect(activity.className) },
                            showCheckbox = true,
                            shape = segmentedShape(index, filtered.size),
                            verticalGap = 0.dp,
                            horizontalGap = 0.dp,
                        )
                    }
                }
            }
        }
    }
}
