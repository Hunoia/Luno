package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("daemon", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keep_alive", false)) {
            context.startService(Intent(context, DaemonService::class.java))
        }
    }
}
