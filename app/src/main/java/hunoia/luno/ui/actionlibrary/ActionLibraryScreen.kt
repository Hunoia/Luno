package hunoia.luno.ui.actionlibrary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.ActionLibraryEntry
import hunoia.luno.action.definitions.ActionCategory
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.resolvedIcon
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
@Composable
fun ActionLibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    vm: ActionLibraryVM = viewModel(),
    onNavToEdit: (String, String?) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(),
) {
    val uiState by vm.uiState.collectAsState()
    var iconPickerEntry by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    val filtered = remember(uiState.entries) {
        uiState.entries
            .sortedBy { ActionDefinitions.categoryOrder(ActionDefinitions.byTypeId(it.typeId)?.category ?: ActionCategory.ADVANCED) }
    }
    val grouped = remember(filtered) { filtered.groupBy { ActionDefinitions.byTypeId(it.typeId)?.category ?: ActionCategory.ADVANCED } }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = FloatingContentBottom),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState(stringResource(R.string.action_library_empty)) }
                } else {
                    grouped.forEach { (category, entries) ->
                        stickyHeader(key = "header_${category}") {
                            Text(
                                text = stringResource(category.displayNameRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(start = PageGutter, top = CardInnerSpacing, bottom = ListSpacing),
                            )
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                            ActionLibraryItem(
                                modifier = Modifier.animateItem(),
                                shape = segmentedShape(index, entries.size),
                                entry = entry,
                                referenceCount = uiState.referenceCounts[entry.id] ?: 0,
                                onClick = {
                                    onNavToEdit(entry.id, null)
                                },
                                onIconClick = { iconPickerEntry = entry },
                            )
                        }
                    }
                }
            }
        }
        iconPickerEntry?.let { entry ->
            val def = ActionDefinitions.byTypeId(entry.typeId)
            IconPickerSheet(
                onDismissRequest = { iconPickerEntry = null },
                onPick = { iconKey ->
                    vm.updateIcon(entry.id, iconKey)
                    iconPickerEntry = null
                },
                currentKey = entry.iconKey,
                defaultIcon = def?.icon ?: Icons.Default.Build,
            )
        }
    }
}
@Composable
private fun ActionLibraryItem(
    shape: Shape,
    entry: ActionLibraryEntry,
    referenceCount: Int,
    onClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val def = ActionDefinitions.byTypeId(entry.typeId)
    val icon = entry.resolvedIcon() ?: Icons.Default.Build
    // 副标题 = 已配置的参数值（空值跳过，布尔 false 不喘）；无参数回退类型名。
    val paramSummary = remember(entry) {
        entry.params.entries.mapNotNull { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            when {
                text == null -> null
                text == "false" -> null
                key == "useTriggerPoint" -> null
                else -> text
            }
        }.joinToString(" · ")
    }
    LunoSettingsRow(
        modifier = modifier,
        title = entry.name.ifBlank { def?.name ?: entry.typeId },
        subtitle = paramSummary.ifBlank { (def?.name ?: entry.typeId).takeIf { entry.name.isNotBlank() } ?: "" },
        leading = {
            val clickMod = Modifier
                .size(RowIconSize)
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onIconClick,
                )
            when (icon) {
                is TextIcon -> TextIconGlyph(
                    text = icon.text,
                    size = RowIconSize,
                    modifier = clickMod,
                )
                else -> Icon(
                    modifier = clickMod,
                    imageVector = icon as ImageVector,
                    contentDescription = null,
                )
            }
        },
        shape = shape,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.action_library_reference_count, referenceCount),
                    style = MaterialTheme.typography.labelSmall,
                )
                Icon(
                    modifier = Modifier.size(RowIconSize).padding(start = TileInnerPadding),
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        },
    )
}