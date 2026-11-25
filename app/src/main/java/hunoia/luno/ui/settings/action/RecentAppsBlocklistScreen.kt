package hunoia.luno.ui.settings.action

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.system.gotoAppDetailSettings
import hunoia.luno.core.AppContext
import hunoia.luno.quickaccess.QuickLaunchFacade
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.core.domain.RecentApp
import hunoia.luno.quickaccess.model.getIcon
import hunoia.luno.quickaccess.model.identity
import hunoia.luno.quickaccess.query.AppDrawerSections
import hunoia.luno.quickaccess.query.SystemAppsVisibility
import hunoia.luno.ui.component.IndexBar
import hunoia.luno.ui.component.IndexBarEntry
import hunoia.luno.ui.component.IndexBarOrientation
import hunoia.luno.ui.component.IndexBarSide
import hunoia.luno.ui.component.LunoCapsuleSearch
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.component.SystemAppsMenuButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.icon.toIconBitmap
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.DockCapsuleHeight
import hunoia.luno.ui.theme.DockMaxWidth
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.appHazeSource
import hunoia.luno.ui.theme.glassCapsuleBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 最近应用黑名单（全屏版应用多选）：选择列表由调用方提供、结果经 [onSave] 落盘，
 * 通知胶囊黑名单等复用同一交互。选择只保存在本地草稿；点底栏「保存」才提交。
 * 直接返回（含系统返回键）不写配置，即取消。
 *
 * 列表按拼音字母分节（A–#、节内全拼序），风格跟随全局大卡/分割设置（[LunoGroup]），
 * 右下角贴底限高拼音滑杆（[IndexBar]，最多 5 个字母）直接覆盖在列表上，搜索态隐藏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentAppsBlocklistScreen(
    onBack: () -> Unit,
    current: List<String>,
    onSave: (List<String>) -> Unit,
    title: String = stringResource(R.string.recent_apps_blocklist),
) {
    // 以当前配置为草稿初值；用户改动不写盘，确认时才提交。
    var selected by remember(current) {
        mutableStateOf(current.toSet())
    }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    // 图标位图随屏幕存活：LazyColumn 条目滚出视口即销毁，进程级 AppIconCache 容量有限，
    // 全量应用列表滚动时头部条目被 LRU 挤出 → 回滚异步重载 → 图标消失-显示来回闪。
    // 按行惰性加载进这张屏幕级 map，行渲染同步命中，滚动回看不再闪。
    val appIcons = remember { mutableStateMapOf<String, ImageBitmap>() }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchKeyboard = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    // 收起 = 连查询一起清掉（搜索框藏起来还留着筛选条件，用户看不见也清不掉）。
    // hide 键盘 + 清 query + 关搜索态**立即**（收起动画立即播）；输入框节点由叠层保留到
    // 键盘滑完（`imeVisible=false`）才移除，不在下滑途中销毁 → 焦点/InputConnection 不断 →
    // 键盘有完整下滑动画、不回弹。动画与键盘是两条独立驱动源、互不等待（FlClash 同款）。
    fun closeSearch() {
        searchKeyboard?.hide()
        searchQuery = ""
        searchExpanded = false
    }
    // 系统返回先收搜索框，否则输入到一半直接退页。
    BackHandler(enabled = searchExpanded) { closeSearch() }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // 页内内存开关：默认关、不记忆；两类（无界面组件 / 有界面无入口）各自独立，展开重查。
    var systemVisibility by remember { mutableStateOf(SystemAppsVisibility()) }
    val scope = rememberCoroutineScope()
    val permissionState = rememberGetInstalledAppsPermissionState { granted ->
        if (granted) {
            // 刚授权：清掉进程级缓存里的空表，再触发重读。
            QuickLaunchFacade.invalidateAppListCaches(AppContext.get())
            loaded = false
        }
    }

    suspend fun loadApps() {
        loading = true
        apps = withContext(Dispatchers.IO) {
            QuickLaunchFacade.queryAppsIncludingDisabled(
                AppContext.get(),
                systemVisibility = systemVisibility,
            )
        }
        loading = false
    }

    LaunchedEffect(permissionState.isGranted, loaded) {
        if (!permissionState.isGranted) {
            // 永久拒绝后再申请不会弹框，交给空态里的按钮去设置页。
            if (!permissionState.deniedForever) permissionState.launchPermissionRequest()
        } else if (!loaded) {
            loadApps()
            loaded = true
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    // 纯字母序分节（A–#、节内全拼序）：右侧滑杆要能稳定落到某个字母。
    val sections = remember(filteredApps) {
        AppDrawerSections.alphabeticalSectionsOf(filteredApps) { it.label }
    }
    val listState = rememberLazyListState()

    fun confirm() {
        val ordered = current.filter { it in selected } +
            selected.filter { it !in current }
        onSave(ordered)
    }

    hunoia.luno.ui.component.page.LunoPage(title = title,
onBack = onBack,
actions = {
            if (permissionState.isGranted) {
                SystemAppsMenuButton(
                    showUILess = systemVisibility.showUILess,
                    showNoEntry = systemVisibility.showNoEntry,
                    onShowUILessChange = { enabled ->
                        systemVisibility = systemVisibility.copy(showUILess = enabled)
                        scope.launch { loadApps() }
                    },
                    onShowNoEntryChange = { enabled ->
                        systemVisibility = systemVisibility.copy(showNoEntry = enabled)
                        scope.launch { loadApps() }
                    },
                )
                LunoIconButton(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = stringResource(hunoia.luno.design.R.string.reset),
                    onClick = { selected = emptySet() },
                )
            }
        }) {
    raw { env ->
        val topPadding = env.topPadding
        val hazeState = env.hazeState ?: hunoia.luno.ui.theme.rememberAppHazeState()
Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
        ) {
            if (!permissionState.isGranted) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Lock,
                        message = stringResource(R.string.apps_permission_required),
                        action = {
                            PressTextButton(
                                onClick = {
                                    if (permissionState.deniedForever) {
                                        AppContext.get().gotoAppDetailSettings()
                                    } else {
                                        permissionState.launchPermissionRequest()
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (permissionState.deniedForever) R.string.apps_permission_settings
                                        else R.string.request_get_apps_permission,
                                    ),
                                )
                            }
                        },
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
                    val barEntries = remember(sections) {
                        sections.map {
                            IndexBarEntry(
                                key = it.letter.toString(),
                                label = it.letter.toString(),
                            )
                        }
                    }
                    // 搜索态隐藏滑杆：键盘 + 全宽搜索框 + 滑杆三重重叠，输入过滤本就无需跳字母。
                    // （横向滑杆 metrics 由 IndexBar 内部按自身可用宽度自算。）
                    val showIndex = !loading && sections.isNotEmpty() && !searchExpanded
                    val iconPx = with(LocalDensity.current) { RowIconSize.toPx() }.toInt()
                    // 列表全屏（不压缩）：内容底部让位给悬浮底栏 + 键盘。列表滚到玻璃胶囊后面
                    // 被模糊（AppControlScreen 同款），不再露出 Scaffold 背景层盖住列表。
                    // 让位取搜索态/常态的较大值、固定不变（不做二次动画，§3.2-9）；收起搜索
                    // 时列表底部不跳，键盘 inset 逐帧跟手即可。
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .appHazeSource(hazeState),
                        contentPadding = PaddingValues(
                            start = PageGutter,
                            end = PageGutter,
                            top = CardInnerSpacing,
                            bottom = ToolbarReservedBottom + imeBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ListSpacing),
                    ) {
                        if (loading) {
                            item { LoadingState(message = stringResource(R.string.loading)) }
                        } else if (filteredApps.isEmpty()) {
                            item {
                                EmptyState(
                                    message = stringResource(hunoia.luno.design.R.string.no_matching_results),
                                )
                            }
                        } else {
                            sections.forEach { section ->
                                stickyHeader(key = "app_header_${section.letter}") {
                                    SheetGroupHeader(section.letter.toString())
                                }
                                item(key = "app_body_${section.letter}") {
                                    // LunoGroup 自动跟随全局大卡/分割：Segmented 时行各带圆角
                                    // 缝；CardDivided 时整节一张大卡、行间缩进分割线（页面不判风格）。
                                    LunoGroup {
                                        val sectionFlags = section.items.map {
                                            RecentApp.blockKey(it.packageName, it.userId) in selected
                                        }
                                        section.items.forEachIndexed { index, item ->
                                            val blockKey = RecentApp.blockKey(
                                                item.packageName,
                                                item.userId,
                                            )
                                            val iconKey = item.identity
                                            val icon = appIcons[iconKey]
                                            if (icon == null) {
                                                LaunchedEffect(iconKey) {
                                                    // 快甩全量列表时每行都会进这里：限流，别让几百行同时发起
                                                    // 图标加载（口径同 QuickLaunchContentState 的预热并发）。
                                                    iconLoadGate.withPermit {
                                                        val bitmap = withContext(Dispatchers.IO) {
                                                            item.getIcon(AppContext.get())
                                                                ?.let { it.toIconBitmap(iconPx) }
                                                        }
                                                        if (bitmap != null) appIcons[iconKey] = bitmap
                                                    }
                                                }
                                            }
                                            LunoSelectableRow(
                                                title = if (item.isClone) {
                                                    "${item.label} · ${stringResource(hunoia.luno.actions.R.string.app_clone_label)}"
                                                } else {
                                                    item.label
                                                },
                                                subtitle = item.packageName,
                                                iconBitmap = icon,
                                                selected = blockKey in selected,
                                                onSelectedChange = { isSelected ->
                                                    selected = if (isSelected) {
                                                        selected + blockKey
                                                    } else {
                                                        selected - blockKey
                                                    }
                                                },
                                                shape = selectedSegmentedShape(
                                                    index,
                                                    section.items.size,
                                                    sectionFlags,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 底部：一个玻璃胶囊（字母条 + 保存/搜索同胶囊内）。玻璃作装饰背景层
                    // （裁切），内容层叠加其上不被裁——索引条按压气泡朝上溢过胶囊。
                    // IME∪导航 inset 与 bottom padding 由外层 Column 统一承担。
                    // 字母在列时胶囊全宽（字母左 + 按钮右）；loading/空/search 时无字母，
                    // 胶囊随按钮收窄靠右。
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .swallowTouches()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                            )
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                            )
                            .padding(
                                start = PageGutter,
                                end = PageGutter,
                                bottom = BottomBarPadding,
                            ),
                        horizontalAlignment = Alignment.End,
                    ) {
                        // 搜索态 ↔ 常态：叠层交叉淡入淡出（FlClash 式）。搜索层与常态层同时组合，
                        // 视觉 alpha 由 searchAlpha 立即驱动；搜索层节点保留到键盘滑完（searchExpanded || imeVisible），
                        // 输入框不在下滑途中销毁 → 焦点/InputConnection 不断 → 键盘有完整下滑动画、不回弹。
                        val searchAlpha by animateFloatAsState(
                            targetValue = if (searchExpanded) 1f else 0f,
                            animationSpec = tween(MotionSwapIn),
                            label = "blocklistSearchAlpha",
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            if (!searchExpanded) {
                                // 常态层（玻璃胶囊，字母条 + 保存/搜索）。
                                Box(modifier = Modifier.graphicsLayer { alpha = 1f - searchAlpha }) {
                                    Box(
                                        modifier = Modifier
                                            .then(if (showIndex) Modifier.fillMaxWidth() else Modifier),
                                    ) {
                                        // 装饰背景层：裁切 + 玻璃视觉；内容层不被它裁（气泡需溢出不裁切）。
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .glassCapsuleBackground(hazeState),
                                        )
                                        // 内容层：内边距口径同 glassCapsule（TileInnerPadding），内里高 56。
                                        Row(
                                            modifier = Modifier
                                                .then(if (showIndex) Modifier.fillMaxWidth() else Modifier)
                                                .padding(TileInnerPadding)
                                                .height(BottomBarHeight),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (showIndex) {
                                                val activeKey = sections
                                                    .getOrNull(listState.firstVisibleItemIndex / 2)
                                                    ?.letter?.toString()
                                                IndexBar(
                                                    entries = barEntries,
                                                    metrics = null,
                                                    activeKey = activeKey,
                                                    side = IndexBarSide.Start,
                                                    onJump = { entry ->
                                                        val sectionIndex = sections.indexOfFirst {
                                                            it.letter.toString() == entry.key
                                                        }
                                                        if (sectionIndex >= 0) {
                                                            listState.requestScrollToItem(sectionIndex * 2)
                                                        }
                                                    },
                                                    orientation = IndexBarOrientation.Horizontal,
                                                    maxWindow = 5,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Spacer(modifier = Modifier.width(LetterBarCapsuleGap))
                                            }
                                            ToolbarNormalContent(
                                                onSave = { confirm() },
                                                onToggleSearch = { searchExpanded = true },
                                            )
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
                                            query = searchQuery,
                                            onQueryChange = { searchQuery = it },
                                            placeholder = stringResource(R.string.search_app_hint),
                                            onCancel = { closeSearch() },
                                            cancelContentDescription = stringResource(R.string.search_collapse),
                                            hazeState = hazeState,
                                            focusRequester = searchFocusRequester,
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
            }
        }

    }
}
}

/** 常态：保存 + 搜索（均图标按钮，与 AppControlToolbar 同一形态）。 */
@Composable
private fun ToolbarNormalContent(
    onSave: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PressIconButton(onClick = onSave) {
            Icon(
                Icons.Default.Save,
                contentDescription = stringResource(hunoia.luno.design.R.string.confirm),
            )
        }
        PressIconButton(onClick = onToggleSearch) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.search_app_hint),
            )
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

/** 行图标加载并发上限：快甩列表时每行一个协程，限流防瞬时几百路 PackageManager 调用。 */
private val iconLoadGate = Semaphore(ICON_LOAD_CONCURRENCY)
private const val ICON_LOAD_CONCURRENCY = 4

/**
 * 底部悬浮区让位：取搜索态（浮动胶囊 `DockCapsuleHeight + 2×BottomBarPadding`）与
 * 常态（玻璃胶囊 `BottomBarHeight + 2×TileInnerPadding + BottomBarPadding`）的较大值，
 * **固定不随搜索态切换跳变**——让位不做二次动画（IME inset 已逐帧，§3.2-9）。
 */
private val ToolbarReservedBottom =
    maxOf(
        DockCapsuleHeight + BottomBarPadding * 2,
        BottomBarHeight + TileInnerPadding * 2 + BottomBarPadding,
    )

/** 横向滑杆与悬浮胶囊之间的水平间隙。 */
private val LetterBarCapsuleGap = 12.dp
