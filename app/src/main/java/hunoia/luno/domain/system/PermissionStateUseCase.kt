package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.facade.HomePermissionState
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.bridge.system.isAccessibilitySettingsOn
import hunoia.luno.config.ConfigProvider
import hunoia.luno.service.DispatchClient
import hunoia.luno.service.SideGestureService
import kotlinx.coroutines.flow.StateFlow

object PermissionStateUseCase {

    /**
     * 服务进程连接就绪流。UI 订阅它来刷新状态卡（冷启动 bind 异步，首读会误报「暂不可用」）；
     * UI 边界禁止直连 `:service` 客户端（UiBoundaryTest），所以从这里转发。
     */
    val dispatchReadyFlow: StateFlow<Boolean> get() = DispatchClient.readyFlow

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
