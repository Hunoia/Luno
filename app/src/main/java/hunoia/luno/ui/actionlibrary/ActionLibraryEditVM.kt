package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.isPayloadValid
import hunoia.luno.config.model.resolvedShellCommand
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.core.AppContext
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.ui.settings.CommandTester
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditState(
    val draft: ActionLibraryEntry?,
    val isNew: Boolean,
    val testing: Boolean,
    val testOutput: String,
)

class ActionLibraryEditVM : ViewModel() {

    private val _uiState = MutableStateFlow(EditState(
        draft = null,
        isNew = true,
        testing = false,
        testOutput = "",
    ))
    val uiState: StateFlow<EditState> = _uiState

    private var entries: List<ActionLibraryEntry> = emptyList()

    private val context get() = AppContext.get()

    init {
        viewModelScope.launch {
            // Migrate legacy payloads on first access
            ConfigProvider.updateActionLibrarySettings { settings ->
                if (settings.hasLegacyPayloads()) settings.migrate() else settings
            }
            ConfigProvider.actionLibrarySettings.collect { settings ->
                entries = settings.entries
            }
        }
    }

    fun load(entryId: String, initialType: ActionLibraryType?) {
        val type = initialType ?: ActionLibraryType.Shell
        val draft = if (entryId == NEW_ACTION_LIBRARY_ENTRY_ID) {
            ActionLibraryEntry.create(type, defaultActionLibraryName(type, entries))
        } else {
            entries.firstOrNull { it.id == entryId }
                ?: ActionLibraryEntry.create(type, defaultActionLibraryName(type, entries))
        }
        _uiState.update { it.copy(draft = draft, isNew = entryId == NEW_ACTION_LIBRARY_ENTRY_ID) }
    }

    fun switchType(type: ActionLibraryType) {
        val existing = _uiState.value.draft
        val filtered = entries.filter { it.id != existing?.id }
        _uiState.update {
            it.copy(
                draft = ActionLibraryEntry.create(type, defaultActionLibraryName(type, filtered)),
                testOutput = "",
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update {
            val d = it.draft ?: return@update it
            it.copy(draft = d.copy(name = name))
        }
    }

    fun updateShell(data: ShellCommandData) {
        _uiState.update {
            val d = it.draft ?: return@update it
            it.copy(draft = d.updateShellCommand(data))
        }
    }

    fun updateUrl(data: OpenAppOrUrlData) {
        _uiState.update {
            val d = it.draft ?: return@update it
            it.copy(draft = d.updateOpenAppOrUrl(data))
        }
    }

    fun updateActivity(data: OpenAppOrUrlData) {
        _uiState.update {
            val d = it.draft ?: return@update it
            it.copy(draft = d.updateOpenAppOrUrl(data))
        }
    }

    val isValid: Boolean
        get() = _uiState.value.draft?.isPayloadValid() ?: false

    val canTest: Boolean
        get() {
            val d = _uiState.value.draft ?: return false
            if (_uiState.value.testing) return false
            return when (d.type) {
                ActionLibraryType.Shell -> d.resolvedShellCommand() != null
                ActionLibraryType.Url -> d.openAppOrUrl.url.isNotBlank()
                ActionLibraryType.Activity -> d.openAppOrUrl.packageName.isNotBlank() && d.openAppOrUrl.activityClassName.isNotBlank()
            }
        }

    fun test() {
        val entry = _uiState.value.draft ?: return
        when (entry.type) {
            ActionLibraryType.Shell -> {
                val command = entry.resolvedShellCommand() ?: return
                testShellCommand(command)
            }
            ActionLibraryType.Url -> {
                testLaunch { Launcher.launchUrl(context, entry.openAppOrUrl) }
            }
            ActionLibraryType.Activity -> {
                testLaunch {
                    Launcher.launchAppActivity(context, entry.openAppOrUrl.packageName, entry.openAppOrUrl.activityClassName)
                }
            }
        }
    }

    private fun testShellCommand(command: String) {
        _uiState.update { it.copy(testing = true, testOutput = context.getString(R.string.testing)) }
        viewModelScope.launch {
            val result = CommandTester.test(context, command)
            val noOutput = context.getString(R.string.shell_command_no_output)
            val output = result.output.ifBlank { noOutput }
            val testOutput = if (result.success) {
                context.getString(R.string.shell_command_test_output, result.exitCode, result.elapsedMs, output)
            } else {
                context.getString(R.string.shell_command_test_error_output, result.error ?: "unknown error", result.exitCode, result.elapsedMs, output)
            }
            _uiState.update { it.copy(testing = false, testOutput = testOutput) }
            if (result.success) {
                showToast(result.output.ifBlank { noOutput }.take(500))
            } else {
                showToast((result.error ?: result.output.ifBlank { "unknown error" }).take(500))
            }
        }
    }

    private fun testLaunch(launch: () -> Boolean) {
        val ok = launch()
        _uiState.update {
            it.copy(testing = false, testOutput = if (ok) context.getString(R.string.test_success) else context.getString(R.string.test_failed))
        }
    }

    fun save() {
        val draft = _uiState.value.draft ?: return
        val entryToSave = draft.copy(name = draft.name.ifBlank {
            defaultActionLibraryName(draft.type, entries.filter { it.id != draft.id })
        })
        viewModelScope.launch {
            ConfigProvider.updateActionLibrarySettings { settings ->
                val index = settings.entries.indexOfFirst { it.id == entryToSave.id }
                val entries = settings.entries.toMutableList()
                if (index >= 0) entries[index] = entryToSave else entries.add(entryToSave)
                settings.copy(entries = entries)
            }
        }
    }
}
