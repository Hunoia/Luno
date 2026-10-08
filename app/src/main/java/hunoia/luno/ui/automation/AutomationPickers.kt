package hunoia.luno.ui.automation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionTypePickerSheet(
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
    /** IF 步骤条件不允许 MANUAL（恒假死分支），选择页传 false 隐藏手动分组。 */
    includeManual: Boolean = true,
) {
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
                contentPadding = PaddingValues(bottom = ContentBottom),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                item(key = "group_level") {
                    SheetGroupHeader(stringResource(R.string.condition_level_group))
                }
                itemsIndexed(items = LEVEL_CONDITION_TYPES, key = { _, spec -> "type_${spec.type}" }) { index, spec ->
                    LunoSettingsRow(
                        title = stringResource(spec.labelRes),
                        onClick = {
                            onPick(spec.type)
                            onDismissRequest()
                        },
                        shape = segmentedShape(index, LEVEL_CONDITION_TYPES.size),
                    )
                }
                item(key = "group_event") {
                    SheetGroupHeader(stringResource(R.string.condition_event_group))
                }
                itemsIndexed(items = EVENT_CONDITION_TYPES, key = { _, spec -> "type_${spec.type}" }) { index, spec ->
                    LunoSettingsRow(
                        title = stringResource(spec.labelRes),
                        onClick = {
                            onPick(spec.type)
                            onDismissRequest()
                        },
                        shape = segmentedShape(index, EVENT_CONDITION_TYPES.size),
                    )
                }
                if (includeManual) {
                    item(key = "group_manual") {
                        SheetGroupHeader(stringResource(R.string.condition_manual_group))
                    }
                    itemsIndexed(items = MANUAL_CONDITION_TYPES, key = { _, spec -> "type_${spec.type}" }) { index, spec ->
                        LunoSettingsRow(
                            title = stringResource(spec.labelRes),
                            subtitle = stringResource(R.string.condition_manual_hint),
                            onClick = {
                                onPick(spec.type)
                                onDismissRequest()
                            },
                            shape = segmentedShape(index, MANUAL_CONDITION_TYPES.size),
                        )
                    }
                }
            }
        }
    }
}