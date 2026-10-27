package hunoia.luno.action.capability

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.core.domain.Capability

/**
 * 每个 capability 由唯一对应的检查负责。不能用多个 checker + `any`：各 checker
 * 对不归自己管的 capability 返回 true，会让闸门恒放行。
 *
 * 无障碍是否可用依赖宿主（服务存活 / 设置页开关），由 `:app` 注入：
 * 执行口不 import `:app` 的服务类。
 */
class CapabilityGate(
    private val appContext: Context,
    private val isAccessibilityAvailable: () -> Boolean,
    private val isShizukuAvailable: () -> Boolean = { hunoia.luno.shizuku.ShizukuFacade.isReady() },
) {
    fun isAvailable(capability: Capability): Boolean = when (capability) {
        Capability.None -> true
        Capability.Shizuku -> isShizukuAvailable()
        Capability.Accessibility -> isAccessibilityAvailable()
        Capability.Notification -> {
            val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            permissionOk && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
    }
}
