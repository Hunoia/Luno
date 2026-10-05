package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.bridge.system.isAccessibilitySettingsOn
import hunoia.luno.config.ConfigProvider
import hunoia.luno.service.DispatchClient
import hunoia.luno.service.SideGestureService

data class HomePermissionState(
    val isGestureEnabled: Boolean,
    val isAccessibilityEnabled: Boolean,
    val isKeepAliveEnabled: Boolean,
)

object PermissionStateUseCase {

    suspend fun loadHomePermissionState(context: Context): HomePermissionState {
        val appContext = context.applicationContext
        val gestureEnabledSetting = ConfigProvider.getInitialSettings().gestureEnabled
        val accessibilityEnabled = appContext.isAccessibilitySettingsOn(SideGestureService::class.java) ||
            DispatchClient.isReady()
        val hasWriteSecureSettings = appContext.hasWriteSecureSettingsPermission()
        val keepAliveEnabledSetting = ConfigProvider.getAdvancedSettings().keepAliveEnabled
        return HomePermissionState(
            isGestureEnabled = gestureEnabledSetting,
            isAccessibilityEnabled = accessibilityEnabled,
            isKeepAliveEnabled = keepAliveEnabledSetting && hasWriteSecureSettings,
        )
    }
}
