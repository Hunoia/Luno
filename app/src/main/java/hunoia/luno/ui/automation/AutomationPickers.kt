package hunoia.luno.ui.automation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.sheet.LunoSheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionTypePickerSheet(
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
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
                    TypePickerHeader(stringResource(R.string.condition_level_group))
                }
                items(items = LEVEL_CONDITION_TYPES, key = { "type_${it.type}" }) { spec ->
                    LunoSettingsRow(
                        title = stringResource(spec.labelRes),
                        onClick = {
                            onPick(spec.type)
                            onDismissRequest()
                        },
                    )
                }
                item(key = "group_event") {
                    TypePickerHeader(stringResource(R.string.condition_event_group))
                }
                items(items = EVENT_CONDITION_TYPES, key = { "type_${it.type}" }) { spec ->
                    LunoSettingsRow(
                        title = stringResource(spec.labelRes),
                        onClick = {
                            onPick(spec.type)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun TypePickerHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ListSpacing),
    )
}