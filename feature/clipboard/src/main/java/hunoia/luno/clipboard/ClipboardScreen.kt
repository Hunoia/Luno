package hunoia.luno.clipboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import dev.chrisbanes.haze.HazeState
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardMaxEntriesRange
import hunoia.luno.ui.component.FloatingBarLayout
import hunoia.luno.ui.component.FloatingBottomBarDefaults
import hunoia.luno.ui.component.FloatingTabRow
import hunoia.luno.ui.component.LunoCapsuleSearch
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.chunkedGroup
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.BottomBarIconSize
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.BottomBarTabWidth
import hunoia.luno.ui.theme.DockCapsuleHeight
import hunoia.luno.ui.theme.DockGutter
import hunoia.luno.ui.theme.DockMaxWidth
import hunoia.luno.ui.theme.DockTabHeight
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.SmallShape
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.glassCapsule

/**
 * 剪贴板管理页：历史唯一来源 + 固定 / 删除 / 清空 / 搜索 / 批量 / 导入导出。
 *
 * 记录与捕获规则都在 `ClipboardCenter`：页面只消费流、发事件；进入页面补一次幂等
 * [ClipboardVM.refresh]（覆盖后台收不到系统回调的机型）。行点按 = 写回剪贴板；
 * 敏感项先显示，再点一次进入二次确认。
 *
 * 底栏分段为 文本 / 图片 / 设置 三段（[ClipboardTab]，顺序即底栏顺序，默认文本），
 * 玻璃胶囊分段控件 + 右端搜索槽（ui-guidelines §4.2 类型 chip 底栏 / §4.3 搜索型底栏，与动作
 * 选择页底栏同源）；搜索展开与分段同格互换，有搜索词时跨内容两类匹配（§4.3，设置段不参与）。
 * 文本 / 图片两类各自管理固定项：页内固定项组置顶，互不混合；设置段承载记录设置组
 * （记录剪贴板 / 未固定条目上限），文本 / 图片页不显示设置组；本次会话（敏感）组仅文本段顶部显示。
 * 清空 / 导出按当前页执行（保留当前页固定项）。行菜单固定 / 分享 / 悬浮显示 / 删除。
 * 选中态：删除移到顶栏右上角（退出按钮左侧），底栏只留 [已选 N / 全选 / 固定]。
 */
@Composable
fun ClipboardScreen(
    onBack: () -> Unit,
    vm: ClipboardVM = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val facades = LocalFacades.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val text = if (message.arg == null) {
                context.getString(message.resId)
            } else {
                context.getString(message.resId, message.arg)
            }
            showToast(text)
        }
    }

    // 分段 / 查询变化主动回顶（ui-guidelines §3.2 第 6 条）；首位恒是设置组或会话 / 内容组，scrollToItem 安全。
    LaunchedEffect(state.contentTab, state.query) {
        listState.scrollToItem(0)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        // 按当前页导出：设置页无内容，kind 为空时忽略（菜单在设置页已禁用导出）。
        uri?.let { u -> state.contentTab.kind?.let { k -> vm.export(context, u, k) } }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { vm.import(context, it) } }

    // 有搜索词 → 跨内容两类匹配（§4.3 底栏内搜索口径，设置段不参与）；空查询 → 当前分段。
    // 设置段不是内容：inTab(SETTINGS) 恒 false，空查询下 visible 恒空。
    val visible = remember(state.entries, state.query, state.contentTab) {
        val query = state.query.trim()
        if (query.isNotEmpty()) {
            state.entries.filter { it.matchesQuery(query) }
        } else {
            state.entries.filter { it.inTab(state.contentTab) }
        }
    }
    // 设置段：仅当空查询时渲染记录设置组（搜索时同样搜记录，清空后回设置页）。
    val settingsTab = state.contentTab == ClipboardTab.SETTINGS && state.query.isBlank()
    // 当前页对应的内容类型；设置页无内容。
    val contentKind = state.contentTab.kind

    @Composable
    fun EntryRow(entry: ClipboardEntry, shape: androidx.compose.ui.graphics.Shape) {
        ClipboardRow(
            entry = entry,
            shape = shape,
            selectionMode = state.selectionMode,
            selected = entry.id in state.selectedIds,
            pinned = entry.pinned,
            onClick = { vm.onEntryClick(entry) },
            onTogglePin = { vm.togglePin(entry) },
            onDelete = { vm.delete(entry) },
            onShare = { vm.share(context, entry) },
            onFloat = { facades.clipFloat.show(context, entry.id) },
        )
    }

    LunoPage(title = stringResource(R.string.clipboard_title),
onBack = onBack,
actions = {
            // 常态：选择入口 + 数据菜单；选中态：删除（退出左侧）+ 退出，数据菜单隐藏。
            if (state.selectionMode) {
                PressIconButton(
                    onClick = { vm.deleteSelected() },
                    enabled = state.selectedIds.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.clipboard_delete_selected),
                    )
                }
                PressIconButton(onClick = { vm.toggleSelectionMode() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clipboard_clear_selection),
                    )
                }
            } else {
                PressIconButton(
                    onClick = { vm.toggleSelectionMode() },
                    enabled = visible.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = stringResource(R.string.clipboard_select),
                    )
                }
                ClipboardDataMenu(
                    onExport = { exportLauncher.launch("luno_clipboard.json") },
                    onImport = { importLauncher.launch("application/json") },
                    onClearAll = { vm.requestClear() },
                    contentEnabled = contentKind != null,
                )
            }
        },
