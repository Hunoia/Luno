package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.AppDrawerWidgetParams
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.ui.pinchToColumns
import hunoia.luno.quickaccess.panel.SectionedAppGridContent
import hunoia.luno.quickaccess.query.AppDrawerSections
import hunoia.luno.quickaccess.query.AppListRepository
import hunoia.luno.runtime.surface.SurfaceDispatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 抽屉网格的内边距：格子只有面板的一小块，留 4dp 让图标别贴边。 */
private val DrawerPadding = 4.dp

/** 常用区行数：顶部两行，取使用分最高的 `列数 × 2` 个。 */
private const val TopAppRows = 2

/** 解析每实例参数（JSON）：失败 / 未配置返回 null（与习惯 / 趋势部件同一口径）。 */
internal fun parseAppDrawerParams(params: String?): AppDrawerWidgetParams? {
    if (params.isNullOrBlank()) return null
    return runCatching { JsonSerializer.decodeFromString<AppDrawerWidgetParams>(params) }.getOrNull()
}

/**
 * 应用抽屉页面部件：⭐ 常用区（两行）+ 按首字母分组的全量应用，组内按使用频率排；
 * 触发侧边缘带字母滑杆（点击跳转 / 拖动扫过）。
 *
 * 复用关系：
 * - 应用列表来自进程级共享仓 [AppListRepository]（与快速启动器同一份，只扫一次包）；
 * - 排序与分组只在 [AppDrawerSections] 一处：先走 `AppSearch.sortApps`（空搜索词 =
 *   使用分 → 标题长度 → 字典序），再在已排好的序列上稳定切分，组内顺序天然是频率序；
 * - 网格复用 `SectionedAppGridContent`（与快速启动器共用同一套 LazyGrid 骨架与格子）。
 *
 * 列数：网格细胞里按自身 `spanX`；单功能面板整页按 [AppDrawerWidgetParams] 记忆的列数，
 * 未调过则沿用面板网格列数。**只有编辑态**（`enabled=false`）能双指捏合改列数——正常态
 * 单指手势全归滚动 / 点击，编辑态内容不可交互、字母滑杆也收起，捏合不会和它们抢事件。
 *
 * 禁用应用：网格面板没有 a11y 的启用回调，点按静默忽略（与「不可用即不可点」一致）。
 */
