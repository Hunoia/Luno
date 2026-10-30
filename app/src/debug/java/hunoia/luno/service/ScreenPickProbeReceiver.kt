package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** debug-only：广播触发识屏快照探测（extras: x, y）。 */
class ScreenPickProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val x = intent.getIntExtra("x", 0)
        val y = intent.getIntExtra("y", 0)
        ScreenPickProbe.hook?.invoke(x, y)
    }
}
