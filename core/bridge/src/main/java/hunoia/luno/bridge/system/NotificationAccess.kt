package hunoia.luno.bridge.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * 通知使用权（`BIND_NOTIFICATION_LISTENER_SERVICE`）的只读查询。
 * 平台 API 收口 `bridge`；domain / UI 不直接读 Settings.Secure。
 */
object NotificationAccess {

    /** 通知监听服务的实现类；组件在 `:app` 清单声明，跨模块只认这个字符串。 */
    const val LISTENER_CLASS = "hunoia.luno.runtime.floatball.notification.NotificationPulseListener"

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context.packageName, LISTENER_CLASS)

    fun isGranted(context: Context): Boolean {
        val flat = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            )
        }.getOrNull().orEmpty()
        return isListenerEnabled(flat, listenerComponent(context).flattenToString())
    }

    /**
     * Checks the exact listener component instead of only the package name.
     *
     * The secure setting can retain another listener from the same package (or
     * a stale component after an update). Treating the package alone as granted
     * makes the permission page report success while this listener is disabled.
     */
    internal fun isListenerEnabled(value: String?, expected: String): Boolean {
        if (value.isNullOrBlank()) return false
        return value.split(':').any { raw ->
            raw.trim() == expected
        }
    }
}
