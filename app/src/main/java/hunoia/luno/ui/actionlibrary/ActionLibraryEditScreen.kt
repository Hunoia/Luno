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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import hunoia.luno.ui.theme.FloatingContentBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.ui.settings.action.MiniWindowAdjustContent
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.controller.ShellController
import hunoia.luno.action.controller.ShellResult
import hunoia.luno.action.definitions.hasPointParams
import hunoia.luno.action.model.PARAM_X_DP
import hunoia.luno.action.model.PARAM_Y_DP
import hunoia.luno.action.model.POINT_PARAM_KEYS
import hunoia.luno.action.model.USE_TRIGGER_POINT_KEY
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.toMiniWindowSettings
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.query.ActivityOption
import hunoia.luno.ui.component.sheet.AppPickerSheet
import hunoia.luno.ui.component.sheet.ButtonPickerSheet
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.core.AppContext
import hunoia.luno.service.DispatchClient
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * 依赖真实触点的动作：分发时必须带触点坐标；库条目配好固定坐标后可独立试执行，
 * 无坐标（或勾了「使用触发位置坐标」）时测试按钮置灰。
 */
private val TOUCH_ONLY_TYPES = setOf("accessibility.tap", "accessibility.longPress", "accessibility.doubleTap")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLibraryEditScreen(
    entryId: String,
    typeId: String?,
    onBack: () -> Unit,
    vm: ActionLibraryEditVM = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    var showAppPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showActivityPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showAppMultiPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showButtonPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
    var showMiniWindowPicker by remember { mutableStateOf<ParameterDefinition?>(null) }
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
    // 测试门槛：触点类动作必须已配固定坐标且未勾「使用触发位置坐标」。
    val testable = draft?.let { d ->
        val hasTapPoint = d.params[PARAM_X_DP]?.scalarText()?.toIntOrNull() != null &&
            d.params[PARAM_Y_DP]?.scalarText()?.toIntOrNull() != null
        val useTriggerPoint = d.params[USE_TRIGGER_POINT_KEY]?.scalarText()?.toBoolean() == true
        d.typeId !in TOUCH_ONLY_TYPES || (hasTapPoint && !useTriggerPoint)
    } == true
    LunoScreen(
        title = stringResource(
            if (draft?.id == null) R.string.action_library_add else R.string.action_library_edit,
        ),
        onBack = guardedBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = { _ ->
            if (draft != null) {
                // 点击直接保存（无效草稿 toast 提示）。
                LunoFab(
                    onClick = {
                        if (draft.isValid) {
                            vm.save(onSaved = onBack)
                        } else {
                            showToast(R.string.action_library_invalid_hint)
                        }
                    },
                    icon = Icons.Default.Save,
                    contentDescription = stringResource(R.string.save),
                    containerColor = if (draft.isValid) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                if (draft.typeId == "shell.custom" && (shellResult != null || shellRunning)) {
                    ShellResultCard(
                        loading = shellRunning,
                        result = shellResult,
                        onDismiss = { shellResult = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        },
        actions = {
            if (draft != null) {
                // 测试放右上角：触点类动作必须已配固定坐标。
                LunoIconButton(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.action_library_test),
                    enabled = draft.isValid && testable && !state.isSaving && !shellRunning,
                    onClick = {
                        // shell 命令在编辑页直接跑一次，把 stdout/stderr 显示在底部面板，
                        // 不走分发（分发拿不到结果，只能 toast）。
                        if (draft.typeId == "shell.custom") {
                            val command = draft.params["command"]?.scalarText().orEmpty().trim()
                            when {
                                command.isBlank() -> showToast(R.string.action_library_invalid_hint)
                                !ShizukuFacade.isReady() -> showToast(R.string.toast_shizuku_required)
                                else -> {
                                    shellRunning = true
                                    scope.launch {
                                        shellResult = ShellController().runCommand(command)
                                        shellRunning = false
                                    }
                                }
                            }
                        } else if (!DispatchClient.dispatch(draft.toStoredAction())) {
                            // 分发失败才提示；成功不给 toast（动作本身会在屏幕上有反馈，多一个弹窗是噪音）。
                            showToast(R.string.dispatch_service_not_ready)
                        }
                    },
                )
            }
            if (draft?.id != null) {
                LunoIconButton(
                    icon = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    onClick = { showDeleteConfirm = true },
                )
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
            showAppPicker?.let { paramDef ->
                AppPickerSheet(
                    onDismissRequest = { showAppPicker = null },
                    selectedPackageNames = listOfNotNull(
                        draft?.params?.get(paramDef.key)?.scalarText()?.takeIf(String::isNotBlank),
                    ),
                    confirmOnSelect = true,
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
                // 全屏对话框而不是另开路由：结果要回填给当前编辑中的动作，
                // 走路由就得跨 nav entry 传参，这里就地拿到 vm 最直接。
                val initial = draft?.params?.get(paramDef.key)?.toMiniWindowSettings() ?: MiniWindowSettings()
                Dialog(
                    onDismissRequest = { showMiniWindowPicker = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    MiniWindowAdjustContent(
                        initial = initial,
                        onCommit = { result ->
                            vm.updateParamMiniWindow(paramDef.key, result)
                            showMiniWindowPicker = null
                        },
                        onCancel = { showMiniWindowPicker = null },
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
                    val useTriggerPoint =
                        draft.params[USE_TRIGGER_POINT_KEY]?.scalarText()?.toBoolean() == true
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
                                        onParamElementChange = vm::updateParamElement,
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
                            // 勾了「使用触发位置坐标」：固定坐标整组（含屏幕取点入口）不显示。
                            if (useTriggerPoint && paramDef.key in POINT_PARAM_KEYS) return@forEach
                            // 坐标参数（tap/longPress）：首个数字框前插屏幕取点入口，
                            // 取点经服务侧 overlay，回填后仍可在数字框里手改。
                            if (paramDef.key == PARAM_X_DP && definition.hasPointParams()) {
                                val curX = draft.params[PARAM_X_DP]?.scalarText()?.toIntOrNull()
                                val curY = draft.params[PARAM_Y_DP]?.scalarText()?.toIntOrNull()
                                LunoSettingsRow(
                                    title = stringResource(R.string.point_pick),
                                    subtitle = if (curX != null && curY != null) {
                                        stringResource(R.string.point_pick_point, curX, curY)
                                    } else {
                                        stringResource(R.string.point_pick_empty)
                                    },
                                    onClick = {
                                        // 取点窗口盖在别的应用上面，只能由 :service 的无障碍服务加
                                        // （TYPE_ACCESSIBILITY_OVERLAY 只有 AccessibilityService 能
                                        // addView；改用 TYPE_APPLICATION_OVERLAY 需要引入一个本来
                                        // 不必有的「显示在其他应用上层」权限）。见 DispatchService。
                                        val ok = DispatchClient.showPointPicker(
                                            initialXdp = curX,
                                            initialYdp = curY,
                                            onCommit = { xDp, yDp ->
                                                vm.updateParam(PARAM_X_DP, xDp.toString())
                                                vm.updateParam(PARAM_Y_DP, yDp.toString())
                                            },
                                            onDismiss = {},
                                        )
                                        if (!ok) showToast(R.string.dispatch_service_not_ready)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ParameterEditor(
                                definition = paramDef,
                                currentParams = draft.params,
                                onParamChange = vm::updateParam,
                                onParamElementChange = vm::updateParamElement,
                                onPickApp = { showAppPicker = paramDef },
                                onPickApps = { showAppMultiPicker = paramDef },
                                onPickButtons = { showButtonPicker = paramDef },
                                onPickMiniWindow = { showMiniWindowPicker = paramDef },
                                onPickActivity = { _, _ -> showActivityPicker = paramDef },
                            )
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.action_shell_result),
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        loading -> MaterialTheme.colorScheme.onSurfaceVariant
                        result?.success == true -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f),
                )
                LunoIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
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
                        val raw = if (res.success) {
                            res.output.ifBlank { res.error.orEmpty() }
                        } else {
                            res.error ?: res.output
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
