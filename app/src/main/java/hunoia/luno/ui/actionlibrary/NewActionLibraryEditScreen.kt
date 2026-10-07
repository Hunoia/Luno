package hunoia.luno.ui.actionlibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.toMiniWindowSettings
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.query.ActivityOption
import hunoia.luno.ui.component.sheets.AppPickerSheet
import hunoia.luno.ui.component.sheets.ButtonPickerSheet
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.core.AppContext
import hunoia.luno.service.DispatchClient
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.feedback.LunoIconButton
/**
 * 依赖真实触点的动作：分发时必须带触点坐标，独立试执行必然报参数无效，
 * 所以这两种类型下测试按钮置灰（见 accessibility.tap / longPress 的分发前置校验）。
 */
private val TOUCH_ONLY_TYPES = setOf("accessibility.tap", "accessibility.longPress")
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
    var showButtonPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
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
    LunoScreen(
        title = stringResource(
            if (draft?.id == null) R.string.action_library_add else R.string.action_library_edit,
        ),
        onBack = guardedBack,
        actions = {                     if (draft?.id != null) {
                        LunoIconButton(
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = { showDeleteConfirm = true },
                        )
                    }
                    TextButton(
                        enabled = draft?.isValid == true && !state.isSaving,
                        onClick = { vm.save(onSaved = onBack) },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                 },
        dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.action_library_delete_title),
                    text = stringResource(R.string.action_editor_delete_desc),
                    confirmLabel = stringResource(R.string.delete),
                    destructive = true,
                    onDismissRequest = { showDeleteConfirm = false },
                    onConfirm = {
                        vm.delete()
                        showDeleteConfirm = false
                        onBack()
                    },
                )
            }
            if (showDiscardConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.action_library_discard_title),
                    text = stringResource(R.string.action_library_discard_desc),
                    onDismissRequest = { showDiscardConfirm = false },
                    onConfirm = { onBack() },
                )
            }
            if (showTypePicker) {
                TypePickerSheet(
                    onDismissRequest = { showTypePicker = false },
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
            showButtonPicker?.let { paramDef ->
                val buttons by GestureConfig.buttons
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val selectedIds = draft?.params?.get(paramDef.key)?.jsonArray?.mapNotNull {
                    it.jsonPrimitive?.contentOrNull
                } ?: emptyList()
                ButtonPickerSheet(
                    onDismissRequest = { showButtonPicker = null },
                    buttons = buttons,
                    selectedIds = selectedIds,
                    onConfirm = { selected ->
                        vm.updateParamMulti(paramDef.key, selected)
                    },
                )
            }
            showMiniWindowPicker?.let { paramDef ->
                LaunchedEffect(paramDef.key) {
                    val initial = draft?.params?.get(paramDef.key)?.toMiniWindowSettings() ?: MiniWindowSettings()
                    DispatchClient.showMiniWindowAdjust(
                        initial = initial,
                        onCommit = { result ->
                            vm.updateParamMiniWindow(paramDef.key, result)
                            showMiniWindowPicker = null
                        },
                        onDismiss = { showMiniWindowPicker = null },
                    )
                }
            }
            showActivityPicker?.let { paramDef ->
                val activityDef = paramDef as? ParameterDefinition.ActivitySelector ?: return@let
                ActivityPickerSheet(
                    packageName = draft?.params?.get(activityDef.packageKey)?.scalarText() ?: "",
                    selectedActivity = draft?.params?.get(activityDef.key)?.scalarText() ?: "",
                    onDismissRequest = { showActivityPicker = null },
                    onSelect = { className ->
                        vm.updateParam(activityDef.key, className)
                        showActivityPicker = null
                    },
                )
            }

        },
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
                            LunoGroup(
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
                                        onPickButtons = { showButtonPicker = paramDef },
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
                                onPickButtons = { showButtonPicker = paramDef },
                                onPickMiniWindow = { showMiniWindowPicker = paramDef },
                                onPickActivity = { _, _ -> showActivityPicker = paramDef },
                            )
                        }
                    }
                    // 测试/保存并排：测试只要求参数有效，不写盘；保存沿用顶部同一入口。
                    val testable = draft.typeId !in TOUCH_ONLY_TYPES
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            enabled = draft.isValid && testable && !state.isSaving,
                            onClick = {
                                if (DispatchClient.dispatch(draft.toStoredAction())) {
                                    showToast(R.string.action_library_test_sent)
                                } else {
                                    showToast(R.string.dispatch_service_not_ready)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.action_library_test))
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            enabled = draft.isValid && !state.isSaving,
                            onClick = { vm.save(onSaved = onBack) },
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        
    }
}
@Composable
private fun TypeSelectorField(
    definition: ActionDefinition,
    onClick: () -> Unit,
) {
    LunoSettingsRow(
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
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    LaunchedEffect(packageName) {
        if (packageName.isBlank()) onDismissRequest()
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
    LunoSheet(onDismissRequest = onDismissRequest) {
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
                val selectionFlags = filtered.map { it.className == selectedActivity }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetListMaxHeight)
                        .padding(top = CardInnerSpacing),
                    contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = CardInnerSpacing),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    itemsIndexed(filtered) { index, activity ->
                        LunoSelectableRow(
                            title = QuickLaunchFacade.formatActivityOptionText(activity, packageName),
                            subtitle = activity.className,
                            selected = activity.className == selectedActivity,
                            onSelectedChange = { onSelect(activity.className) },
                            singleSelect = true,
                            shape = selectedSegmentedShape(index, filtered.size, selectionFlags),
                        )
                    }
                }
            }
        }
    }
}