package hunoia.luno.runtime.automation

import android.content.Context
import android.content.Intent

/**
 * 分享/链接入口在 App 主进程，自动化协调器在 `:service` 进程。
 * 用一条仅本应用可收的广播把原文送过去，避免把规则引擎复制到 Activity 进程。
 */
object AutomationShareBridge {
    const val ACTION = "hunoia.luno.action.AUTOMATION_SHARE"
    const val EXTRA_TEXT = "text"
    const val EXTRA_SOURCE = "source"

    fun publish(context: Context, text: String, source: String) {
        if (text.isBlank()) return
        runCatching {
            context.sendBroadcast(
                Intent(ACTION)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_SOURCE, source),
            )
        }
    }
}
