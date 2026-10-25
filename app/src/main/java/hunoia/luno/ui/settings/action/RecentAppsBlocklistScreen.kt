package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.core.AppContext
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.getIcon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.component.LunoSearchField
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.screen.LunoScreenPlain
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.EmptyStatePaddingVertical
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.appHazeSource
import hunoia.luno.ui.theme.glassCapsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 最近应用黑名单（全屏版应用多选）。
 *
 * 选择只保存在本地草稿；点右下角 FAB 才落盘。直接返回（含系统返回键）不写配置，即取消。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentAppsBlocklistScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    // 以当前配置为草稿初值；用户改动不写盘，确认时才提交。
    var selected by remember(actionSettings.recentAppsBlocklist) {
        mutableStateOf(actionSettings.recentAppsBlocklist.toSet())
    }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    // 图标位图随屏幕存活：LazyColumn 条目滚出视口即销毁，进程级 AppIconCache 容量有限，
    // 全量应用列表滚动时头部条目被 LRU 挤出 → 回滚异步重载 → 图标消失-显示来回闪。
    // 按行惰性加载进这张屏幕级 map，行渲染同步命中，滚动回看不再闪。
    val appIcons = remember { mutableStateMapOf<String, ImageBitmap>() }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // 进入页面只重排一次：配置与列表都就绪后，把已选项排到前面。
    var initialOrderApplied by remember { mutableStateOf(false) }
    val permissionState = rememberGetInstalledAppsPermissionState { granted ->
        if (granted) loaded = false
    }

    suspend fun loadApps() {
        loading = true
        apps = withContext(Dispatchers.IO) {
            QuickLaunchFacade.queryAppsIncludingDisabled(AppContext.get())
        }
        loading = false
    }

    // 配置可能是异步到达的：等 apps 与已选列表都就绪后再排一次，且只排一次。
    LaunchedEffect(apps, actionSettings.recentAppsBlocklist) {
        if (!initialOrderApplied && apps.isNotEmpty()) {
            val selectedPackages = actionSettings.recentAppsBlocklist.toSet()
            apps = apps.sortedByDescending { it.packageName in selectedPackages }
            initialOrderApplied = true
        }
    }

    LaunchedEffect(permissionState.isGranted, loaded) {
        if (!permissionState.isGranted) {
            permissionState.launchPermissionRequest()
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

    fun confirm() {
        val ordered = actionSettings.recentAppsBlocklist.filter { it in selected } +
            selected.filter { it !in actionSettings.recentAppsBlocklist }
        scope.launch {
            TuneConfig.updateAction { it.copy(recentAppsBlocklist = ordered) }
            onBack()
        }
    }

    LunoScreenPlain(
        title = stringResource(R.string.recent_apps_blocklist),
        onBack = onBack,
        actions = {
            if (permissionState.isGranted) {
                LunoIconButton(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.reset),
                    onClick = { selected = emptySet() },
                )
            }
        },
    ) { topPadding, hazeState ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
        ) {
            if (!permissionState.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = EmptyStatePaddingVertical),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { permissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.request_get_apps_permission))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 列表是 hazeSource：底部搜索胶囊作为兄弟节点悬浮其上、采列表做毛玻璃
                    // （ui-guidelines §5，与动作选择页底栏同一层次关系）。
                    val selectionFlags = filteredApps.map { it.packageName in selected }
                    val iconPx = with(LocalDensity.current) { RowIconSize.toPx() }.toInt()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .appHazeSource(hazeState),
                        contentPadding = PaddingValues(
                            start = PageGutter,
                            end = PageGutter,
                            top = CardInnerSpacing,
                            bottom = SearchFabCapsuleReservedBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                    ) {
                        if (loading) {
                            item { LoadingState(message = stringResource(R.string.loading)) }
                        } else if (filteredApps.isEmpty()) {
                            item { EmptyState(message = stringResource(R.string.no_matching_results)) }
                        } else {
                            itemsIndexed(filteredApps, key = { _, item -> item.qualifiedName }) { index, item ->
                                val iconKey = item.qualifiedName
                                val icon = appIcons[iconKey]
                                if (icon == null) {
                                    LaunchedEffect(iconKey) {
                                        // 快甩全量列表时每行都会进这里：限流，别让几百行同时发起
                                        // 图标加载（口径同 QuickAppLauncherState 的预热并发）。
                                        iconLoadGate.withPermit {
                                            val bitmap = withContext(Dispatchers.IO) {
                                                item.getIcon(AppContext.get())?.let { it.toIconBitmap(iconPx) }
                                            }
                                            if (bitmap != null) appIcons[iconKey] = bitmap
                                        }
                                    }
                                }
                                LunoSelectableRow(
                                    title = item.label,
                                    subtitle = item.packageName,
                                    iconBitmap = icon,
                                    selected = item.packageName in selected,
                                    onSelectedChange = { isSelected ->
                                        selected = if (isSelected) {
                                            selected + item.packageName
                                        } else {
                                            selected - item.packageName
                                        }
                                    },
                                    shape = selectedSegmentedShape(index, filteredApps.size, selectionFlags),
                                )
                            }
                        }
                    }
                    // 底部悬浮区（FAB 叠在搜索胶囊上方，层叠同主页 FAB/底栏）：
                    // inset 取导航栏 ∪ 键盘 —— 键盘弹起时胶囊带着 FAB 一起抬上来，
                    // 搜索框不会被键盘压住（相加会多顶一截，见动作选择页底栏注释）。
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = BottomBarPadding)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 与 LunoFab 同口径：全 app 的 FAB 都无 ripple（反馈靠容器色/图标）。
                        CompositionLocalProvider(LocalRippleConfiguration provides null) {
                            FloatingActionButton(
                                onClick = { confirm() },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(end = PageGutter, bottom = CardInnerSpacing)
                                    .size(FabSize),
                                shape = FloatingActionButtonDefaults.shape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = stringResource(R.string.confirm),
                                )
                            }
                        }
                        // 常驻搜索胶囊：与动作选择页底栏同一套玻璃样式（glassCapsule），
                        // 无展开/收起；进页不自动聚焦（focusRequester 不传），点框才弹键盘。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PageGutter)
                                .glassCapsule(hazeState),
                        ) {
                            LunoSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = stringResource(R.string.search_app_hint),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(BottomBarHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** FAB 尺寸（与 LunoFab/主页一致）。 */
private val FabSize = 64.dp

/** 行图标加载并发上限：快甩列表时每行一个协程，限流防瞬时几百路 PackageManager 调用。 */
private val iconLoadGate = Semaphore(ICON_LOAD_CONCURRENCY)
private const val ICON_LOAD_CONCURRENCY = 4

/** 底部悬浮区让位：FAB + 间距 + 胶囊本高（BottomBarHeight + 2×TileInnerPadding）+ BottomBarPadding。 */
private val SearchFabCapsuleReservedBottom =
    FabSize + CardInnerSpacing + BottomBarHeight + TileInnerPadding * 2 + BottomBarPadding

/** Drawable → 行首图标位图，只缩不放（同 SearchIconCache 口径）；取不到返回 null，该行无图标。 */
private fun Drawable.toIconBitmap(sizePx: Int): ImageBitmap? {
    val intrinsic = maxOf(intrinsicWidth, intrinsicHeight)
    if (intrinsic <= 0 || sizePx <= 0) return null
    val side = minOf(sizePx, intrinsic)
    // 源图不大于目标尺寸：直接共用原位图，不放大重画。
    if (this is BitmapDrawable && side >= intrinsic) return bitmap?.asImageBitmap()
    // 位图分配/绘制失败只损失一个行图标，不该带崩整页。
    return runCatching {
        setBounds(0, 0, side, side)
        Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            .also { draw(Canvas(it)) }
            .asImageBitmap()
    }.getOrNull()
}
