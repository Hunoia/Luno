package hunoia.luno.ui.actionselect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectedActionSettings(
    selectedItems: List<Any>,
    longPressTargetIndex: Int?,
    itemLabel: (Any) -> String,
    reorderMode: Boolean,
    onReorderModeToggle: () -> Unit,
    onSetLongPress: (Int) -> Unit,
    onCancelLongPress: () -> Unit,
    onMoveSelected: (Int, Int) -> Unit,
    onRemoveItem: (Any) -> Unit,
    onClearAll: () -> Unit,
) {
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var tileWidth by remember { mutableStateOf(0f) }
    var tileHeight by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val spacingPx = with(density) { Spacing8.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing8)
            .animateContentSize(animationSpec = tween(AnimNormal.toInt())),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (longPressTargetIndex == null) {
                if (reorderMode) {
                    Text(
                        text = stringResource(R.string.reorder),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.clear_all))
                }
                TextButton(onClick = onReorderModeToggle) {
                    Text(
                        if (reorderMode) stringResource(R.string.done_reorder)
                        else stringResource(R.string.reorder)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.choose_long_press_action_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelLongPress) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ContentPaddingHorizontal * 2)
                .heightIn(max = 184.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing8),
            verticalArrangement = Arrangement.spacedBy(Spacing8),
        ) {
            items(
                count = selectedItems.size,
                key = { index -> selectedItems[index].toStableKey() },
            ) { globalIndex ->
                val item = selectedItems[globalIndex]
                val itemKey = item.toStableKey()
                val isDragged = draggingKey == itemKey
                SelectedTile(
                    modifier = if (isDragged) Modifier else Modifier.animateItem(),
                    item = item,
                    label = itemLabel(item),
                    longPressLabel = (item as? Action)?.longPressAction?.let { itemLabel(it) },
                    isLongPressTarget = longPressTargetIndex == globalIndex,
                    isDragged = isDragged,
                    dragOffset = if (isDragged) dragOffset else Offset.Zero,
                    reorderMode = reorderMode && longPressTargetIndex == null,
                    onSizeChanged = { w, h ->
                        if (tileWidth == 0f) tileWidth = w
                        if (tileHeight == 0f) tileHeight = h
                    },
                    onLongPress = {
                        if (reorderMode) {
                            draggingKey = itemKey
                            dragOffset = Offset.Zero
                        } else if (longPressTargetIndex == null) {
                            onSetLongPress(globalIndex)
                        }
                    },
                    onDrag = { amount ->
                        if (isDragged) {
                            dragOffset += amount
                            val tW = (tileWidth + spacingPx).coerceAtLeast(1f)
                            val tH = (tileHeight + spacingPx).coerceAtLeast(1f)
                            val colDelta = (dragOffset.x / tW).roundToInt()
                            val rowDelta = (dragOffset.y / tH).roundToInt()
                            val delta = rowDelta * 4 + colDelta
                            if (delta != 0) {
                                val currentIndex = selectedItems.indexOfFirst { it.toStableKey() == itemKey }
                                if (currentIndex >= 0) {
                                    val targetIndex = (currentIndex + delta).coerceIn(0, selectedItems.lastIndex)
                                    if (targetIndex != currentIndex) {
                                        onMoveSelected(currentIndex, targetIndex)
                                        val actualDelta = targetIndex - currentIndex
                                        val colAdjust = (actualDelta % 4) * tW
                                        val rowAdjust = (actualDelta / 4) * tH
                                        dragOffset -= Offset(colAdjust, rowAdjust)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingKey = null
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        draggingKey = null
                        dragOffset = Offset.Zero
                    },
                    onRemove = { onRemoveItem(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectedTile(
    modifier: Modifier = Modifier,
    item: Any,
    label: String,
    longPressLabel: String?,
    isLongPressTarget: Boolean,
    isDragged: Boolean,
    dragOffset: Offset,
    reorderMode: Boolean,
    onSizeChanged: (Float, Float) -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val action = item as? Action
    val icon = action?.let { actionIcon(it) }

    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val targetColor = when {
        isLongPressTarget -> MaterialTheme.colorScheme.primaryContainer
        isDragged -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val bgColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(AnimMedium.toInt()),
        label = "tileBg",
    )

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .onGloballyPositioned { coords ->
                val size = coords.size
                onSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .graphicsLayer {
                if (isDragged) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                }
            }
            .pointerInput(reorderMode) {
                if (reorderMode) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _: Offset -> currentOnLongPress() },
                        onDrag = { change, amount ->
                            change.consume()
                            currentOnDrag(amount)
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() },
                    )
                } else {
                    detectTapGestures(
                        onTap = { onRemove() },
                        onLongPress = { currentOnLongPress() },
                    )
                }
            },
        shape = CardShape,
        color = bgColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(Spacing24), contentAlignment = Alignment.Center) {
                when (icon) {
                    is ImageVector -> Image(
                        imageVector = icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (isLongPressTarget) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        ),
                    )
                    is String -> AsyncImage(
                        model = icon,
                        contentDescription = null,
                        imageLoader = context.imageLoader,
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.height(Spacing2))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isLongPressTarget) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
            )
            if (longPressLabel != null && !isLongPressTarget) {
                Spacer(Modifier.height(Spacing1))
                Text(
                    text = longPressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Any.toStableKey(): String = when (this) {
    is Action -> "${value}:${data}"
    else -> toString()
}
