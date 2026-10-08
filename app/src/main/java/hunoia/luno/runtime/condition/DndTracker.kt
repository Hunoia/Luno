package hunoia.luno.runtime.condition

import android.app.NotificationManager
import android.content.Context

/**
 * 勿扰模式状态：读当前中断过滤器（非 ALL=开启）。
 *
 * 变化来源：
 * - 通知监听服务的 onInterruptionFilterChanged → Hub 推入（需通知访问权限，完整实时）；
 * - 未授权时由调用方在启动/规则变化/亮屏等时机 refreshFromSystem() 补读。
 * 首读只做基线不发脉冲。
 */
class DndTracker(
    private val context: Context,
    private val onChanged: (Boolean) -> Unit = {},
) {
    var isOn: Boolean = false
        private set

    private var initialized = false

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 从系统读一次并更新（首读不发脉冲）。 */
    fun refreshFromSystem() {
        apply(
            runCatching {
                manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            }.getOrDefault(false)
        )
    }

    /** 通知监听服务上报的变化（Hub 回调）。 */
    fun onSystemChanged(on: Boolean) {
        apply(on)
    }

    private fun apply(on: Boolean) {
        if (!initialized) {
            initialized = true
            isOn = on
            return
        }
        if (on == isOn) return
        isOn = on
        onChanged(on)
    }
}
