package hunoia.luno.ui.condition.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.RuleScope
import hunoia.luno.ui.component.ExpressiveSwitchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScopeSheet(
    buttons: List<GestureButton>,
    scope: RuleScope,
    buttonIds: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (RuleScope, List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedScope by remember(scope) { mutableStateOf(scope) }
    var selectedIds by remember(buttonIds) { mutableStateOf(buttonIds.toSet()) }

    val scopeOptions = listOf(
        RuleScope.ALL to stringResource(R.string.scope_all),
        RuleScope.EXCEPT to stringResource(R.string.scope_except),
        RuleScope.ONLY to stringResource(R.string.scope_only),
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.condition_scope),
                style = MaterialTheme.typography.titleLarge,
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                scopeOptions.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = selectedScope == option,
                        onClick = { selectedScope = option },
                        shape = SegmentedButtonDefaults.itemShape(
                            count = scopeOptions.size,
                            index = index,
                        ),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            when (selectedScope) {
                RuleScope.ALL -> {
                    Text(
                        text = stringResource(R.string.scope_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RuleScope.EXCEPT, RuleScope.ONLY -> {
                    buttons.sortedBy { it.id }.forEachIndexed { index, button ->
                        ExpressiveSwitchItem(
                            title = button.name.ifBlank {
                                stringResource(R.string.gesture_button_name, index + 1)
                            },
                            checked = button.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + button.id
                                else selectedIds - button.id
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismissRequest,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfirm(
                            selectedScope,
                            if (selectedScope == RuleScope.ALL) emptyList()
                            else selectedIds.toList(),
                        )
                        onDismissRequest()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
