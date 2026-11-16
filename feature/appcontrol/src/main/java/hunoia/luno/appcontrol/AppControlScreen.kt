package hunoia.luno.appcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.quickaccess.icon.AppIconRequests
import hunoia.luno.quickaccess.query.AppAvailability
import hunoia.luno.ui.component.LunoSearchField
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.DockGutter
import hunoia.luno.ui.theme.DockTabHeight
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.glassCapsule

/**
 * 应用冻结管理页：冻结 / 解冻、一键名单、批量操作与失败回执。
 *
 * 默认只列「已冻结 ∪ 一键名单」；搜索时扩到全机（系统包跟随全局「显示系统应用」开关）。
 * 系统 suspend 只读展示；自己与当前桌面禁止冻结（由 PackageControl 兜底）。
 *
 * 底栏玻璃胶囊工具条（参考剪贴板页 [hunoia.luno.clipboard.ClipboardScreen]）：
 * 常态 [添加(一键名单)][搜索]、搜索 [输入框][收起×]、选择 [已选 N][全选][冻结][解冻]；
 * 胶囊随内容自适应、靠右，搜索时展开成全宽。搜索 / 选择互斥（VM 负责）。
 * 行点按不触发冻结（1B），冻结 / 解冻只走行尾雪花 / 解锁图标。
 */
