package hunoia.luno.domain.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.service.SideGestureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 直接写 Settings.Secure 启用无障碍服务（需 WRITE_SECURE_SETTINGS，通常经 Shizuku 授予）。
 * 与 DaemonService.restoreIfNeeded 同语义：服务未在列表则追加，ACCESSIBILITY_ENABLED 置 1。
 * 保留 restoreIfNeeded 作为保活链路的兜底。
 *
 * [forceRebind]：token 已在但连接可能已断（进程崩溃后系统未重绑）时，摘除再写回
 * 触发系统重新绑定。仅摘除本服务的 token，其它无障碍组件保留。
 */
object EnableAccessibilityUseCase {

    suspend fun enable(context: Context, forceRebind: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!appContext.hasWriteSecureSettingsPermission()) return@withContext false
        val componentName = ComponentName(appContext, SideGestureService::class.java)
        val serviceId = componentName.flattenToShortString()
        val resolver = appContext.contentResolver
        val current = runCatching {
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: ""

        if (forceRebind && AccessibilitySettings.tokenContains(current, componentName)) {
            // 崩溃后 token 还在但连接已断：先摘掉再写回，让系统走一遍 unbind → bind。
            val removed = removeToken(current, serviceId)
            runCatching {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, removed)
            }
            // 给系统一点时间处理 unbind，再写回加我们的组件。
            delay(150)
            val after = runCatching {
                Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull() ?: ""
            val newValue = if (after.isBlank()) serviceId else "$serviceId:$after"
            runCatching {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newValue)
            }
        } else if (!AccessibilitySettings.tokenContains(current, componentName)) {
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

    /** 从冒号分隔列表里摘掉本服务的 token，保留其它组件。 */
    private fun removeToken(value: String, serviceId: String): String =
        value.split(":")
            .filter { it.isNotBlank() && it != serviceId }
            .joinToString(":")
}
