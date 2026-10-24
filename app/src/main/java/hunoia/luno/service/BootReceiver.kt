package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启：拉起保活与悬浮球两个常驻前台服务。
 *
 * 判定逻辑与「应用更新后恢复」共用 [ServiceAutoStart] ——
 * 两处各写一份判断迟早漂移（开机拉起、更新后不拉起）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                ServiceAutoStart.restore(context)
            }
        }
    }
}
