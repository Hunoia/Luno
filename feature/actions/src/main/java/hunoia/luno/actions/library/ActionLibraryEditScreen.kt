package hunoia.luno.actions.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import hunoia.luno.runtime.dispatch.ActionDispatch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import hunoia.luno.ui.component.LunoActionBar
import hunoia.luno.ui.component.LunoActionBarItem
import hunoia.luno.ui.component.page.LunoPage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.actions.R
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.runtime.dispatch.ActivityOptions
import hunoia.luno.core.domain.ActivityOptionItem
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.shizuku.ShizukuFacade
import hunoia.luno.shizuku.ShellResult
import hunoia.luno.ui.theme.raisedSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.TileInnerPadding
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLibraryEditScreen(
    entryId: String,
    typeId: String?,
    onBack: () -> Unit,
    /** 应用选择器由 `:app` 注入（含分身 userId 语义）。 */
    appPicker: AppPickerSlot? = null,
    vm: ActionLibraryEditVM = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    var showAppPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showActivityPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var shellResult by remember { mutableStateOf<ShellResult?>(null) }
    var shellRunning by remember { mutableStateOf(false) }
    LaunchedEffect(entryId, typeId) {
        shellResult = null
        shellRunning = false
        vm.load(entryId, typeId)
    }
    val draft = state.draft
    val definition = draft?.definition
    val guardedBack: () -> Unit = {
        if (state.isDirty) showDiscardConfirm = true else onBack()
    }
    LunoPage(
        title = stringResource(
            if (draft?.id == null) R.string.action_library_add else R.string.action_library_edit,
        ),
        onBack = guardedBack,
        bottomBar = { hazeState ->
            if (draft != null) {
                LunoActionBar(hazeState) {
                    // 次要：试跑（shell 命令直接执行，结果卡浮在底栏上方）；主操作：保存。
                    LunoActionBarItem(
                        onClick = {
                            if (draft.typeId == "shell.custom") {
                                val command = draft.params["command"]?.scalarText().orEmpty().trim()
                                when {
                                    command.isBlank() -> showToast(R.string.action_library_invalid_hint)
                                    !ShizukuFacade.isReady() -> showToast(R.string.toast_shizuku_required)
                                    else -> {
                                        shellRunning = true
                                        scope.launch {
                                            shellResult = ShizukuFacade.runShellCommand(command)
                                            shellRunning = false
                                        }
                                    }
                                }
                            } else {
                                scope.launch {
                                    if (!ActionDispatch.send(draft.toStoredAction())) {
                                        showToast(hunoia.luno.design.R.string.dispatch_service_not_ready)
                                    }
                                }
                            }
                        },
                        icon = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.action_library_test),
                        enabled = draft.isValid && !state.isSaving && !shellRunning,
                    )
                    LunoActionBarItem(
                        onClick = {
                            if (draft.isValid) {
                                vm.save(onSaved = onBack)
                            } else {
                                showToast(R.string.action_library_invalid_hint)
                            }
                        },
                        icon = Icons.Default.Save,
                        contentDescription = stringResource(hunoia.luno.design.R.string.save),
                    )
                }
            }
            },
        floatingContent = { hazeState ->
            if (draft != null) {
                if (draft.typeId == "shell.custom" && (shellResult != null || shellRunning)) {
                    ShellResultCard(
                        loading = shellRunning,
                        result = shellResult,
                        onDismiss = { shellResult = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                        // 底部抬到动作底栏上方：底栏高 = BottomBarHeight + 玻璃胶囊内边距，加一段间隙。
                        .padding(
                            start = PageGutter,
                            end = PageGutter,
                            bottom = BottomBarPadding + BottomBarHeight + TileInnerPadding * 2 + ListSpacing,
                        )
                        .fillMaxWidth(),
                    )
                }
            }
        },
        actions = {
            if (draft?.id != null) {
                LunoIconButton(
                    icon = Icons.Outlined.Delete,
                    contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                    onClick = { showDeleteConfirm = true },
                )
            }
        },
        dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.action_library_delete_title),
                    text = stringResource(R.string.action_editor_delete_desc),
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
            showAppPicker?.let { paramDef ->
                val picker = appPicker
                if (picker == null) {
                    showAppPicker = null
                } else {
                    picker(
                        AppPickerRequest(
                            selectedPackageNames = listOfNotNull(
                                draft?.params?.get(paramDef.key)?.scalarText()?.takeIf(String::isNotBlank),
                            ),
                            selectedUserId = (paramDef as? ParameterDefinition.AppSelector)
                            ?.takeIf { it.userIdKey != null }
                            ?.let {
                                draft?.params?.get("userId")?.scalarText()?.toIntOrNull() ?: 0
                            },
                            onDismiss = { showAppPicker = null },
                            onConfirm = { selected ->
                                val current = draft?.params?.get(paramDef.key)?.scalarText()
                                val picked = selected.lastOrNull { it != current }
                                vm.updateParam(
                                    paramDef.key,
                                    picked ?: if (selected.isEmpty()) "" else current.orEmpty(),
                                )
                                showAppPicker = null
                            },
                            onSelect = (paramDef as? ParameterDefinition.AppSelector)
                            ?.takeIf { it.userIdKey != null }
                            ?.let { selector ->
                                { packageName, userId ->
                                    vm.updateParam(selector.key, packageName)
                                    selector.userIdKey?.let {
                                        vm.updateParam(it, if (userId == 0) "" else userId.toString())
                                    }
                                    showAppPicker = null
                                }
                            },
                        ),
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
        column() {
            block {
                when {
                    state.notFound -> EmptyState(stringResource(R.string.action_library_entry_not_found))
                    draft == null || definition == null ->
                    LoadingState(message = stringResource(R.string.action_library_loading))
                    else -> {
                        // 名称：页首浮空组，行内形式（标签「名称」+ 值右对齐）。
                        group {
                            inputRow(
                                label = stringResource(R.string.action_library_name),
                                value = draft.name,
                                onValueChange = { vm.updateName(it) },
                                placeholder = stringResource(R.string.action_library_entry_name_hint),
                                isError = draft.name.isBlank(),
                                subtitle = if (draft.name.isBlank()) {
                                    stringResource(R.string.action_library_entry_name_required)
                                } else {
                                    ""
                                },
                            )
                        }
                        if (definition.parameters.isNotEmpty()) {
                            // 开关（小窗模式这类）放参数组最上面，其余非键值对按定义序；
                            // 键值对（extras / query）单独一组「额外参数」。
                            val switches = definition.parameters.filterIsInstance<ParameterDefinition.Bool>()
                            val otherParams = definition.parameters.filter {
                                it !is ParameterDefinition.Bool && it !is ParameterDefinition.KeyValueMap
                            }
                            val extraParams = definition.parameters.filterIsInstance<ParameterDefinition.KeyValueMap>()
                            val mainParams = switches + otherParams
                            if (mainParams.isNotEmpty()) {
                                LunoGroup(title = stringResource(R.string.action_param_label)) {
                                    mainParams.forEachIndexed { i, paramDef ->
                                        ParameterEditor(
                                            definition = paramDef,
                                            currentParams = draft.params,
                                            onParamChange = vm::updateParam,
                                            onParamElementChange = vm::updateParamElement,
                                            onPickApp = { showAppPicker = paramDef },
                                            onPickActivity = { _, _ -> showActivityPicker = paramDef },
                                            shape = segmentedShape(i, mainParams.size),
                                        )
                                    }
                                }
                            }
                            if (extraParams.isNotEmpty()) {
                                // 组标题用参数自身的 label（如 Query 参数 / Extras）；每行分段由
                                // KeyValueMapEditor 内部按行数自算。
                                extraParams.forEach { paramDef ->
                                    LunoGroup(title = paramDef.label) {
                                        ParameterEditor(
                                            definition = paramDef,
                                            currentParams = draft.params,
                                            onParamChange = vm::updateParam,
                                            onParamElementChange = vm::updateParamElement,
                                            onPickApp = { showAppPicker = paramDef },
                                            onPickActivity = { _, _ -> showActivityPicker = paramDef },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
    val activities by produceState<List<ActivityOptionItem>>(emptyList(), packageName) {
        val source = ActivityOptions.current
        if (packageName.isBlank() || source == null) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            source.activities(
                packageName = packageName,
                launcherActivity = source.launcherPackageActivity(packageName),
            )
        }
    }
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) activities
    else activities.filter {
        it.className.contains(query, ignoreCase = true) ||
            it.label.contains(query, ignoreCase = true)
    }
    LunoSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            Text(
                text = stringResource(R.string.select_activity_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = CardInnerSpacing),
            )
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_activity_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CardInnerSpacing),
            )
            if (filtered.isEmpty()) {
                EmptyState(message = stringResource(hunoia.luno.design.R.string.no_matching_results))
            } else {
                val selectionFlags = filtered.map { it.className == selectedActivity }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetListMaxHeight)
                        .padding(top = CardInnerSpacing),
                    contentPadding = PaddingValues(bottom = CardInnerSpacing),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    itemsIndexed(filtered, key = { _, activity -> activity.className }) { index, activity ->
                        LunoSelectableRow(
                            title = activity.label,
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

/** 只吃事件、不做任何事：把落在工具条矩形范围内的触摸从下层列表手里拿回来。 */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}

/** 自定义命令测试输出：浮在页面底部（FAB 上方），可滚动查看。 */
@Composable
private fun ShellResultCard(
    loading: Boolean,
    result: ShellResult?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = LargeShape,
        color = raisedSurfaceColor(),
        tonalElevation = ElevationSmall,
        shadowElevation = ElevationSmall,
    ) {
        Column(modifier = Modifier.padding(PageGutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.action_shell_result),
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        loading -> MaterialTheme.colorScheme.onSurfaceVariant
                        result?.isSuccess == true -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f),
                )
                LunoIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(hunoia.luno.design.R.string.close),
                    onClick = onDismiss,
                )
            }
            if (loading) {
                Text(
                    text = stringResource(R.string.action_shell_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = result?.let { res ->
                        val raw = if (res.isSuccess) {
                            res.stdout.ifBlank { res.stderr }
                        } else {
                            res.errorMessage.ifBlank { res.stdout.ifBlank { res.stderr } }
                        }
                        raw.takeIf { it.isNotBlank() }
                    } ?: stringResource(R.string.action_shell_no_output),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
