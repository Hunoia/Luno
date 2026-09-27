package hunoia.luno.ui.actionlibrary

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.action.template.SystemFunctionTemplates
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryPayload
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.shizuku.ShizukuFacade
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import hunoia.luno.ui.settings.ActivitySettingsContent
import hunoia.luno.ui.settings.ShellCommandSettingsContent
import hunoia.luno.ui.settings.TestOutputBox
import hunoia.luno.ui.settings.UrlSettingsContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ActionLibraryEntry.isPayloadValid(): Boolean = when (type) {
    ActionLibraryType.Shell -> shellCommand.command.isNotBlank()
    ActionLibraryType.Url -> openAppOrUrl.url.isNotBlank()
    ActionLibraryType.Activity -> openAppOrUrl.packageName.isNotBlank() && openAppOrUrl.activityClassName.isNotBlank()
    ActionLibraryType.SystemTemplate -> systemTemplate.templateId.isNotBlank()
    ActionLibraryType.SystemApi -> systemApi.command.isNotBlank()
}

internal fun ActionLibraryEntry.resolvedShellCommand(): String? = when (val payload = payload) {
    is ActionLibraryPayload.Shell -> payload.data.command.trim().takeIf { it.isNotBlank() }
    is ActionLibraryPayload.SystemApi -> payload.data.command.trim().takeIf { it.isNotBlank() }
    is ActionLibraryPayload.SystemTemplate -> {
        val template = SystemFunctionTemplates.getById(payload.data.templateId) ?: return null
        SystemFunctionTemplates.generateCommand(template, payload.data.params).trim().takeIf { it.isNotBlank() }
    }
    else -> null
}

internal fun ActionLibraryEntry.toConfigAction(): Action = when (type) {
    ActionLibraryType.Shell -> Action(data = JsonSerializer.encodeToString(shellCommand))
    ActionLibraryType.Url,
    ActionLibraryType.Activity -> Action(data = JsonSerializer.encodeToString(openAppOrUrl))
    ActionLibraryType.SystemTemplate -> Action(data = JsonSerializer.encodeToString(systemTemplate))
    ActionLibraryType.SystemApi -> Action(data = JsonSerializer.encodeToString(systemApi))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLibraryEditScreen(
    entryId: String,
    type: ActionLibraryType,
    onBack: () -> Unit,
    vm: ActionLibraryVM = viewModel(),
) {
    val libraryState by vm.uiState.collectAsState()
    val isNew = entryId == NEW_ACTION_LIBRARY_ENTRY_ID
    var draft by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(libraryState.entries) {
        if (draft == null) {
            draft = libraryState.entries.firstOrNull { it.id == entryId }
                ?: ActionLibraryEntry.create(type, defaultActionLibraryName(type, libraryState.entries))
        }
    }

    val entry = draft ?: return

    val testingLabel = stringResource(R.string.testing)
    val testSuccessLabel = stringResource(R.string.test_success)
    val testFailedLabel = stringResource(R.string.test_failed)
    val noOutputLabel = stringResource(R.string.shell_command_no_output)
    val shellSuccessFormat = stringResource(R.string.shell_command_test_output)
    val shellErrorFormat = stringResource(R.string.shell_command_test_error_output)

    fun testLaunch(launch: () -> Boolean) {
        testing = true
        testOutput = ""
        val ok = launch()
        testing = false
        testOutput = if (ok) testSuccessLabel else testFailedLabel
    }

    fun testShellCommand(command: String) {
        testing = true
        testOutput = testingLabel
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShizukuFacade.runShellCommand(context.applicationContext, command)
            }
            testing = false
            val output = result.output.ifBlank { noOutputLabel }
            testOutput = if (result.success) {
                String.format(shellSuccessFormat, result.exitCode, result.elapsedMs, output)
            } else {
                String.format(
                    shellErrorFormat,
                    result.error ?: "unknown error",
                    result.exitCode,
                    result.elapsedMs,
                    output,
                )
            }
            showToast(if (result.success) output.take(500) else (result.error ?: output).take(500))
        }
    }

    fun launchTest() {
        when (entry.type) {
            ActionLibraryType.Url -> testLaunch { Launcher.launchUrl(context, entry.openAppOrUrl) }
            ActionLibraryType.Activity -> testLaunch {
                Launcher.launchAppActivity(
                    context,
                    entry.openAppOrUrl.packageName,
                    entry.openAppOrUrl.activityClassName,
                )
            }

            else -> {
                val command = entry.resolvedShellCommand() ?: return
                testShellCommand(command)
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(if (isNew) R.string.action_library_add else R.string.action_library_edit),
                actions = {
                    TextButton(
                        enabled = entry.isPayloadValid(),
                        onClick = {
                            vm.save(entry.copy(name = entry.name.ifBlank {
                                defaultActionLibraryName(
                                    entry.type,
                                    libraryState.entries.filter { it.id != entry.id },
                                )
                            }))
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
            Text(
                text = stringResource(R.string.action_library_type_prefix, stringResource(entry.type.titleRes)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = entry.name,
                onValueChange = { draft = entry.copy(name = it) },
                label = { Text(stringResource(R.string.action_library_entry_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when (entry.type) {
                ActionLibraryType.Shell -> ShellCommandSettingsContent(
                    action = entry.toConfigAction(),
                    onConfirm = {},
                    showConfirmButton = false,
                    modifier = Modifier,
                    onDataChange = { data ->
                        val shell = JsonSerializer.decodeFromString<ShellCommandData>(data)
                        draft = entry.updateShellCommand(shell)
                    },
                )
                ActionLibraryType.Url -> UrlSettingsContent(
                    action = entry.toConfigAction(),
                    onConfirm = {},
                    showConfirmButton = false,
                    modifier = Modifier,
                    onDataChange = { data ->
                        val openUrl = JsonSerializer.decodeFromString<OpenAppOrUrlData>(data)
                        draft = entry.updateOpenAppOrUrl(openUrl)
                    },
                )
                ActionLibraryType.Activity -> ActivitySettingsContent(
                    action = entry.toConfigAction(),
                    onConfirm = {},
                    modifier = Modifier,
                    onDataChange = { data ->
                        val openUrl = JsonSerializer.decodeFromString<OpenAppOrUrlData>(data)
                        draft = entry.updateOpenAppOrUrl(openUrl)
                    },
                )
                ActionLibraryType.SystemTemplate -> {
                    SystemTemplatePickerInline(
                        entry = entry,
                        onConfirm = { draft = it },
                    )
                    SystemTemplateParamsInline(
                        entry = entry,
                        onConfirm = { draft = it },
                    )
                }
                ActionLibraryType.SystemApi -> SystemApiConfigInline(
                    entry = entry,
                    onConfirm = { draft = it },
                )
            }
            when (entry.type) {
                ActionLibraryType.Url,
                ActionLibraryType.Activity,
                ActionLibraryType.SystemTemplate -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(enabled = entry.isPayloadValid() && !testing, onClick = { launchTest() }) {
                        Text(stringResource(if (testing) R.string.testing else R.string.test))
                    }
                }

                else -> Unit
            }
            if (testOutput.isNotBlank()) {
                TestOutputBox(testOutput)
            }
        }
    }
}
