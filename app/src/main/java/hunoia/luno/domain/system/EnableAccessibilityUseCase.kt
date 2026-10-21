package hunoia.luno.domain.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.service.SideGestureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 直接写 Settings.Secure 启用无障碍服务（需 WRITE_SECURE_SETTINGS，通常经 Shizuku 授予）。
 * 与 DaemonService.restoreIfNeeded 同语义：服务未在列表则追加，ACCESSIBILITY_ENABLED 置 1。
 * 保留 restoreIfNeeded 作为保活链路的兜底。
 */
object EnableAccessibilityUseCase {

    suspend fun enable(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!appContext.hasWriteSecureSettingsPermission()) return@withContext false
        val componentName = ComponentName(appContext, SideGestureService::class.java)
        val serviceId = componentName.flattenToShortString()
        val resolver = appContext.contentResolver
        val current = runCatching {
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: ""

        if (!tokenContains(current, componentName)) {
            val newValue = if (current.isBlank()) serviceId else "$serviceId:$current"
            runCatching {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newValue,
                )
            }.getOrElse { return@withContext false }
        }
        runCatching {
            Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        }.getOrElse { return@withContext false }
        true
    }

    private fun tokenContains(value: String, componentName: ComponentName): Boolean {
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(value)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == componentName) return true
        }
        return false
    }
}
