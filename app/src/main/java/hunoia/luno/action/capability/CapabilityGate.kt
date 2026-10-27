package hunoia.luno.action.capability

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.core.domain.Capability
import hunoia.luno.bridge.system.isAccessibilitySettingsOn
import hunoia.luno.service.SideGestureService

/**
 * 每个 capability 由唯一对应的检查负责。不能用多个 checker + `any`：各 checker
 * 对不归自己管的 capability 返回 true，会让闸门恒放行。
 */
class CapabilityGate(
    private val appContext: Context,
    private val isServiceAlive: () -> Boolean = {
        hunoia.luno.service.DispatchClient.isReady() ||
            SideGestureService.current != null
    },
) {
    fun isAvailable(capability: Capability): Boolean = when (capability) {
        Capability.None -> true
        Capability.Shizuku -> hunoia.luno.shizuku.ShizukuFacade.isReady()
        Capability.Accessibility ->
            appContext.isAccessibilitySettingsOn(SideGestureService::class.java) ||
                isServiceAlive()
        Capability.Notification -> {
            val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            permissionOk && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
    }
}