bottomBar = { hazeState ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ClipboardToolbar(
                    hazeState = hazeState,
                    selectionMode = state.selectionMode,
                    selectedCount = state.selectedIds.size,
                    query = state.query,
                    searchExpanded = state.searchExpanded,
                    tab = state.contentTab,
                    onTabChange = { vm.setTab(it) },
                    onQueryChange = { vm.setQuery(it) },
                    onToggleSearch = { vm.toggleSearch() },
                    onSelectAll = { vm.selectAll(visible.map { it.id }) },
                    onPinSelected = { vm.pinSelected() },
                    onExitSelection = { vm.toggleSelectionMode() },
                )
            }
        },
dialogs = {
            if (state.clearConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.clipboard_clear_confirm_title),
                    text = stringResource(R.string.clipboard_clear_confirm_desc),
                    destructive = true,
                    onDismissRequest = { vm.dismissClear() },
                    onConfirm = { state.contentTab.kind?.let { vm.confirmClear(it) } },
                )
            }
        }) {
    list(state = listState) {
        lazy {

        if (settingsTab) {
            // 设置段：只渲染记录设置组（无空态 / 会话 / 内容）。
            item(key = "settings") {
                group(title = stringResource(R.string.clipboard_settings_group)) {
                    switchRow(
                        title = stringResource(R.string.clipboard_record_enabled),
                        subtitle = stringResource(R.string.clipboard_record_enabled_hint),
                        checked = state.settings.recordEnabled,
                        onCheckedChange = { vm.setRecordEnabled(it) },
                    )
                    row(
                        title = stringResource(R.string.clipboard_max_entries),
                        trailing = {
                            MaxEntriesControls(
                                value = state.settings.maxEntries,
                                onValueChange = { vm.setMaxEntries(it) },
                            )
                        },
                    )
                }
            }
        } else {
            val searching = state.query.isNotBlank()
            val pinnedItems = visible.filter { it.pinned }
            val restItems = visible.filterNot { it.pinned }

            // 固定项组置顶（文本 / 图片各自管理；搜索时搜索结果平铺，不再分组）。
            if (!searching && pinnedItems.isNotEmpty()) {
                chunkedGroup(
                    pinnedItems,
                    "pinned",
                    title = { stringResource(R.string.clipboard_pinned_group) },
                ) { shape, _, entry ->
                    EntryRow(entry, shape)
                }
            }

            if (searching) {
                // 搜索结果可达数百条，同内容组一样按块拆（§3.2 第 10 条）。
                chunkedGroup(
                    visible,
                    "search",
                    title = { stringResource(R.string.clipboard_search_results_group) },
                    emptyContent = {
                        EmptyState(message = stringResource(R.string.clipboard_empty_search))
                    },
                ) { shape, _, entry ->
                    EntryRow(entry, shape)
                }
            } else if (pinnedItems.isEmpty() && restItems.isEmpty()) {
                // 当前页完全为空（无固定、无内容）。
                item(key = "empty") {
                    EmptyState(
                        message = stringResource(
                            if (state.entries.isEmpty()) {
                                R.string.clipboard_empty
                            } else {
                                R.string.clipboard_category_empty
                            },
                        ),
                    )
                }
            } else if (restItems.isNotEmpty()) {
                // 内容组可达数百条，是合成上限的主要来源（§3.2 第 10 条）。
                chunkedGroup(
                    restItems,
                    "content",
                    title = { stringResource(state.contentTab.labelRes) },
                ) { shape, _, entry ->
                    EntryRow(entry, shape)
                }
            }
            // 只有固定项（无未固定内容）时：固定组已渲染，不再渲染空态或内容组。
        }

        }
    }
}
}

