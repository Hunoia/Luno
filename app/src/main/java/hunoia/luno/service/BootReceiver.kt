package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.core.AppContext
import hunoia.luno.domain.habit.HabitReminderScheduler
import hunoia.luno.domain.todo.TodoReminderScheduler
import hunoia.luno.domain.trend.TrendReminderScheduler
import hunoia.luno.launch.PackageControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机自启：拉起保活与悬浮球两个常驻前台服务。
 *
 * 判定逻辑与「应用更新后恢复」共用 [ServiceAutoStart] ——
 * 两处各写一份判断迟早漂移（开机拉起、更新后不拉起）。
 *
 * 只认解锁后的 BOOT_COMPLETED：应用数据全在 CE 存储（无 deviceProtectedStorage），
 * 锁屏期（LOCKED_BOOT_COMPLETED / directBootAware）读不到任何配置，只剩
 * 「未解锁被拉起 → 读 CE 崩溃 → START_STICKY 反复重启」的风险，没有收益。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ServiceAutoStart.restore(context)
            // 习惯 / 待办 / 趋势提醒闹钟是系统持久化之外的（AlarmManager 在重启后清空），开机重建。
            // 待办调度器同时负责「错过提醒」补弹：标记还在、时间已过 → 静默补一条通知。
            HabitReminderScheduler.rescheduleAll(context)
            TodoReminderScheduler.rescheduleAll(context)
            TrendReminderScheduler.rescheduleAll(context)
            // 一键冻结名单里的已卸载包：开机清理一次；页面进入时还会再兜一次。
            AppContext.applicationScope?.launch(Dispatchers.IO) {
                runCatching { PackageControl.pruneUninstalledFromOneKey() }
            }
        }
    }
}
