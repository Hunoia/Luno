package hunoia.luno.quicklaunch.panel
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.key

@Composable
internal fun CandidateAppRows(
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    candidateHeight: Dp,
    iconSizePx: Int,
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
            iconSizePx = iconSizePx,
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
internal fun AppGridContent(
    apps: List<AppInfo>,
    disabledPkgs: Set<String>,
    gridState: LazyGridState,
    iconSizePx: Int,
    gridColumns: Int = 4,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = ContentBottom),
    userScrollEnabled: Boolean = true,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns),
        verticalArrangement = Arrangement.spacedBy(GridCellSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridCellSpacing),
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier.fillMaxSize(),
    ) {
        if (apps.isEmpty()) {
            item { Box(modifier = Modifier.height(EmptyStateHeight).fillMaxWidth()) }
        }
        items(apps, key = { it.key() }, contentType = { "app" }) { app ->
            val isDisabled = app.packageName in disabledPkgs
            AppItem(
                app = app,
                iconSizePx = iconSizePx,
                onClick = { onClick(app, isDisabled) },
                onLongPress = { onLongPress(app, isDisabled) },
            )
        }
    }
}