/** 工具条三种形态；搜索优先于选中态（两者互斥，见 [ClipboardVM.toggleSelectionMode]）。 */
private enum class ToolbarMode { Search, Selection, Normal }

/** 单条记录是否命中查询（跨内容两类搜索用，§4.3）。 */
private fun ClipboardEntry.matchesQuery(query: String): Boolean = when (kind) {
    ClipKind.TEXT -> text.contains(query, ignoreCase = true)
    ClipKind.IMAGE -> mime?.contains(query, ignoreCase = true) == true
}

/** 分段底栏的图标（无文字，contentDescription 用 [ClipboardTab.labelRes]）。 */
private val ClipboardTab.icon: ImageVector
    get() = when (this) {
        ClipboardTab.TEXT -> Icons.Default.TextFields
        ClipboardTab.IMAGE -> Icons.Default.Image
        ClipboardTab.SETTINGS -> Icons.Default.Settings
    }

/** 当前分段对应的内容类型；设置段不是内容，返回 null（导出 / 清空据此按页执行）。 */
private val ClipboardTab.kind: ClipKind?
    get() = when (this) {
        ClipboardTab.TEXT -> ClipKind.TEXT
        ClipboardTab.IMAGE -> ClipKind.IMAGE
        ClipboardTab.SETTINGS -> null
    }

/** 单条记录是否属于当前内容分段（文本 / 图片两类；设置段不是内容，恒 false）。 */
private fun ClipboardEntry.inTab(tab: ClipboardTab): Boolean = when (tab) {
    ClipboardTab.TEXT -> kind == ClipKind.TEXT
    ClipboardTab.IMAGE -> kind == ClipKind.IMAGE
    ClipboardTab.SETTINGS -> false
}

/**
 * 顶部「数据」下拉菜单：导出 / 导入 / 清空本页（清空用破坏性色）。导出与清空按当前页执行，
 * 设置页无内容时禁用（[contentEnabled]）。替代原列表底部的「数据」组（ui-guidelines §4.4）。
 */
@Composable
private fun ClipboardDataMenu(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
    contentEnabled: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PressIconButton(onClick = { open = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(hunoia.luno.design.R.string.more_options),
            )
        }
        LunoDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LunoDropdownMenuItem(
                text = stringResource(R.string.clipboard_export),
                onClick = { open = false; onExport() },
                enabled = contentEnabled,
            )
            LunoDropdownMenuItem(
                text = stringResource(R.string.clipboard_import),
                onClick = { open = false; onImport() },
            )
            LunoDropdownMenuItem(
                text = stringResource(R.string.clipboard_clear_page),
                onClick = { open = false; onClearAll() },
                enabled = contentEnabled,
                isError = true,
            )
        }
    }
}

/**
 * 底部玻璃胶囊底栏（ui-guidelines §4.2 / §4.3，与动作选择页底栏同源）：左 = 分段控件
 * （文本 / 图片 / 设置），右 = 搜索槽（搜索展开与分段同格互换，胶囊宽固定不重算）；
 * 选中态替换为 [已选 N / 全选 / 固定]，尾槽换成退出。
 *
 * 几何复刻 [ActionSelectBottomBar]：`FloatingBarLayout` 算宽、`glassCapsule` 容器、窄屏压缩
 * chip 宽、宽屏限宽 600dp 居中。IME 与安全区处理同款：键盘/导航取并集、先收键盘再清 query、
 * 返回键先收搜索框、`onGloballyPositioned` 后取焦点、`swallowTouches` 防穿透。
 */
