package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.theme.SheetTopShape

/** 触钮多选表（动作库 ButtonSelectorMulti 参数用）：行点选开关 + 确认回填 id 数组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonPickerSheet(
    onDismissRequest: () -> Unit,
    buttons: List<GestureButton>,
    selectedIds: List<String>,
    onConfirm: (List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(selectedIds.toSet()) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.condition_scope),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (buttons.isEmpty()) {
                Text(
                    text = stringResource(R.string.automation_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SheetListMaxHeight)
                        .padding(horizontal = PageGutter),
                    contentPadding = PaddingValues(bottom = ContentBottom),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    itemsIndexed(buttons, key = { _, b -> b.id }) { index, button ->
                        val label = button.name.ifBlank {
                            stringResource(R.string.gesture_button_name, index + 1)
                        }
                        SegmentedGroup {
                            SegmentedSettingsRow(
                                title = label,
                                subtitle = button.id,
                                onClick = {
                                    selected = if (button.id in selected) selected - button.id
                                    else selected + button.id
                                },
                                leadingContent = {
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
                FilledTonalButton(
                    onClick = {
                        onConfirm(buttons.map { it.id }.filter { it in selected })
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
