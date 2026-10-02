package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.ui.theme.SmallShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    currentKey: String?,
    defaultIcon: ImageVector,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filteredGroups = remember(query) {
        if (query.isBlank()) {
            IconPalette.groups
        } else {
            IconPalette.groups.map { group ->
                group.copy(icons = group.icons.filter { IconPalette.matchesQuery(it, query) })
            }.filter { it.icons.isNotEmpty() }
        }
    }
    val hasIconResult = filteredGroups.any { it.icons.isNotEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.action_library_icon_pick),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
            )
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_hint_all),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
            )

            if (!hasIconResult && query.isNotBlank()) {
                EmptyState(message = stringResource(R.string.no_matching_results))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetListMaxHeight)
                        .padding(horizontal = PageGutter),
                    contentPadding = PaddingValues(bottom = ContentBottom),
                    verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                    horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DefaultIconRow(
                            icon = defaultIcon,
                            selected = currentKey == null,
                            onClick = { onPick(null) },
                        )
                    }
                    filteredGroups.forEach { group ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = CardInnerSpacing, bottom = CardInnerSpacing),
                            )
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
            }

            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun DefaultIconRow(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = CardInnerSpacing, vertical = CardInnerSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(CardInnerSpacing))
        Text(
            text = stringResource(R.string.action_library_icon_default),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.onPrimaryContainer,
            )
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        )
    }
}
