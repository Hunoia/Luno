package hunoia.luno.runtime.floatball.notification

import android.app.PendingIntent
import java.util.LinkedHashMap

/**
 * 通知 `PendingIntent` 的 runtime 侧旁路。
 *
 * `NotificationPulse` 在契约层（纯 JVM），不能带 `PendingIntent`；通知胶囊点击时
 * 按通知 `key` 从这里取。只在 `:service` 进程内使用，不跨进程。
 *
 * 生命周期跟「胶囊条目」走，不跟「系统通知是否还在通知栏」走：开启「只显示胶囊」后
 * 通知会被我们 cancel/snooze（通知栏里没有它），但点击仍要用它的 PI 直达目标界面。
 * 因此这里不做 `onNotificationRemoved` 式逐条清除，只按 LRU 上限收敛，防缓慢累积。
 */
object NotificationContentIntents {

    /** 上限：通知 key 唯一，同 key 重投会覆盖；活跃条目远小于此，超出说明是陈旧的。 */
    private const val MAX_ENTRIES = 64

    private val lock = Any()
    private val intents = LinkedHashMap<String, PendingIntent>(16, 0.75f, true)

    fun remember(key: String?, intent: PendingIntent?) {
        if (key.isNullOrEmpty()) return
        synchronized(lock) {
            if (intent == null) {
                intents.remove(key)
            } else {
                intents[key] = intent
                while (intents.size > MAX_ENTRIES) {
                    // accessOrder=true：淘汰最久未用的那条。
                    val eldest = intents.entries.firstOrNull() ?: break
                    intents.remove(eldest.key)
                }
            }
        }
    }

    fun get(key: String?): PendingIntent? {
        if (key.isNullOrEmpty()) return null
        synchronized(lock) { return intents[key] }
    }

    /** 监听器真拆解（`onDestroy`）时清空：通知快照已失效，避免 PI 残留累积。 */
    fun clear() {
        synchronized(lock) { intents.clear() }
    }
}
