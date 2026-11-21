package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import hunoia.luno.bridge.system.BatteryPowerReader
import hunoia.luno.config.ConfigProvider
import hunoia.luno.core.LunoLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 电量 / 功率历史的后台采集器（事件驱动，不轮询）。
 *
 * 为什么用动态广播而不是 WorkManager：
 *  - `ACTION_BATTERY_CHANGED` 是 sticky 广播，注册即得当前值，之后电量 / 状态变化才触发；
 *    零额外唤醒，符合 architecture §13「无事件不做功」。
 *  - 代价是 `:service` 不在时没有采样：没开保活 / 无障碍、或用户强停应用时曲线会有空档。
 *    这是有意的取舍，不为此新增常驻进程或定时任务。
 *
 * 引用计数：`DaemonService` 与 `SideGestureService` 同在 `:service`，两者都可能启动；
 * 谁先启动谁注册，最后一个停止才真正注销，避免重复 receiver 与漏注销。
 */
object BatteryHistoryRecorder {

    private const val LOG_TAG = "BatteryHistory"

    private var refCount = 0
    private var receiver: BroadcastReceiver? = null
    private var scope: CoroutineScope? = null

    @Synchronized
    fun start(context: Context) {
        refCount++
        if (refCount > 1 || receiver != null) return

        val app = context.applicationContext
        val reader = BatteryPowerReader(app)
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = writeScope

        val next = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val sample = runCatching { reader.readSample() }.getOrNull() ?: return
                // goAsync 让广播在写盘期间保持存活；10s 上限对一次 DataStore 写入足够。
                val pending = goAsync()
                val workScope = scope
                if (workScope == null || !workScope.isActive) {
                    runCatching { pending.finish() }
                    return
                }
                workScope.launch {
                    try {
                        // 历史是辅助数据：写盘失败不能影响采集器 / 服务本身。
                        runCatching { ConfigProvider.appendBatterySample(sample) }
                    } finally {
                        runCatching { pending.finish() }
                    }
                }
            }
        }
        runCatching {
            app.registerReceiver(
                next,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiver = next
        }.onFailure {
            LunoLog.w(LOG_TAG, "register battery receiver failed: ${it.message}")
            writeScope.cancel()
            scope = null
        }
    }

    @Synchronized
    fun stop(context: Context) {
        if (refCount > 0) refCount--
        if (refCount > 0) return
        receiver?.let { registered ->
            runCatching { context.applicationContext.unregisterReceiver(registered) }
        }
        receiver = null
        scope?.cancel()
        scope = null
    }
}
