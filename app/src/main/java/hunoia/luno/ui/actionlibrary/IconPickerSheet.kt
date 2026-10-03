package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.ui.theme.SmallShape

/**
 * 更换图标面板：无标题、无搜索、无底部按钮，网格撑满剩余高度，
 * 底部固定一行自定义文字/表情输入。点选即生效并关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    currentKey: String?,
    defaultIcon: ImageVector,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isCustomText = currentKey != null && IconPalette.byKey(currentKey) == null
    var customText by remember(currentKey) {
        mutableStateOf(if (isCustomText) currentKey.orEmpty() else "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = PageGutter),
            contentPadding = PaddingValues(bottom = CardInnerSpacing),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            IconPalette.groups.forEachIndexed { groupIndex, group ->
                item(key = "group_${group.title}", span = { GridItemSpan(maxLineSpan) }) {
                    SheetGroupHeader(group.title)
                }
                if (groupIndex == 0) {
                    item(key = "default") {
                        IconTile(
                            vector = defaultIcon,
                            label = stringResource(R.string.action_library_icon_default),
                            selected = currentKey == null,
                            onClick = { onPick(null) },
                        )
                    }
                }
                items(items = group.icons, key = { it.key }) { icon ->
                    IconTile(
                        vector = icon.vector,
                        label = icon.label,
                        selected = currentKey == icon.key,
                        onClick = { onPick(icon.key) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.action_library_icon_custom)) },
                shape = SmallShape,
            )
            FilledTonalButton(
                onClick = {
                    val t = customText.trim()
                    if (t.isNotEmpty()) onPick(t)
                },
            ) {
                Text(stringResource(R.string.action_library_icon_use))
            }
        }
    }
}

@Composable
private fun IconTile(
    vector: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = CardInnerSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = vector,
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}