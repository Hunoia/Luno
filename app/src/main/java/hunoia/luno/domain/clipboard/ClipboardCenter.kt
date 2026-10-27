package hunoia.luno.domain.clipboard

import android.util.Log
import hunoia.luno.R
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.system.copyImage
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.system.readClipboardContentDetailed
import hunoia.luno.config.ConfigProvider
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.shizuku.ShizukuClipboard
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 剪贴板页面部件的唯一数据入口：读当前主剪贴项、记历史、写回、删除/清空。
 *
 * 新 ROM（HyperOS 等）后台既不投递 `OnPrimaryClipChangedListener` 也拒绝后台
 * `getPrimaryClip`，所以历史只能在这些时机抓：剪贴板球弹出时、剪贴板部件打开时。
 * 读取优先走 Shizuku（shell uid 在放行名单里），不可用时退回应用内直读（悬浮窗
 * 获焦期间通常有效）。
 */
object ClipboardCenter {

    private const val TAG = LOG_TAG

    /** 读当前主剪贴项：Shizuku 可用走 shell 桥，否则应用内直读。 */
    suspend fun readCurrent(): ClipboardContent? = withContext(Dispatchers.IO) {
        val status = runCatching { ShizukuManager.currentStatus() }.getOrNull()
        if (status?.binderAlive == true && status.permissionGranted) {
            val viaShizuku = runCatching { ShizukuClipboard.readContent() }
                .onFailure { Log.w(TAG, "clipboard read via shizuku failed: ${it.message}") }
                .getOrNull()
            if (viaShizuku != null) return@withContext viaShizuku
        }
        runCatching { readClipboardContentDetailed(AppContext.get()) }.getOrNull()
    }

    /**
     * 把读到的内容记进历史：只记非敏感文本。
     * 图片与敏感文本只作为「当前主剪贴项」显示，不入历史（前者 URI 会失效，后者不该落盘）。
     */
    fun record(content: ClipboardContent?) {
        val text = (content as? ClipboardContent.Text)?.takeIf { !it.sensitive }?.text ?: return
        if (text.isBlank()) return
        // 只在用户可见读取路径记历史：同时作为剪贴板触发源，不承诺后台实时监听。
        hunoia.luno.runtime.automation.AutomationPulseHub.fireClipboard(text)
        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            ConfigProvider.recordClipboardText(text)
        }
    }

    /** 写回剪贴板（普通 `ClipboardManager`；Shizuku 写回不做）。 */
    suspend fun copy(text: String): Boolean {
        if (text.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            copyText(AppContext.get(), clipboardLabel(), text)
        }
    }

    /** 图片写回：把 content URI 放回剪贴板（聊天/笔记应用可直接粘贴）。 */
    suspend fun copyImage(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        copyImage(AppContext.get(), clipboardLabel(), uri)
    }

    private fun clipboardLabel(): String = AppContext.get().getString(R.string.app_name)

    fun remove(id: String) {
        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { ConfigProvider.removeClipboardEntry(id) }
        }
    }

    fun clear() {
        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { ConfigProvider.clearClipboardHistory() }
        }
    }
}
