package hunoia.luno.ui.actionlibrary
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
import hunoia.luno.action.template.TemplateSnippets
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.SheetSearchList
/** 参数模板选择面板：运行时变量 + 常用静态片段，点选即插入到光标处。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    val variables = ActionLibrary.templateVariables
    val snippets = TemplateSnippets.settingsKeys
    var query by remember { mutableStateOf("") }
    val filteredVariables = remember(query, variables) {
        if (query.isBlank()) variables
        else variables.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.token.contains(query, ignoreCase = true)
        }
    }
    val filteredSnippets = remember(query, snippets) {
        if (query.isBlank()) snippets
        else snippets.filter {
            it.text.contains(query, ignoreCase = true) ||
                it.label.contains(query, ignoreCase = true)
        }
    }
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            SheetSearchList(
                query = query,
                onQueryChange = { query = it },
            ) {
                if (filteredVariables.isNotEmpty()) {
                    item(key = "__vars") { SheetGroupHeader(R.string.action_template_group_variables) }
                    itemsIndexed(filteredVariables, key = { _, v -> "v_${v.token}" }) { index, variable ->
                        LunoSelectableRow(
                            title = variable.label,
                            subtitle = token(variable.token) + " · " +
                                stringResource(R.string.action_template_sample, variable.sample),
                            selected = false,
                            onSelectedChange = { onPick(token(variable.token)) },
                            shape = segmentedShape(index, filteredVariables.size),
                            trailing = if (variable.triggerOnly) {
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
                }
                if (filteredSnippets.isNotEmpty()) {
                    item(key = "__snippets") { SheetGroupHeader(R.string.action_template_group_settings_key) }
                    itemsIndexed(filteredSnippets, key = { _, s -> "s_${s.text}" }) { index, snippet ->
                        LunoSelectableRow(
                            title = snippet.text,
                            subtitle = snippet.label,
                            selected = false,
                            onSelectedChange = { onPick(snippet.text) },
                            shape = segmentedShape(index, filteredSnippets.size),
                        )
                    }
                }
            }
        }
    }
}
private fun token(name: String): String = "\${$name}"