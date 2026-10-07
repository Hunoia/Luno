package hunoia.luno.ui.component.sheet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
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
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.SheetFooterActions
import hunoia.luno.ui.component.sheet.SheetHeader
import hunoia.luno.ui.component.sheet.SheetSearchList
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
                val selectionFlags = filtered.map { (_, b) -> b.id in selected }
                SheetSearchList(
                    query = query,
                    onQueryChange = { query = it },
                ) {
                    itemsIndexed(filtered, key = { _, (_, b) -> b.id }) { position, (index, button) ->
                        val label = button.name.ifBlank {
                            stringResource(R.string.gesture_button_name, index + 1)
                        }
                        LunoSelectableRow(
                            title = label,
                            subtitle = button.id,
                            selected = button.id in selected,
                            onSelectedChange = { checked ->
                                selected = if (checked) selected + button.id
                                else selected - button.id
                            },
                            shape = selectedSegmentedShape(
                                position,
                                filtered.size,
                                selectionFlags,
                            ),
                        )
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