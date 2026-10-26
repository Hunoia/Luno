package hunoia.luno.domain.system

import android.content.Context
import android.content.Intent
import hunoia.luno.R
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.config.ConfigProvider
import hunoia.luno.service.DaemonService

object KeepAliveUseCase {

    suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
        onPermissionRequired: (Int) -> Unit,
    ): Boolean {
        val appContext = context.applicationContext
        if (enabled && !appContext.hasWriteSecureSettingsPermission()) {
            onPermissionRequired(R.string.keep_alive_need_permission)
            return false
        }

        ConfigProvider.updateAdvancedSettings { it.copy(keepAliveEnabled = enabled) }
        appContext.getSharedPreferences("daemon", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("keep_alive", enabled)
            .apply()

        val intent = Intent(appContext, DaemonService::class.java)
        if (enabled) {
            // 显式动作 = 用户刚打开开关（磁盘写发生在 start 之前）：daemon 已归并到
            // :service 进程，SharedPreferences 跨进程缓存不可靠，不能让 daemon
            // 再拿开关自判把这次启动否决掉。
            appContext.startForegroundService(intent.setAction(DaemonService.ACTION_USER_ENABLE))
        } else {
            appContext.stopService(intent)
        }
        return true
    }
}
