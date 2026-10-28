package hunoia.luno.runtime.condition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 电量与充电状态源。ACTION_BATTERY_CHANGED 是粘性广播，电量每变化约 1% 都会重投递，
 * 因此无需轮询；接收器注册在主线程（未传 Handler），全部状态写入均在主线程。
 * 回调参数区分两类变化：
 * - [chargingChanged]：充电边沿变化。调用方应据此发 charging 脉冲（脉冲携带的是值，
 *   非 CHARGING_CHANGED 边沿不能发，否则「充电开始/断开时」规则会随电量浮动重复触发）；
 * - [charging]：变化后的充电状态值；
 * - [levelChanged]：仅电量百分比变化。只触发状态重评（BATTERY_RANGE），不发脉冲。
 */
class BatteryTracker(
    private val context: Context,
    private val onChanged: (chargingChanged: Boolean, charging: Boolean, levelChanged: Boolean) -> Unit,
) {
    var isCharging: Boolean = false
        private set
    var batteryLevel: Int = -1
        private set

    private var registered = false
    private var initialized = false

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
        if (!initialized) {
            // 首读（粘性广播）只建基线：当前充电态不是「边沿」，不设守卫的话
            // 充电中重绑会被记成 false→true，误触发一次「开始充电」规则。
            initialized = true
            isCharging = charging
            batteryLevel = level
            return
        }
        val chargingChanged = charging != isCharging
        val levelChanged = level != batteryLevel
        if (!chargingChanged && !levelChanged) return
        isCharging = charging
        batteryLevel = level
        onChanged(chargingChanged, charging, levelChanged)
    }

    private fun Intent.batteryLevel(): Int {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100) / scale
    }
}
