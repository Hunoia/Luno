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
    /** Shizuku 就绪 = 「自动切换采集」可用；否则置灰走手动兜底。 */
    val shizukuReady: Boolean = false,
    /** 自动切换会话进行中（已切为临时桌面且未恢复）→ 显示「恢复原桌面」行。 */
    val sessionActive: Boolean = false,
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

    /** 进页 / 回前台刷新：权限可能随临时桌面切换变化，会话态也可能被外部（杀进程重开）打断。 */
    fun refreshPermission() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val allowed = withContext(Dispatchers.IO) { DynamicShortcutCenter.hasPermission(app) }
            val shizukuReady = withContext(Dispatchers.IO) { DynamicShortcutCenter.autoSwitchReady() }
            // 无快照时 activeSession 不走 shell，常态零特权往返。
            val sessionActive = withContext(Dispatchers.IO) {
                runCatching { DynamicShortcutCenter.activeSession(app) }.getOrDefault(false)
            }
            _uiState.value = _uiState.value.copy(
                hasPermission = allowed,
                shizukuReady = shizukuReady,
                sessionActive = sessionActive,
            )
        }
    }

    /** 采集全部（需默认桌面权限）：读系统入口、pin 全部并标为已导入。 */
    fun collectAll() = runOperation {
        val count = DynamicShortcutCenter.collectAll(getApplication())
        _messages.send(DynamicShortcutMessage(R.string.dynamic_shortcuts_collected_n, count))
    }

    /**
     * 自动切换为临时桌面：Shizuku 快照原桌面与导航方式后切换。
     * 切换后按 HOME 会经临时桌面入口回到本页（既有路由），每条「添加到桌面」由
     * pin 宿主静默入库。
     */
    fun beginAutoSession() = runOperation {
        if (DynamicShortcutCenter.beginAutoSession(getApplication())) {
            _messages.send(DynamicShortcutMessage(R.string.dynamic_shortcuts_switched))
        }
        refreshPermission()
    }

    /** 结束会话：先采集再恢复原桌面与导航方式。恢复失败保留快照，横幅态可重试。 */
    fun endAutoSession() = runOperation {
        val restored = DynamicShortcutCenter.endAutoSession(getApplication())
        if (restored) _messages.send(DynamicShortcutMessage(R.string.dynamic_shortcuts_restored))
        refreshPermission()
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