@Composable
fun AppDrawerCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 4,
    /** 编辑态=false：内部滚动与点按全禁，事件让给层根拖拽/缩放；此时才能捏合改列数。 */
    enabled: Boolean = true,
    /** 是否作为网格项渲染：false = 单功能面板整页（滑杆贴格子边缘即贴面板边缘）。 */
    isGridItem: Boolean = true,
    /** 每实例参数（JSON，[AppDrawerWidgetParams]）。网格项不读。 */
    params: String? = null,
    /** 参数写回入口；为 null 时捏合只能预览、不落盘（网格项路径）。 */
    onUpdateParams: ((String) -> Unit)? = null,
) {
    val closePanel = LocalGridPanelClose.current
    val panelAnchor = hunoia.luno.gridpanel.LocalGridPanelAnchor.current
    val appList by AppListRepository.state.collectAsState()
    val usageStats by hunoia.luno.domain.search.SearchCenter.appUsage.collectAsState(initial = AppUsageStats())
    val launcherSettings by hunoia.luno.domain.search.SearchCenter.launcher.collectAsState(initial = QuickAppLauncherSettings())
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 引用计数：部件离开组合就退订；多个部件/快速启动器同时在线只维持一份列表。
    DisposableEffect(Unit) {
        AppListRepository.acquire()
        onDispose { AppListRepository.release() }
    }
    // 系统语言变了 label 要重扫（与快速启动器同一判断）。
    val localeTag = configuration.locales[0].toLanguageTag()
    LaunchedEffect(localeTag) { AppListRepository.reloadIfLocaleChanged() }

    // 宿主给的默认列数：格子按 span，单功能面板按面板网格列数。
    val defaultColumns = spanX.coerceIn(
        GridPanelDefaults.MinColumnCount,
        GridPanelDefaults.MaxColumnCount,
    )
    // 捏合期间只改本地预览，松手才落盘（与面板自身调列数同一套语义）。
    var pinchColumns by remember { mutableStateOf<Int?>(null) }
    val savedColumns = if (isGridItem) {
        null
    } else {
        parseAppDrawerParams(params)?.columns(landscape)
    }
    val baseColumns = (savedColumns ?: defaultColumns).coerceIn(
        GridPanelDefaults.MinColumnCount,
        GridPanelDefaults.MaxColumnCount,
    )
    val columns = (pinchColumns ?: baseColumns).coerceIn(
        GridPanelDefaults.MinColumnCount,
        GridPanelDefaults.MaxColumnCount,
    )
    // 落盘的 params 回来后才撤掉预览：否则「松手 → 配置回写」之间会闪回旧列数。
    LaunchedEffect(params) {
        if (pinchColumns != null && savedColumns == pinchColumns) pinchColumns = null
    }

    val layout = remember(appList.apps, usageStats, columns) {
        AppDrawerSections.layout(appList.apps, usageStats, columns, TopAppRows)
    }

    fun launch(app: AppInfo, miniWindow: Boolean) {
        // 走 Surface 统一执行口：部件只构造动作，启动细节与三态由 Dispatcher 负责。
        SurfaceDispatch.dispatch(
            action = hunoia.luno.core.domain.AppLaunchAction.stored(
                packageName = app.packageName,
                className = app.className,
                miniWindow = miniWindow,
                userId = app.userId,
            ),
            origin = DispatchOrigin.PANEL,
            launchAnchor = panelAnchor,
        ) { launched ->
            if (launched) closePanel()
            else showToast(hunoia.luno.core.bridge.R.string.launch_failed)
        }
    }

    val gridState = rememberLazyGridState()
    // 抽屉每次重新打开都从第一行开始。
    // 不能只看「空 → 非空」：应用列表打开后还会从「无使用统计」重排成
    // 「按使用频率 + 分节」，LazyGrid 会跟着旧首项 key 下移，正表现为从第二行开始。
    // 所以按「应用列表 / 使用统计」复位——**不能按 layout**：捏合改列数时 layout 每帧都变，
    // 列表会被不停拉回顶部。
    LaunchedEffect(appList.apps, usageStats) {
        gridState.scrollToItem(0)
    }

    // 触发侧：触点落在屏幕左半 → 滑杆贴左边缘，拇指不用跨手。
    val density = LocalDensity.current
    val screenWidthPx = with(density) {
        configuration.screenWidthDp.dp.toPx()
    }
    val side = if (panelAnchor == null || panelAnchor.first < screenWidthPx / 2f) {
        LetterIndexSide.Start
    } else {
        LetterIndexSide.End
    }
    // 滑杆压在网格边缘：给内容补同侧内边距，图标不会被字母条盖住。
    // 单功能面板没有格子的圆角裁切，滑杆可以一直贴到自己的边缘。
    val indexInset = if (isGridItem) DrawerPadding else 0.dp
    val entries = layout.indexEntries
    val activeEntry by remember(layout) {
        derivedStateOf { layout.entryAt(gridState.firstVisibleItemIndex) }
    }
    val scope = rememberCoroutineScope()
    var jumpJob by remember { mutableStateOf<Job?>(null) }

    // 只有单功能面板整页、且宿主提供了参数写入口时才允许捏合。
    val canPinch = !isGridItem && !enabled && onUpdateParams != null
    // pointerInput(Unit) 的手势协程不随重组重启：必须让它读 State 才能拿到当前列数。
    val currentColumns = rememberUpdatedState(columns)

    BoxWithConstraints(
        modifier.fillMaxSize().pinchToColumns(
            enabled = canPinch,
            currentColumns = { currentColumns.value },
            onColumns = { target, final ->
                when {
                    !final -> pinchColumns = target.takeIf { it != baseColumns }
                    target == baseColumns -> pinchColumns = null
                    else -> {
                        val next = (parseAppDrawerParams(params) ?: AppDrawerWidgetParams())
                            .withColumns(landscape, target)
                        // 预览留到 params 回写成功（见上面的 LaunchedEffect）。
                        pinchColumns = target
                        onUpdateParams?.invoke(JsonSerializer.encodeToString(next))
                    }
                }
            },
        ),
    ) {
        // 尺寸实测（含大字体下的字高）：与滑杆自己算的是同一份，宽度不会错位。
        val indexMetrics = rememberLetterIndexMetrics(entries, maxHeight)
        val indexFits = enabled && indexMetrics.visible && entries.isNotEmpty()
        val indexWidth = if (indexFits) indexMetrics.barWidth else 0.dp
        val contentPadding = PaddingValues(
            start = DrawerPadding + if (side == LetterIndexSide.Start) indexWidth else 0.dp,
            end = DrawerPadding + if (side == LetterIndexSide.End) indexWidth else 0.dp,
            top = DrawerPadding,
            bottom = DrawerPadding,
        )
        SectionedAppGridContent(
            layout = layout,
            disabledPkgs = appList.disabledPkgs,
            gridState = gridState,
            gridColumns = columns,
            onClick = { app, isDisabled ->
                when {
                    !enabled -> Unit
                    // 禁用应用静默忽略会让人以为点坏了：给一句提示。
                    isDisabled -> showToast(R.string.app_drawer_disabled_app)
                    else -> launch(app, launcherSettings.tapOpensMiniWindow)
                }
            },
            onLongPress = { app, isDisabled ->
                when {
                    !enabled -> Unit
                    isDisabled -> showToast(R.string.app_drawer_disabled_app)
                    else -> launch(app, !launcherSettings.tapOpensMiniWindow)
                }
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            userScrollEnabled = enabled,
            enabled = enabled,
        )
        if (indexFits) {
            LetterIndexBar(
                entries = entries,
                metrics = indexMetrics,
                activeEntry = activeEntry,
                side = side,
                onJump = { entry ->
                    val index = layout.headerItemIndex(entry)
                    if (index >= 0) {
                        // 拖动时连续发跳转：只留最新一次，避免上一跳还在跑新跳又插队。
                        jumpJob?.cancel()
                        jumpJob = scope.launch { gridState.scrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(
                        if (side == LetterIndexSide.Start) Alignment.CenterStart else Alignment.CenterEnd,
                    )
                    .padding(horizontal = indexInset),
            )
        }
    }
}
