package hunoia.luno.runtime.floatball.notification

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

    /**
     * 只保留当前通知栏里仍存活的 key。
     *
     * `onNotificationRemoved` 在个别 ROM 上不保证逐条回调；重连/重投时按活跃快照
     * 收敛一次，避免 PendingIntent 随通知数量缓慢累积。
     */
    fun retainOnly(keys: Set<String>) {
        val iterator = intents.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() !in keys) iterator.remove()
        }
    }

    /** 监听断开 / 服务销毁时清空：通知快照已失效，避免 PendingIntent 残留累积。 */
    fun clear() {
        intents.clear()
    }
}
