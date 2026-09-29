package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.key

@Composable
internal fun PageMatchIcon(
    name: String,
    iconHeight: Dp? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .let { if (iconHeight != null) it.height(iconHeight).fillMaxWidth() else it.fillMaxWidth().aspectRatio(1f) }
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (name) {
                    "密码" -> Icons.Default.Lock
                    else -> Icons.Default.Settings
                },
                contentDescription = name,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.width(28.dp).height(28.dp)
            )
        }
    }
}

@Composable
internal fun CandidateAppRows(
    pageMatches: List<Pair<String, Page>>,
    chunkedApps: List<List<AppInfo>>,
    disabledPkgs: Set<String>,
    candidateHeight: Dp,
    rows: Int = 1,
    onClick: (AppInfo, Boolean) -> Unit,
    onLongPress: (AppInfo, Boolean) -> Unit,
    onPageMatchClick: (Page) -> Unit,
) {
    val iconHeight = (candidateHeight - 2.dp * (rows - 1)) / rows - 4.dp * 2
    val rowState = rememberLazyListState()
    LaunchedEffect(chunkedApps) {
        rowState.animateScrollToItem(0)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(candidateHeight)
    ) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (pageMatches.isNotEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.width(64.dp)
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
            items(chunkedApps, key = { chunk -> chunk.firstOrNull()?.key() ?: "empty" }) { columnApps ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.width(64.dp)
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
    pageMatches: List<Pair<String, Page>>,
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
    onPageMatchClick: (Page) -> Unit,
) {
    val view = LocalView.current

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
                            if (totalDrag > 32f) onExpandKeyboard()
                        }
                    }
                )
            }
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
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
                item { Box(modifier = Modifier.height(88.dp).fillMaxWidth()) }
            }
            items(apps, key = { it.key() }, contentType = { "app" }) { app ->
                val isDisabled = app.packageName in disabledPkgs
                AppItem(
                    app = app,
                    onClick = { onClick(app, isDisabled) },
                    onLongPress = { onLongPress(app, isDisabled) }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .pointerInput(keyboardExpanded) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            if (!keyboardExpanded && totalDrag > 32f) onExpandKeyboard()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onExpandKeyboard()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                            .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }
        }
    }
}
