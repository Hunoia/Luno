package hunoia.luno.appcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import hunoia.luno.quickaccess.query.AppDrawerSections
import hunoia.luno.ui.component.LunoCapsuleSearch
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.component.SystemAppsMenuButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SectionBadge
import hunoia.luno.ui.component.row.chunkedGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.DockCapsuleHeight
import hunoia.luno.ui.theme.DockGutter
import hunoia.luno.ui.theme.DockMaxWidth
import hunoia.luno.ui.theme.DockTabHeight
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.glassCapsule

/**
 * 应用冻结管理页：冻结 / 解冻、一键名单、批量操作与失败回执。
 *
 * 默认只列「一键名单」内的应用；搜索时扩到全机（系统包跟随全局「显示系统应用」开关）。
 * 系统 suspend 只读展示；自己与当前桌面禁止冻结（由 PackageControl 兜底）。
 *
 * 底栏玻璃胶囊工具条（参考剪贴板页 [hunoia.luno.clipboard.ClipboardScreen]）：
 * 常态 [添加(一键名单)][搜索]、搜索 [输入框][收起×]、选择 [已选 N][全选][冻结][解冻]；
 * 胶囊随内容自适应、靠右，搜索时展开成全宽。搜索 / 选择互斥（VM 负责）。
 * 行单击不做事；长按行冻结 / 解冻（SUSPENDED / 保护应用不触发）。行尾 ⋮ 菜单提供
 * 加入/移出一键名单 与 冻结/解冻（SUSPENDED / 保护应用禁用冻结项）。
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

    // 按拼音分节且**保留节结构**（不 flatMap 平铺）：列表按节发多个 item。整表塞进单个 item 时
    // 该节点承载全部行，高度越过设备合成上限（真机 ≈8192px，34 行 ≈8058px 在限内、35 行 ≈8295px
    // 越界），超出部分既不可见也不可点、且与屏幕位置无关；拆成多 item 后惰性窗口落到节粒度。
    val sections = remember(state.apps, state.query) {
        val query = state.query.trim()
        val filtered = if (query.isEmpty()) {
            state.apps.filter { it.inOneKey }
        } else {
            state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        // 拼音口径排序（与选择页 / 最近应用黑名单页 / 应用抽屉一致）：中文按拼音首字母 +
        // 节内全拼序、英文按字母、`#` 恒最后。排序键只含 label（不含 availability/oneKey），
        // 冻结/解冻只改 availability 不改 label → 行不跳位（#5）。
        AppDrawerSections.alphabeticalSectionsOf(filtered) { it.label }
    }
    // 保留分节结构供按节发 item（见列表 content）；平铺视图供空态 / 使能态判断。
    val visible = sections.flatMap { it.items }

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.app_control_title),
onBack = onBack,
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
                SystemAppsMenuButton(
                    showUILess = state.systemVisibility.showUILess,
                    showNoEntry = state.systemVisibility.showNoEntry,
                    onShowUILessChange = { enabled ->
                        vm.setSystemVisibility(state.systemVisibility.copy(showUILess = enabled))
                    },
                    onShowNoEntryChange = { enabled ->
                        vm.setSystemVisibility(state.systemVisibility.copy(showNoEntry = enabled))
                    },
                )
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
                    onRemoveOneKey = vm::removeSelectedFromOneKey,
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
        }) {
    list(bottomContentPadding = FloatingContentBottom) {
        lazy {

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
            // 按拼音分节各发一个 item（同 RecentAppsBlocklistScreen），节内再按 [MaxRowsPerLazyItem]
            // 分块：单个 item 只承载少量行，节点高度远低于设备合成上限。计数摘要仍为全量，放首节首块。
            sections.forEachIndexed { sectionIndex, section ->
                // 字母节头吸顶（同 RecentAppsBlocklistScreen）：分组可扫读，节间留白有了着落。
                // 冻结计数徽标只放首字母节（A）标题右侧（≥0 始终显示，含 0）。
                stickyHeader(key = "app_header_${section.letter}") {
                    SheetGroupHeader(
                        title = section.letter.toString(),
                        trailing = {
                            if (sectionIndex == 0) {
                                SectionBadge(text = state.frozenCount.toString())
                            }
                        },
                    )
                }
                chunkedGroup(
                    section.items,
                    "app_${section.letter}",
                ) { shape, _, app ->
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
                            shape = shape,
                        )
                    } else {
                        key(app.packageName) {
                            val frozen = app.availability == AppAvailability.DISABLED
                            val freezeEnabled = !app.protected &&
                                app.availability != AppAvailability.SUSPENDED
                            var menuExpanded by remember { mutableStateOf(false) }
                            LunoSettingsRow(
                                title = app.label,
                                subtitle = stateText.ifEmpty { app.packageName },
                                iconModel = AppIconRequests.request(
                                    context,
                                    app.packageName,
                                    iconSizePx,
                                ),
                                // 冻结行图标灰度（#3）；只作用于 AsyncImage(iconModel) 路径。
                                iconColorFilter = if (frozen) FrozenIconFilter else null,
                                // 长按行冻结/解冻（#2）；单击不做事（保持既有行为）。
                                onLongClick = { vm.toggleFreeze(app) },
                                shape = shape,
                                trailing = {
                                    Box {
                                        PressIconButton(onClick = { menuExpanded = true }) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = stringResource(
                                                    R.string.app_control_more_actions,
                                                ),
                                            )
                                        }
                                        LunoDropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                        ) {
                                            LunoDropdownMenuItem(
                                                text = stringResource(
                                                    if (app.inOneKey) {
                                                        R.string.app_control_one_key_remove
                                                    } else {
                                                        R.string.app_control_one_key_add
                                                    },
                                                ),
                                                onClick = {
                                                    menuExpanded = false
                                                    vm.toggleOneKey(app)
                                                },
                                            )
                                            // 冻结 / 解冻（#1）；SUSPENDED / 保护应用禁用该项。
                                            LunoDropdownMenuItem(
                                                text = stringResource(
                                                    if (frozen) {
                                                        R.string.app_control_unfreeze
                                                    } else {
                                                        R.string.app_control_freeze
                                                    },
                                                ),
                                                enabled = freezeEnabled,
                                                onClick = {
                                                    menuExpanded = false
                                                    vm.toggleFreeze(app)
                                                },
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
    onRemoveOneKey: () -> Unit,
    onExitSelection: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    // 收起：hide 键盘 + 清 query + 关搜索态**立即**（收起动画立即播）；输入框节点由叠层保留到
    // 键盘滑完（`imeVisible=false`）才移除，不在下滑途中销毁 → 焦点/InputConnection 不断 →
    // 键盘有完整下滑动画、不回弹。动画与键盘是两条独立驱动源、互不等待（FlClash 同款）。
    fun closeSearch() {
        keyboard?.hide()
        onQueryChange("")
        onToggleSearch()
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
        // 搜索态 ↔ 常态/选择：叠层交叉淡入淡出（FlClash 式）。搜索层与常态层**同时组合**，
        // 视觉 alpha 由 [searchAlpha] 立即驱动；搜索层节点保留到键盘滑完
        // (`isSearch || imeVisible`)，输入框不在下滑途中销毁 → 焦点/InputConnection 不断 →
        // 键盘有完整下滑动画、不回弹；收起动画与键盘收起是两条独立驱动源，互不等待。
        val searchAlpha by animateFloatAsState(
            targetValue = if (isSearch) 1f else 0f,
            animationSpec = tween(MotionSwapIn),
            label = "appControlSearchAlpha",
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            if (!isSearch) {
                // 常态 / 选择层：玻璃胶囊容器，随内容自适应、靠右；胶囊宽不随形态变化。
                Column(
                    modifier = Modifier
                        .glassCapsule(hazeState)
                        .graphicsLayer { alpha = 1f - searchAlpha },
                ) {
                    Row(
                        modifier = Modifier.height(DockTabHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState = mode,
                            modifier = Modifier.fillMaxHeight(),
                            transitionSpec = {
                                fadeIn(tween(MotionSwapIn)) togetherWith fadeOut(tween(MotionSwapOut))
                            },
                            label = "appControlToolbarMode",
                        ) { m ->
                            when (m) {
                                ToolbarMode.Selection -> ToolbarSelectionContent(
                                    selectedCount = selectedCount,
                                    canSelectAll = canSelectAll,
                                    busy = busy,
                                    onSelectAll = onSelectAll,
                                    onFreeze = onFreeze,
                                    onUnfreeze = onUnfreeze,
                                    onRemoveOneKey = onRemoveOneKey,
                                    onExitSelection = onExitSelection,
                                )
                                ToolbarMode.Normal -> ToolbarNormalContent(
                                    showAdd = showAdd,
                                    onAdd = onAdd,
                                    onToggleSearch = onToggleSearch,
                                )
                                // 搜索态在另一层，这里不会出现。
                                ToolbarMode.Search -> Unit
                            }
                        }
                    }
                }
            }
            // 搜索层：叠在上层，alpha 立即淡出/淡入；节点保留到键盘滑完再移除。
            if (isSearch || imeVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = searchAlpha },
                    contentAlignment = Alignment.Center,
                ) {
                    LunoCapsuleSearch(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.app_control_search_hint),
                        onCancel = { closeSearch() },
                        cancelContentDescription = stringResource(R.string.app_control_search_collapse),
                        hazeState = hazeState,
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = DockMaxWidth)
                            .height(DockCapsuleHeight),
                    )
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
                    Icons.Default.Add,
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

/** 选择：纯数字徽标（唯一文字）+ 全选 / 冻结 / 解冻 / 移出名单 / 退出（其余全图标）。 */
@Composable
private fun ToolbarSelectionContent(
    selectedCount: Int,
    canSelectAll: Boolean,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onRemoveOneKey: () -> Unit,
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
        PressIconButton(onClick = onRemoveOneKey, enabled = selectedCount > 0 && !busy) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.app_control_one_key_remove_selected),
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

/** 冻结行图标灰度（#3）：去饱和。无状态，文件级共享，无需 remember。 */
private val FrozenIconFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) },
)
