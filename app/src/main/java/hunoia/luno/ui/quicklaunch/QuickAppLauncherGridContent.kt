package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.key

/**
 * 设置 / 密码页入口。压平后与普通应用图标同构：不再有圆角底色方块，
 * 靠图标本身（齿轮 / 锁）区分，矢量图标也不需要 clip。
 */
@Composable
internal fun PageMatchIcon(
    name: String,
    iconHeight: Dp? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppItemPadding)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .let { if (iconHeight != null) it.height(iconHeight).fillMaxWidth() else it.fillMaxWidth().aspectRatio(1f) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (name) {
                    "密码" -> Icons.Default.Lock
                    else -> Icons.Default.Settings
                },
                contentDescription = name,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(PageMatchIconSize).height(PageMatchIconSize)
            )
        }
    }
}

@Composable
internal fun CandidateAppRows(
    pageMatches: List<Pair<String, Page>>,
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    candidateHeight: Dp,
    iconSizePx: Int,
    rows: Int = 1,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    onPageMatchClick: (Page) -> Unit,
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
            if (pageMatches.isNotEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(CandidateRowSpacing),
                        modifier = Modifier.width(CandidateColumnWidth)
                    ) {
                        pageMatches.forEach { (name, page) ->
                            PageMatchIcon(
                                name = name,
                                iconHeight = iconHeight,
                                onClick = { onPageMatchClick(page) }
                            )
                        }
                    }
                }
            }
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
                                iconSizePx = iconSizePx,
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
    pageMatches: List<Pair<String, Page>>,
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    gridState: LazyGridState,
    gridAtTop: Boolean,
    keyboardExpanded: Boolean,
    gridHeight: Dp,
    iconSizePx: Int,
    gridColumns: Int = 4,
    onExpandKeyboard: () -> Unit,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    onPageMatchClick: (Page) -> Unit,
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
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            verticalArrangement = Arrangement.spacedBy(GridCellSpacing),
            horizontalArrangement = Arrangement.spacedBy(GridCellSpacing),
            contentPadding = PaddingValues(bottom = ContentBottom),
            modifier = Modifier.fillMaxSize()
        ) {
            if (pageMatches.isNotEmpty()) {
                pageMatches.forEach { (name, page) ->
                    item {
                        PageMatchIcon(
                            name = name,
                            onClick = { onPageMatchClick(page) }
                        )
                    }
                }
            }
            if (apps.isEmpty()) {
                item { Box(modifier = Modifier.height(EmptyStateHeight).fillMaxWidth()) }
            }
            items(apps, key = { it.key() }, contentType = { "app" }) { app ->
                val isDisabled = app.packageName in disabledPkgs
                AppItem(
                    app = app,
                    iconSizePx = iconSizePx,
                    onClick = { onClick(app, isDisabled) },
                    onLongPress = { onLongPress(app, isDisabled) }
                )
            }
        }

        Pill(
            onClick = { onExpandKeyboard() },
            onDragUp = { onExpandKeyboard() },
            dragEnabled = !keyboardExpanded,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}