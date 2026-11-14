package hunoia.luno.quickaccess.panel
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.quickaccess.R
import hunoia.luno.quickaccess.query.AppDrawerLayout
import hunoia.luno.quickaccess.query.AppSearch.key

@Composable
internal fun CandidateAppRows(
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    candidateHeight: Dp,
    rows: Int = 1,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
) {
    val iconHeight = (candidateHeight - CandidateRowSpacing * (rows - 1)) / rows - AppItemPadding * 2
    val rowState = rememberLazyListState()
    LaunchedEffect(apps, rows) {
        rowState.animateScrollToItem(0)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(candidateHeight)
    ) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(CandidateRowSpacing),
            modifier = Modifier.fillMaxSize()
        ) {
            val columns = (apps.size + rows - 1) / rows
            items(columns, key = { i -> apps.getOrNull(i * rows)?.key() ?: "empty" }) { i ->
                val start = i * rows
                val end = minOf(start + rows, apps.size)
                val columnApps = apps.subList(start, end)
                Column(
                    verticalArrangement = Arrangement.spacedBy(CandidateRowSpacing),
                    modifier = Modifier.width(CandidateColumnWidth)
                ) {
                    columnApps.forEach { app ->
                        key(app.key()) {
                            val isDisabled = app.packageName in disabledPkgs
                            AppItem(
                                app = app,
                                iconHeight = iconHeight,
                                onClick = { onClick(app, isDisabled) },
                                onLongPress = { onLongPress(app, isDisabled) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppGrid(
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    gridState: LazyGridState,
    gridAtTop: Boolean,
    keyboardExpanded: Boolean,
    gridHeight: Dp,
    gridColumns: Int = 4,
    onExpandKeyboard: () -> Unit,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .height(gridHeight)
            .fillMaxWidth()
            .pointerInput(gridAtTop) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        if (gridAtTop && dragAmount > 0f) {
                            change.consume()
                            totalDrag += dragAmount
                            if (totalDrag > DragThresholdPx) onExpandKeyboard()
                        }
                    }
                )
            }
    ) {
        AppGridContent(
            apps = apps,
            disabledPkgs = disabledPkgs,
            gridState = gridState,
            gridColumns = gridColumns,
            onClick = onClick,
            onLongPress = onLongPress,
            modifier = Modifier.fillMaxSize(),
        )

        Pill(
            onClick = { onExpandKeyboard() },
            onDragUp = { onExpandKeyboard() },
            dragEnabled = !keyboardExpanded,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 纯应用网格（无窗口/键盘耦合）：快速启动器与网格面板的应用抽屉部件共用。
 * 键盘上拉、底部药丸、面板几何留给各自的宿主（见 [AppGrid]）。
 */
@Composable
fun AppGridContent(
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    gridState: LazyGridState,
    gridColumns: Int = 4,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = ContentBottom),
    userScrollEnabled: Boolean = true,
    /** 编辑态 false：项不挂点击/长按，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
) {
    AppGridScaffold(
        gridState = gridState,
        gridColumns = gridColumns,
        onClick = onClick,
        onLongPress = onLongPress,
        modifier = modifier,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        enabled = enabled,
    ) {
        if (apps.isEmpty()) {
            item { Box(modifier = Modifier.height(EmptyStateHeight).fillMaxWidth()) }
        }
        items(apps, key = { it.key() }, contentType = { "app" }) { app ->
            AppGridCell(app, disabledPkgs, onClick, onLongPress, enabled)
        }
    }
}

/**
 * 分节应用网格（应用抽屉专属）：顶部常用区 + 字母分节，组头占满整行。
 *
 * 与 [AppGridContent] 共用同一套 LazyGrid 骨架与格子，因此命令不加第二份网格实现。
 * 组头**不吸顶**——`LazyGrid` 没有 sticky header，改用 LazyColumn 就得复制一份
 * 网格布局，代价大于收益。
 *
 * item 序列必须与 [AppDrawerLayout] 的下标契约一致，否则字母滑杆会跳错位置。
 */
@Composable
fun SectionedAppGridContent(
    layout: AppDrawerLayout,
    disabledPkgs: Set<String>,
    gridState: LazyGridState,
    gridColumns: Int = 4,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = ContentBottom),
    userScrollEnabled: Boolean = true,
    /** 编辑态 false：项不挂点击/长按，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
) {
    AppGridScaffold(
        gridState = gridState,
        gridColumns = gridColumns,
        onClick = onClick,
        onLongPress = onLongPress,
        modifier = modifier,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        enabled = enabled,
    ) {
        if (layout.sections.isEmpty()) {
            item { Box(modifier = Modifier.height(EmptyStateHeight).fillMaxWidth()) }
        }
        if (layout.topApps.isNotEmpty()) {
            // 常用区只用一颗星标认，不加文字标题（与字母组头同一套留白）。
            item(
                key = "section_favorites",
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "section" },
            ) {
                AppTopAppsHeader()
            }
        }
        items(layout.topApps, key = { "top_${it.key()}" }, contentType = { "app" }) { app ->
            AppGridCell(app, disabledPkgs, onClick, onLongPress, enabled)
        }
        layout.sections.forEach { section ->
            item(
                key = "section_${section.letter}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "section" },
            ) {
                AppSectionHeader(section.letter)
            }
            // 常用区里的应用仍留在本节（否则字母节里搜不到它），所以同一个应用会出现两次：
            // key 必须带区域前缀，否则 LazyGrid 会因重复 key 直接抛异常。
            items(
                section.apps,
                key = { "sec_${section.letter}_${it.key()}" },
                contentType = { "app" },
            ) { app ->
                AppGridCell(app, disabledPkgs, onClick, onLongPress, enabled)
            }
        }
    }
}

/** 平铺 / 分节两种网格的唯一骨架。 */
@Composable
private fun AppGridScaffold(
    gridState: LazyGridState,
    gridColumns: Int,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    userScrollEnabled: Boolean,
    enabled: Boolean,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns),
        verticalArrangement = Arrangement.spacedBy(GridCellSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridCellSpacing),
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
private fun AppGridCell(
    app: AppInfo,
    disabledPkgs: Set<String>,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    enabled: Boolean,
) {
    val isDisabled = app.packageName in disabledPkgs
    AppItem(
        app = app,
        onClick = { onClick(app, isDisabled) },
        onLongPress = { onLongPress(app, isDisabled) },
        enabled = enabled,
    )
}

/** 常用区组头：只有一颗星，语义靠 contentDescription 给读屏。 */
@Composable
private fun AppTopAppsHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppItemPadding,
                top = AppSectionHeaderTopPadding,
                bottom = AppSectionHeaderBottomPadding,
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.app_drawer_favorites),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppSectionHeaderIconSize),
        )
    }
}

/** 字母组头：只报字母，不画底、不吸顶，靠留白与格子拉开层次。 */
@Composable
private fun AppSectionHeader(letter: Char) {
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppItemPadding,
                top = AppSectionHeaderTopPadding,
                bottom = AppSectionHeaderBottomPadding,
            ),
    )
}