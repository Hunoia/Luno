package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.InitialSettings
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
        // 总闸优先：总开关关时球不该拉起 —— applyVisibility 会立刻 detach 窗口，
        // 但前台通知「悬浮球待命」还会常驻。
        if (readMasterSwitchEnabled(context) && readFloatBallEnabled(context)) {
            FloatBallService.start(context)
        }
    }

    /** 开机同步读总开关：默认 true，DataStore 缺文件时生效的就是它，读失败按「开」处理。 */
    private fun readMasterSwitchEnabled(context: Context): Boolean = runCatching {
        val file = File("${context.filesDir.absolutePath}/ds/${DataStoreFiles.INITIAL_SETTINGS}")
        if (!file.exists()) return@runCatching true
        val json = file.readText()
        if (json.isBlank()) return@runCatching true
        JsonSerializer.decodeFromString<InitialSettings>(json).gestureEnabled
    }.getOrDefault(true)

    /**
     * 开机同步读球的开关：`onReceive` 里起协程要么 `goAsync()` 拖长广播窗口，要么
     * 直接读文件，这里选后者。路径沿用 [DataStoreFiles] 常量（布局由 `Context.dataStore`
     * 单点定义）。读失败按「不开」处理：开机路径抛异常会连带影响 `DaemonService` 拉起。
     */
    private fun readFloatBallEnabled(context: Context): Boolean = runCatching {
        val file = File("${context.filesDir.absolutePath}/ds/${DataStoreFiles.ACTION_SETTINGS}")
        if (!file.exists()) return@runCatching false
        val json = file.readText()
        if (json.isBlank()) return@runCatching false
        JsonSerializer.decodeFromString<ActionSettings>(json).floatBall.enabled
    }.getOrDefault(false)
}