@Composable
fun AppControlScreen(
    onBack: () -> Unit,
    appPicker: AppPickerSlot? = null,
    vm: AppControlVM = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { 40.dp.toPx().toInt() }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { showToast(it) }
    }

    val visible = remember(state.apps, state.query) {
        val query = state.query.trim()
        val filtered = if (query.isEmpty()) {
            state.apps.filter {
                it.availability != AppAvailability.ENABLED || it.inOneKey
            }
        } else {
            state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        filtered.sortedWith(
            compareBy<ManagedApp> {
                when (it.availability) {
                    AppAvailability.DISABLED -> 0
                    AppAvailability.SUSPENDED -> 1
                    AppAvailability.ENABLED -> 2
                }
            }.thenByDescending { it.inOneKey }.thenBy { it.label.lowercase() },
        )
    }

    LunoScreenLazy(
        title = stringResource(R.string.app_control_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            // 常态：显示系统应用（图标开关态）+ 选择入口；选择态：顶栏放退出。
            if (state.selectionMode) {
                PressIconButton(onClick = { vm.toggleSelectionMode() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.app_control_cancel_select),
                    )
                }
            } else {
                PressIconButton(
                    onClick = { vm.setShowSystemApps(!state.showSystemApps) },
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = stringResource(
                            if (state.showSystemApps) {
                                R.string.app_control_hide_system
                            } else {
                                R.string.app_control_show_system
                            },
                        ),
                        tint = if (state.showSystemApps) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
                PressIconButton(
                    onClick = { vm.toggleSelectionMode() },
                    enabled = visible.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.app_control_select),
                    )
                }
            }
        },
        floatingContent = { hazeState ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                AppControlToolbar(
                    hazeState = hazeState,
                    selectionMode = state.selectionMode,
                    selectedCount = state.selected.size,
                    query = state.query,
                    searchExpanded = state.searchExpanded,
                    showAdd = appPicker != null,
                    canSelectAll = visible.isNotEmpty(),
                    busy = state.busy,
                    onAdd = { showPicker = true },
                    onQueryChange = vm::setQuery,
                    onToggleSearch = vm::toggleSearch,
                    onSelectAll = { vm.selectAll(visible.map { it.packageName }) },
                    onFreeze = vm::requestFreezeSelected,
                    onUnfreeze = vm::unfreezeSelected,
                    onAddOneKey = vm::addSelectedToOneKey,
                    onExitSelection = vm::toggleSelectionMode,
                )
            }
        },
        dialogs = {
            if (state.pendingFreezeCount > 0) {
                ConfirmDialog(
                    title = stringResource(
                        R.string.app_control_freeze_confirm_title,
                        state.pendingFreezeCount,
                    ),
                    text = stringResource(R.string.app_control_freeze_confirm_desc),
                    destructive = true,
                    onDismissRequest = { vm.cancelPendingFreeze() },
                    onConfirm = { vm.confirmFreezeSelected() },
                )
            }
        },
    ) {
        if (state.failures.isNotEmpty()) {
            item {
                LunoGroup(title = stringResource(R.string.app_control_failures_title)) {
                    state.failures.forEachIndexed { index, (packageName, reason) ->
                        LunoSettingsRow(
                            title = packageName,
                            subtitle = reason,
                            shape = segmentedShape(index, state.failures.size + 1),
                        )
                    }
                    LunoSettingsRow(
                        title = stringResource(R.string.app_control_clear_failures),
                        shape = segmentedShape(state.failures.size, state.failures.size + 1),
                        onClick = { vm.clearFailures() },
                    )
                }
            }
        }
        if (state.loading) {
            item {
                LoadingState(message = stringResource(R.string.app_control_loading))
            }
        } else if (visible.isEmpty()) {
            item {
                EmptyState(
                    message = stringResource(R.string.app_control_empty),
                    modifier = Modifier.padding(vertical = ListSpacing),
                )
            }
        } else {
            // 应用列表与全站一致：LunoGroup 连接分段圆角，计数摘要放组副标题（仍为全量，不随搜索过滤）。
            item {
                LunoGroup(
                    subtitle = stringResource(
                        R.string.app_control_frozen_count,
                        state.frozenCount,
                        state.oneKeyCount,
                    ),
                ) {
                    visible.forEachIndexed { index, app ->
                        val stateText = statusText(app)
                        if (state.selectionMode) {
                            LunoSelectableRow(
                                title = app.label,
                                subtitle = stateText.ifEmpty { app.packageName },
                                selected = app.packageName in state.selected,
                                onSelectedChange = { vm.toggleSelected(app.packageName) },
                                iconModel = AppIconRequests.request(
                                    context,
                                    app.packageName,
                                    iconSizePx,
                                ),
                                shape = segmentedShape(index, visible.size),
                            )
                        } else {
                            LunoSettingsRow(
                                title = app.label,
                                subtitle = stateText.ifEmpty { app.packageName },
                                iconModel = AppIconRequests.request(
                                    context,
                                    app.packageName,
                                    iconSizePx,
                                ),
                                shape = segmentedShape(index, visible.size),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PressIconButton(onClick = { vm.toggleOneKey(app) }) {
                                            Icon(
                                                modifier = Modifier.size(24.dp),
                                                imageVector = if (app.inOneKey) {
                                                    Icons.Default.Star
                                                } else {
                                                    Icons.Outlined.StarOutline
                                                },
                                                contentDescription = stringResource(
                                                    if (app.inOneKey) {
                                                        R.string.app_control_one_key_remove
                                                    } else {
                                                        R.string.app_control_one_key_add
                                                    },
                                                ),
                                            )
                                        }
                                        // 冻结 / 解冻只用图标（不用 Switch）：已冻结 → 解锁；正常 → 雪花。
                                        val frozen = app.availability == AppAvailability.DISABLED
                                        PressIconButton(
                                            onClick = { vm.toggleFreeze(app) },
                                            enabled = !app.protected &&
                                                app.availability != AppAvailability.SUSPENDED,
                                        ) {
                                            Icon(
                                                modifier = Modifier.size(24.dp),
                                                imageVector = if (frozen) {
                                                    Icons.Default.LockOpen
                                                } else {
                                                    Icons.Default.AcUnit
                                                },
                                                contentDescription = stringResource(
                                                    if (frozen) {
                                                        R.string.app_control_unfreeze
                                                    } else {
                                                        R.string.app_control_freeze
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker && appPicker != null) {
        appPicker(
            AppPickerRequest(
                selectedPackageNames = state.apps.filter { it.inOneKey }.map { it.packageName },
                selectedUserId = null,
                confirmOnSelect = false,
                onDismiss = { showPicker = false },
                onConfirm = { packages ->
                    vm.setOneKeyList(packages)
                    showPicker = false
                },
                onSelect = null,
            ),
        )
    }
}

private enum class ToolbarMode { Normal, Search, Selection }

/**
 * 底部玻璃胶囊工具条（ui-guidelines §4.2 / §4.3，与剪贴板页底栏同源）：常态 [添加][搜索]、
 * 搜索 [输入框][收起×]、选择 [已选 N][全选][冻结][解冻]；胶囊随内容自适应、靠右，搜索展开全宽。
 *
 * 几何复刻 [hunoia.luno.clipboard.ClipboardScreen] 的 [hunoia.luno.clipboard.ClipboardToolbar]：
 * `glassCapsule` 容器、IME∪导航 inset、`BackHandler` 收搜索、展开后取焦点、`swallowTouches` 防穿透。
 */
@Composable
private fun AppControlToolbar(
    hazeState: HazeState,
    selectionMode: Boolean,
    selectedCount: Int,
    query: String,
    searchExpanded: Boolean,
    showAdd: Boolean,
    canSelectAll: Boolean,
    busy: Boolean,
    onAdd: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onAddOneKey: () -> Unit,
    onExitSelection: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    val focusRequester = remember { FocusRequester() }
    var collapsePending by remember { mutableStateOf(false) }

    // 收起搜索：先收键盘，等 IME 真正收起再清 query + 收起，不挤在同一段动画里。
    fun closeSearch() {
        if (imeVisible) {
            collapsePending = true
            keyboard?.hide()
        } else {
            collapsePending = false
            onQueryChange("")
            onToggleSearch()
        }
    }
    LaunchedEffect(collapsePending, imeVisible) {
        if (collapsePending && !imeVisible) {
            collapsePending = false
            onQueryChange("")
            onToggleSearch()
        }
    }
    // 系统返回先收搜索框，否则输入到一半直接退页。
    BackHandler(enabled = searchExpanded) { closeSearch() }

    // 键盘与导航栏取并集：IME 可见时底部 inset 已含导航栏，相加会多顶一截。
    val bottomInsets = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))

    val mode = when {
        searchExpanded -> ToolbarMode.Search
        selectionMode -> ToolbarMode.Selection
        else -> ToolbarMode.Normal
    }
    val isSearch = mode == ToolbarMode.Search

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .swallowTouches()
            .windowInsetsPadding(bottomInsets)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = DockGutter, end = DockGutter, bottom = BottomBarPadding),
        horizontalAlignment = Alignment.End,
    ) {
        // 玻璃胶囊容器：随内容自适应、靠右；仅搜索时展开全宽。宽度随形态切换同帧变化，
        // 内容淡入淡出（动画改动曾用 BoxWithConstraints+widthIn+animateContentSize 导致
        // 靠左 + 方形裁剪圆角阴影，已回退）。若需宽度平滑动画，须在玻璃胶囊外部做，不能
        // 在 clip 之前加方形裁剪。
        Column(
            modifier = Modifier
                .then(if (isSearch) Modifier.fillMaxWidth() else Modifier)
                .glassCapsule(hazeState),
        ) {
            Row(
                modifier = Modifier
                    .then(if (isSearch) Modifier.fillMaxWidth() else Modifier)
                    .height(DockTabHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = mode,
                    modifier = Modifier
                        .then(if (isSearch) Modifier.fillMaxWidth() else Modifier)
                        .fillMaxHeight(),
                    transitionSpec = {
                        fadeIn(tween(MotionSwapIn)) togetherWith fadeOut(tween(MotionSwapOut))
                    },
                    label = "appControlToolbarMode",
                ) { m ->
                    when (m) {
                        ToolbarMode.Search -> ToolbarSearchContent(
                            query = query,
                            onQueryChange = onQueryChange,
                            focusRequester = focusRequester,
                            onClose = { closeSearch() },
                        )
                        ToolbarMode.Selection -> ToolbarSelectionContent(
                            selectedCount = selectedCount,
                            canSelectAll = canSelectAll,
                            busy = busy,
                            onSelectAll = onSelectAll,
                            onFreeze = onFreeze,
                            onUnfreeze = onUnfreeze,
                            onAddOneKey = onAddOneKey,
                            onExitSelection = onExitSelection,
                        )
                        ToolbarMode.Normal -> ToolbarNormalContent(
                            showAdd = showAdd,
                            onAdd = onAdd,
                            onToggleSearch = onToggleSearch,
                        )
                    }
                }
            }
        }
    }
}

/** 常态：添加（一键名单，仅 appPicker 存在）+ 搜索。 */
@Composable
private fun ToolbarNormalContent(
    showAdd: Boolean,
    onAdd: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showAdd) {
            PressIconButton(onClick = onAdd) {
                Icon(
                    Icons.Outlined.StarOutline,
                    contentDescription = stringResource(R.string.app_control_one_key_add),
                )
            }
        }
        PressIconButton(onClick = onToggleSearch) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.app_control_search_hint),
            )
        }
    }
}

