package hunoia.luno.keepalive

import android.content.Context
import android.content.Intent
import hunoia.luno.R
import hunoia.luno.bridge.hasWriteSecureSettingsPermission
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
            appContext.startForegroundService(intent)
        } else {
            appContext.stopService(intent)
        }
        return true
    }
}
