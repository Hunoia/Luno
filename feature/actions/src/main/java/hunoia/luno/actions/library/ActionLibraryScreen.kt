package hunoia.luno.actions.library
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
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
import hunoia.luno.actions.R
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.ui.component.row.LunoSectionTitle
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.page.LunoPageList
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.actions.resolvedIcon
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
@Composable
fun ActionLibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    vm: ActionLibraryVM = viewModel(),
    onNavToEdit: (String, String?) -> Unit = { _, _ -> },
) {
    val uiState by vm.uiState.collectAsState()
    var iconPickerEntry by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    val filtered = remember(uiState.entries) {
        uiState.entries.sortedBy { entry -> libraryCategoryOf(entry.typeId).ordinal }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { entry -> libraryCategoryOf(entry.typeId) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
            LunoPageList(state = listState) {
                lazy {
                if (filtered.isEmpty()) {
                    item { EmptyState(stringResource(R.string.action_library_empty)) }
                } else {
                    grouped.forEach { (category, entries) ->
                        stickyHeader(key = "header_${category}") {
                            Box(modifier = Modifier.animateItem()) {
                                LunoSectionTitle(stringResource(category.displayNameRes))
                            }
                        }
                        item(key = "group_${category}") {
                            LunoGroup(modifier = Modifier.animateItem()) {
                                entries.forEachIndexed { index, entry ->
                                    ActionLibraryItem(
                                        shape = segmentedShape(index, entries.size),
                                        entry = entry,
                                        referenceCount = uiState.referenceCounts[entry.id] ?: 0,
                                        onClick = { onNavToEdit(entry.id, null) },
                                        onIconClick = { iconPickerEntry = entry },
                                    )
                                }
                            }
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
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val def = ActionDefinitions.byTypeId(entry.typeId)
    val icon = entry.resolvedIcon() ?: Icons.Default.Build
    // 副标题 = 已配置的参数值（空值跳过，布尔 false 不喘）；无参数回退类型名。
    val paramSummary = remember(entry, context) { actionLibraryParamSummary(entry, context) }
    LunoSettingsRow(
        title = entry.name.ifBlank { def?.name ?: entry.typeId },
        subtitle = paramSummary.ifBlank { (def?.name ?: entry.typeId).takeIf { entry.name.isNotBlank() } ?: "" },
        leading = {
            val clickMod = Modifier
                .size(RowIconSize)
                // 行本身是 LunoSettingsRow（自己带缩放）；图标单独可点，单独挂反馈。
                .pressClickable(onClick = onIconClick)
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