package hunoia.luno.actions.shortcut

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.actions.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DynamicShortcutUiState(
    val hasPermission: Boolean = false,
    val busy: Boolean = false,
)

data class DynamicShortcutMessage(
    val resId: Int,
    val count: Int? = null,
)

class DynamicShortcutViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DynamicShortcutUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = Channel<DynamicShortcutMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refreshPermission()
    }

    fun refreshPermission() {
        val allowed = DynamicShortcutCenter.hasPermission(getApplication())
        if (_uiState.value.hasPermission != allowed) {
            _uiState.value = _uiState.value.copy(hasPermission = allowed)
        }
    }

    /** 采集全部（需默认桌面权限）：读系统入口、pin 全部并标为已导入。 */
    fun collectAll() = runOperation {
        val count = DynamicShortcutCenter.collectAll(getApplication())
        _messages.send(DynamicShortcutMessage(R.string.dynamic_shortcuts_collected_n, count))
    }

    /**
     * 「更改默认桌面」：统一打开系统「默认桌面」设置页，用户在其中选 Luno 设为默认，
     * 或换回原桌面。不区分当前是否已是默认桌面，两种状态同一入口。
     */
    fun toggleDefaultHome() {
        startActivitySafely(DynamicShortcutCenter.homeSettingsIntent())
    }

    private fun startActivitySafely(intent: android.content.Intent) {
        val app = getApplication<Application>()
        val launched = runCatching {
            app.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!launched) _messages.trySend(DynamicShortcutMessage(R.string.dynamic_shortcuts_failed))
    }

    private fun runOperation(block: suspend () -> Unit) = viewModelScope.launch {
        if (_uiState.value.busy) return@launch
        _uiState.value = _uiState.value.copy(busy = true)
        try {
            withContext(Dispatchers.IO) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _messages.send(DynamicShortcutMessage(R.string.dynamic_shortcuts_failed))
                }
            }
        } finally {
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }
}
