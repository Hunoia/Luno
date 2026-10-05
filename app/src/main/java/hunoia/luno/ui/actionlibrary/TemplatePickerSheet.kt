package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.template.TemplateSnippets
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetTopShape

/** 参数模板选择面板：运行时变量 + 常用静态片段，点选即插入到光标处。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val variables = ActionLibrary.templateVariables
    val snippets = TemplateSnippets.settingsKeys
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
            contentPadding = PaddingValues(bottom = ContentBottom),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            item(key = "__vars") { SheetGroupHeader(R.string.action_template_group_variables) }
            itemsIndexed(variables, key = { _, v -> "v_${v.token}" }) { index, variable ->
                SelectableListItem(
                    title = variable.label,
                    subtitle = token(variable.token) + " · " +
                        stringResource(R.string.action_template_sample, variable.sample),
                    selected = false,
                    onSelect = { onPick(token(variable.token)) },
                    shape = segmentedShape(index, variables.size),
                    verticalGap = 0.dp,
                    horizontalGap = 0.dp,
                    trailingContent = if (variable.triggerOnly) {
                        {
                            Text(
                                text = stringResource(R.string.action_template_trigger_only),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                )
            }
            item(key = "__snippets") { SheetGroupHeader(R.string.action_template_group_settings_key) }
            itemsIndexed(snippets, key = { _, s -> "s_${s.text}" }) { index, snippet ->
                SelectableListItem(
                    title = snippet.text,
                    subtitle = snippet.label,
                    selected = false,
                    onSelect = { onPick(snippet.text) },
                    shape = segmentedShape(index, snippets.size),
                    verticalGap = 0.dp,
                    horizontalGap = 0.dp,
                )
            }
        }
    }
}

private fun token(name: String): String = "\${$name}"
