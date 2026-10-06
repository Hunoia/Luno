package hunoia.luno.ui.component.sheets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.SheetFooterActions
import hunoia.luno.ui.component.sheet.SheetHeader
/** 触钮多选表（动作库 ButtonSelectorMulti 参数用）：行点选开关 + 确认回填 id 数组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonPickerSheet(
    onDismissRequest: () -> Unit,
    buttons: List<GestureButton>,
    selectedIds: List<String>,
    onConfirm: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(selectedIds.toSet()) }
    var query by remember { mutableStateOf("") }
    val indexed = remember(buttons) { buttons.mapIndexed { index, button -> index to button } }
    val filtered = remember(query, indexed) {
        if (query.isBlank()) indexed
        else indexed.filter { (_, b) ->
            b.name.contains(query, ignoreCase = true) ||
                b.id.contains(query, ignoreCase = true)
        }
    }
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            SheetHeader(title = stringResource(R.string.condition_scope))
            if (buttons.isEmpty()) {
                Text(
                    text = stringResource(R.string.automation_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                )
            } else {
                AppSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
                    placeholder = stringResource(R.string.search_hint_all),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetListMaxHeight)
                        .padding(horizontal = PageGutter),
                    contentPadding = PaddingValues(bottom = ContentBottom),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    itemsIndexed(filtered, key = { _, (_, b) -> b.id }) { _, (index, button) ->
                        val label = button.name.ifBlank {
                            stringResource(R.string.gesture_button_name, index + 1)
                        }
                        LunoGroup {
                            LunoSettingsRow(
                                title = label,
                                subtitle = button.id,
                                onClick = {
                                    selected = if (button.id in selected) selected - button.id
                                    else selected + button.id
                                },
                                leading = {
                                    Checkbox(
                                        checked = button.id in selected,
                                        onCheckedChange = null,
                                    )
                                },
                                shape = segmentedShape(0, 1),
                            )
                        }
                    }
                }
                SheetFooterActions(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        onConfirm(buttons.map { it.id }.filter { it in selected })
                        onDismissRequest()
                    },
                )
            }
        }
    }
}