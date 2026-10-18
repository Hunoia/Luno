package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.store.DataStoreFiles
import hunoia.luno.core.JsonSerializer
import hunoia.luno.runtime.floatball.FloatBallService
import java.io.File

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("daemon", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keep_alive", false)) {
            // DaemonService 是 specialUse 前台服务，必须用 startForegroundService 拉起：
            // Android 12+ 用 startService 拉前台服务会抛 ForegroundServiceStartNotAllowedException，
            // 且服务必须在 5s 内调 startForeground，否则崩溃。
            context.startForegroundService(Intent(context, DaemonService::class.java))
        }
        // 悬浮球同样是 specialUse 前台服务，约束完全一致。
        if (readFloatBallEnabled(context)) {
            FloatBallService.start(context)
        }
    }

    /**
     * 开机时同步读球的开关。
     *
     * DataStore 只有 suspend 接口，而 `onReceive` 里起协程要么用 `goAsync()` 拖长
     * 广播窗口、要么直接读文件。这里选直接读：一个小 JSON 文件的阻塞 IO 完全可控，
     * 比在开机路径上引入异步时序简单得多。
     *
     * 文件路径沿用 [DataStoreFiles] 的常量 —— 布局由 `Context.dataStore` 单点定义，
     * 这里只拼同一个前缀。读失败一律按「不开」处理：开机路径抛异常会连带影响
     * `DaemonService` 的拉起。
     */
    private fun readFloatBallEnabled(context: Context): Boolean = runCatching {
        val file = File("${context.filesDir.absolutePath}/ds/${DataStoreFiles.ACTION_SETTINGS}")
        if (!file.exists()) return@runCatching false
        val json = file.readText()
        if (json.isBlank()) return@runCatching false
        JsonSerializer.decodeFromString<ActionSettings>(json).floatBall.enabled
    }.getOrDefault(false)
}