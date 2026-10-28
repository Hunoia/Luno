package hunoia.luno.runtime.automation

import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知 `PendingIntent` 的 runtime 侧旁路。
 *
 * `NotificationPulse` 在契约层（纯 JVM），不能带 `PendingIntent`；通知胶囊点击时
 * 按通知 `key` 从这里取。只在 `:service` 进程内使用，不跨进程。
 */
object NotificationContentIntents {

    private val intents = ConcurrentHashMap<String, PendingIntent>()

    fun remember(key: String?, intent: PendingIntent?) {
        if (key.isNullOrEmpty()) return
        if (intent == null) intents.remove(key) else intents[key] = intent
    }

    fun forget(key: String?) {
        if (!key.isNullOrEmpty()) intents.remove(key)
    }

    fun get(key: String?): PendingIntent? = key?.let(intents::get)
}
