package hunoia.luno.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GlobalVariable
import hunoia.luno.config.model.GlobalVariables
import hunoia.luno.config.model.VariableNames
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.SheetHeader
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter

/** P2-1 全局变量管理：查看、编辑、新增、删除；保存时统一 normalize。 */
@Composable
internal fun GlobalVariablesSheet(
    initial: GlobalVariables,
    onDismissRequest: () -> Unit,
    onSave: (List<GlobalVariable>) -> Unit,
) {
    var rows by remember(initial) { mutableStateOf(initial.entries) }
    val names = rows.map { it.name.trim() }
    val hasInvalid = rows.any { !VariableNames.isValid(it.name) }
    val hasDuplicate = names.filter { it.isNotEmpty() }.size != names.filter { it.isNotEmpty() }.distinct().size
    LunoSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = PageGutter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            SheetHeader(title = stringResource(R.string.automation_variables))
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                Text(
                    text = stringResource(R.string.automation_variables_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { value ->
                                rows = rows.toMutableList().also {
                                    it[index] = item.copy(name = value)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.step_set_var_name)) },
                            singleLine = true,
                            isError = !VariableNames.isValid(item.name),
                            shape = LargeShape,
                        )
                        OutlinedTextField(
                            value = item.value,
                            onValueChange = { value ->
                                rows = rows.toMutableList().also {
                                    it[index] = item.copy(value = value)
                                }
                            },
                            modifier = Modifier.weight(1.4f),
                            label = { Text(stringResource(R.string.step_set_var_value)) },
                            singleLine = true,
                            shape = LargeShape,
                        )
                        LunoIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = {
                                rows = rows.toMutableList().also { it.removeAt(index) }
                            },
                        )
                    }
                }
                LunoSettingsRow(
                    title = stringResource(R.string.automation_variables_add),
                    onClick = {
                        // 生成一个不冲突的默认名，用户可立即改。
                        var index = rows.size + 1
                        while (rows.any { it.name == "var$index" }) index++
                        rows = rows + GlobalVariable("var$index", "")
                    },
                    trailing = {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                )
                if (hasInvalid || hasDuplicate) {
                    Text(
                        text = stringResource(R.string.automation_variables_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            val normalized = GlobalVariables(rows).normalized()
                            onSave(normalized.entries)
                        },
                        enabled = !hasInvalid && !hasDuplicate,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
