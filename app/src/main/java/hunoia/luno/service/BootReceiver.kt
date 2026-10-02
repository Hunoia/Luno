package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("daemon", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keep_alive", false)) {
            // DaemonService 是 specialUse 前台服务，必须用 startForegroundService 拉起：
            // Android 12+ 用 startService 拉前台服务会抛 ForegroundServiceStartNotAllowedException，
            // 且服务必须在 5s 内调 startForeground，否则崩溃。
            context.startForegroundService(Intent(context, DaemonService::class.java))
        }
    }
}
