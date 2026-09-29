package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.settings.TestButtonAndOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLibraryEditScreen(
    entryId: String,
    type: ActionLibraryType?,
    onBack: () -> Unit,
    vm: ActionLibraryEditVM = viewModel(),
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(entryId, type) {
        vm.load(entryId, type)
    }

    val draft = state.draft ?: return

    Scaffold(
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(if (state.isNew) R.string.action_library_add else R.string.action_library_edit),
                actions = {
                    TextButton(
                        enabled = vm.isValid,
                        onClick = {
                            vm.save()
                            onBack()
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaffoldPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isNew && type == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionLibraryType.entries.forEach { t ->
                        FilterChip(
                            selected = draft.type == t,
                            onClick = { vm.switchType(t) },
                            label = { Text(stringResource(t.titleRes)) },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.action_library_type_prefix, stringResource(draft.type.titleRes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { vm.updateName(it) },
                label = { Text(stringResource(R.string.action_library_entry_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when (draft.type) {
                ActionLibraryType.Shell -> ShellCommandTypedEditor(
                    data = draft.shellCommand,
                    onValueChange = vm::updateShell,
                )
                ActionLibraryType.Url -> UrlTypedEditor(
                    data = draft.openAppOrUrl,
                    onValueChange = vm::updateUrl,
                )
                ActionLibraryType.Activity -> ActivityTypedEditor(
                    data = draft.openAppOrUrl,
                    onValueChange = vm::updateActivity,
                )
            }
            TestButtonAndOutput(
                testing = state.testing,
                testOutput = state.testOutput,
                canTest = vm.canTest,
                onTest = vm::test,
            )
        }
    }
}
