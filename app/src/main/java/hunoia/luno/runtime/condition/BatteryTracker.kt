package hunoia.luno.runtime.condition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryTracker(
    private val context: Context,
    private val onChanged: () -> Unit,
) {
    var isCharging: Boolean = false
        private set
    var batteryLevel: Int = -1
        private set

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            update(intent)
        }
    }

    fun register() {
        if (registered) return
        val sticky = context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        update(sticky)
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }

    private fun update(intent: Intent?) {
        if (intent == null) return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val level = intent.batteryLevel()
        if (charging == isCharging && level == batteryLevel) return
        isCharging = charging
        batteryLevel = level
        onChanged()
    }

    private fun Intent.batteryLevel(): Int {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100) / scale
    }
}