/** 搜索：输入框 + × 收起；展开动画铺好布局再取焦点（节点未挂载时 requestFocus 落空）。 */
@Composable
private fun ToolbarSearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
) {
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed) {
        if (placed) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LunoSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.app_control_search_hint),
            focusRequester = focusRequester,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { placed = true },
        )
        PressIconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.app_control_search_collapse),
            )
        }
    }
}

/** 选择：纯数字徽标（唯一文字）+ 全选 / 冻结 / 解冻 / 添加一键名单 / 退出（其余全图标）。 */
@Composable
private fun ToolbarSelectionContent(
    selectedCount: Int,
    canSelectAll: Boolean,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onAddOneKey: () -> Unit,
    onExitSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = selectedCount.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            // 左边距与按钮图标内缩一致（IconButton 40dp 容器 / 24dp 图标 → 8dp），
            // 否则选中数比图标更靠左，行首视觉不齐。
            modifier = Modifier.padding(start = 8.dp),
        )
        PressIconButton(onClick = onSelectAll, enabled = canSelectAll && !busy) {
            Icon(
                Icons.Default.SelectAll,
                contentDescription = stringResource(R.string.app_control_select_all),
            )
        }
        PressIconButton(onClick = onFreeze, enabled = selectedCount > 0 && !busy) {
            Icon(
                Icons.Default.AcUnit,
                contentDescription = stringResource(R.string.app_control_freeze_selected),
            )
        }
        PressIconButton(onClick = onUnfreeze, enabled = selectedCount > 0 && !busy) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = stringResource(R.string.app_control_unfreeze_selected),
            )
        }
        PressIconButton(onClick = onAddOneKey, enabled = selectedCount > 0 && !busy) {
            Icon(
                Icons.Default.Star,
                contentDescription = stringResource(R.string.app_control_one_key_add),
            )
        }
        PressIconButton(onClick = onExitSelection) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.app_control_cancel_select),
            )
        }
    }
}

/**
 * 只吃事件、不做任何事：把落在工具条矩形范围内的触摸从下层列表手里拿回来。
 * 工具条悬浮在列表之上（`floatingContent`），没挂 pointerInput 的空白区域会穿透到列表行。
 */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun statusText(app: ManagedApp): String {
    val parts = buildList {
        if (app.inOneKey) add(stringResource(R.string.app_control_one_key))
        when (app.availability) {
            AppAvailability.DISABLED -> add(stringResource(R.string.app_control_state_frozen))
            AppAvailability.SUSPENDED -> add(stringResource(R.string.app_control_state_suspended))
            AppAvailability.ENABLED -> {
                if (app.protected) add(stringResource(R.string.app_control_state_protected))
            }
        }
    }
    return parts.joinToString(" · ")
}