@Composable
private fun ClipboardToolbar(
    hazeState: HazeState,
    selectionMode: Boolean,
    selectedCount: Int,
    query: String,
    searchExpanded: Boolean,
    tab: ClipboardTab,
    onTabChange: (ClipboardTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onPinSelected: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .swallowTouches()
            .windowInsetsPadding(bottomInsets)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = DockGutter, end = DockGutter, bottom = BottomBarPadding),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val dockWidth = FloatingBarLayout.barWidth(maxWidth)
            val searchSlot = DockTabHeight
            val chipCount = ClipboardTab.entries.size
            val rawChipWidth = (dockWidth - searchSlot - TileInnerPadding * 2) / chipCount
            // 极窄屏连 48dp 下限都放不下时按可用宽压缩，宁可窄一点也不让胶囊溢出。
            val chipWidth = min(
                FloatingBarLayout.tabWidth(
                    barWidth = dockWidth,
                    tabCount = chipCount,
                    maxTabWidth = BottomBarTabWidth,
                    innerPadding = TileInnerPadding,
                    reservedWidth = searchSlot,
                ),
                rawChipWidth,
            )
            val capsuleWidth = FloatingBarLayout.capsuleWidth(
                tabWidth = chipWidth,
                tabCount = chipCount,
                innerPadding = TileInnerPadding,
                trailingSlot = searchSlot,
            )

            // 搜索态 ↔ 常态/选择：叠层交叉淡入淡出（FlClash 式）。搜索层与常态层同时组合，
            // 视觉 alpha 由 searchAlpha 立即驱动；搜索层节点保留到键盘滑完（searchExpanded || imeVisible），
            // 输入框不在下滑途中销毁 → 焦点/InputConnection 不断 → 键盘有完整下滑动画、不回弹。
            val searchAlpha by animateFloatAsState(
                targetValue = if (searchExpanded) 1f else 0f,
                animationSpec = tween(MotionSwapIn),
                label = "clipboardSearchAlpha",
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (!searchExpanded) {
                    // 玻璃胶囊容器（阴影/裁形/毛玻璃/描边/内边距），与页面级悬浮胶囊共用同一份样式。
                    Column(
                        modifier = Modifier
                            .width(capsuleWidth)
                            .glassCapsule(hazeState)
                            .graphicsLayer { alpha = 1f - searchAlpha },
                        ) {
                            val mode = when {
                                selectionMode -> ToolbarMode.Selection
                                else -> ToolbarMode.Normal
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(DockTabHeight),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 分段 / 选中态同一行互换：不把底栏撑高，列表留白不跳。
                                AnimatedContent(
                                    targetState = mode,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    transitionSpec = {
                                        fadeIn(tween(MotionSwapIn)) togetherWith fadeOut(tween(MotionSwapOut))
                                    },
                                    label = "clipboardToolbarMode",
                                ) { m ->
                                    when (m) {
                                        ToolbarMode.Selection -> ToolbarSelectionContent(
                                            selectedCount = selectedCount,
                                            onSelectAll = onSelectAll,
                                            onPin = onPinSelected,
                                        )
                                        ToolbarMode.Normal -> TabSegments(
                                            tab = tab,
                                            chipWidth = chipWidth,
                                            onTabChange = onTabChange,
                                        )
                                        // 搜索态已在分支外，这里不会出现。
                                        ToolbarMode.Search -> Unit
                                    }
                                }
                                // 尾槽：常态搜索 / 选中态退出（删除在顶栏，退出回到底栏右侧）。
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(searchSlot),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (mode) {
                                        ToolbarMode.Selection -> SlotButton(
                                            onClick = onExitSelection,
                                            icon = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clipboard_clear_selection),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ToolbarMode.Normal -> SlotButton(
                                            onClick = { onToggleSearch() },
                                            icon = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.clipboard_search_hint),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ToolbarMode.Search -> Unit
                                    }
                                }
                            }
                    }
                }
                // 搜索层：叠在上层，alpha 立即淡出/淡入；节点保留到键盘滑完再移除。
                if (searchExpanded || imeVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = searchAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        LunoCapsuleSearch(
                            query = query,
                            onQueryChange = onQueryChange,
                            placeholder = stringResource(R.string.clipboard_search_hint),
                            onCancel = { closeSearch() },
                            cancelContentDescription = stringResource(R.string.clipboard_search_collapse),
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
}

/**
 * 分段控件：3 段（文本 / 图片 / 设置）点选即切，滑动指示 pill 与主页底栏同源
 * （ui-guidelines §4.2）。直接复用 [FloatingTabRow]：手势宽度实时读取，转屏后仍跟手。
 */
@Composable
private fun TabSegments(
    tab: ClipboardTab,
    chipWidth: Dp,
    onTabChange: (ClipboardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = ClipboardTab.entries
    val selectedIndex = tabs.indexOf(tab).coerceAtLeast(0)
    FloatingTabRow(
        modifier = modifier,
        items = tabs,
        selectedIndex = { selectedIndex },
        onSelected = { index -> onTabChange(tabs[index]) },
        tabWidth = chipWidth,
        tabHeight = DockTabHeight,
        colors = FloatingBottomBarDefaults.colors(),
        rubberBand = true,
    ) { item, _ ->
        // 选中态由底下滑动的 pill 表达；图标继承 tab tint（active=primary / 其他=onSurfaceVariant）。
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelRes),
            modifier = Modifier.size(BottomBarIconSize),
        )
    }
}

/**
 * 尾槽单颗图标按钮：自己画点击区（不用 IconButton——其固定 48dp 与尾槽见方不对齐），
 * 点在死区上不穿透（swallowTouches 已兜底整块矩形）。
 */
@Composable
private fun SlotButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .pressClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** 选中态：已选数量 + 全选 / 固定（删除在顶栏、退出在顶栏与尾槽）。 */
@Composable
private fun ToolbarSelectionContent(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onPin: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = selectedCount.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            // 与 AppControl 选中数一致：左边距对齐按钮图标内缩（IconButton 40dp / 24dp → 8dp）。
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        PressIconButton(onClick = onSelectAll) {
            Icon(
                Icons.Default.SelectAll,
                contentDescription = stringResource(R.string.clipboard_select_all),
            )
        }
        PressIconButton(onClick = onPin) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = stringResource(R.string.clipboard_pin_selected),
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

/** 单条历史行：预览 + 元信息 + 三点菜单；批量模式用勾选框替代菜单。行菜单固定/分享/悬浮显示/删除。 */
@Composable
private fun ClipboardRow(
    entry: ClipboardEntry,
    shape: androidx.compose.ui.graphics.Shape,
    selectionMode: Boolean,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onFloat: (() -> Unit)?,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = when (entry.kind) {
        ClipKind.TEXT -> entry.text.replace('\n', ' ').trim()
        ClipKind.IMAGE -> stringResource(R.string.clipboard_image)
    }
    val metadata = buildList {
        if (pinned) add(stringResource(R.string.clipboard_pinned_badge))
        val time = ClipboardVM.formatTime(entry.time)
        if (time.isNotEmpty()) add(time)
    }.joinToString(" · ")

    LunoSettingsRow(
        title = title,
        subtitle = metadata,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
        leading = if (entry.kind == ClipKind.IMAGE) {
            {
                var imageFailed by remember(entry.imagePath) { mutableStateOf(false) }
                val file = remember(entry.imagePath) { ClipboardCenter.imageFile(entry.imagePath) }
                if (file != null && !imageFailed) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        imageLoader = context.imageLoader,
                        onError = { imageFailed = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(SmallShape),
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else {
            null
        },
        onClick = onClick,
        trailing = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Box {
                    PressIconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(hunoia.luno.design.R.string.more_options),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    LunoDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        LunoDropdownMenuItem(
                            text = stringResource(
                                if (pinned) R.string.clipboard_unpin else R.string.clipboard_pin,
                            ),
                            onClick = { menuOpen = false; onTogglePin() },
                        )
                        LunoDropdownMenuItem(
                            text = stringResource(R.string.clipboard_share),
                            onClick = { menuOpen = false; onShare() },
                        )
                        if (onFloat != null) {
                            LunoDropdownMenuItem(
                                text = stringResource(R.string.clipboard_float),
                                onClick = { menuOpen = false; onFloat() },
                            )
                        }
                        LunoDropdownMenuItem(
                            text = stringResource(R.string.clipboard_delete),
                            onClick = { menuOpen = false; onDelete() },
                            isError = true,
                        )
                    }
                }
            }
        },
    )
}

/** 上限调整：滑杆粒度太细，这里用 10 条一档的“减 / 加”按钮。 */
@Composable
private fun MaxEntriesControls(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val min = ClipboardMaxEntriesRange.first
    val max = ClipboardMaxEntriesRange.last
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PressIconButton(onClick = { onValueChange((value - 10).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = stringResource(R.string.clipboard_max_entries_value, value),
            style = MaterialTheme.typography.bodyLarge,
        )
        PressIconButton(onClick = { onValueChange((value + 10).coerceAtMost(max)) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
