package hunoia.luno.domain.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.runtime.automation.AutomationNotificationListener
import hunoia.luno.service.SideGestureService
import hunoia.luno.shizuku.PrivilegedGrants

/**
 * 一项特权授权：能查状态、能跳对应系统设置页、能用 Shizuku 直接授予。
 *
 * 放在 domain：UI 只认门面，不直接触达 `:service` 的组件类与 `runtime` 的实现。
 */
enum class PrivilegedPermission(
    /** 除 Shizuku 外还能去系统设置页自己开的项；`WRITE_SECURE_SETTINGS` 只能经 shell 授予。 */
    val hasSettingsPage: Boolean,
) {
    OVERLAY(hasSettingsPage = true),
    ACCESSIBILITY(hasSettingsPage = true),
    NOTIFICATION_LISTENER(hasSettingsPage = true),
    WRITE_SECURE_SETTINGS(hasSettingsPage = false),
    BATTERY(hasSettingsPage = true),
    NOTIFICATIONS(hasSettingsPage = true),
}

data class PrivilegedPermissionStatus(
    val permission: PrivilegedPermission,
    val granted: Boolean,
)

/** 一键补齐的结果：没有 Shizuku 时 [results] 为空，调用方按失败处理。 */
data class PrivilegedGrantSummary(
    val total: Int = 0,
    val failed: Int = 0,
) {
    val succeeded: Boolean get() = total > 0 && failed == 0
}

object PrivilegedPermissionUseCase {

    fun status(context: Context): List<PrivilegedPermissionStatus> =
        PrivilegedPermission.entries.map { permission ->
            PrivilegedPermissionStatus(permission, isGranted(context, permission))
        }

    fun isGranted(context: Context, permission: PrivilegedPermission): Boolean = when (permission) {
        PrivilegedPermission.OVERLAY -> Settings.canDrawOverlays(context)
        PrivilegedPermission.ACCESSIBILITY ->
            AccessibilitySettings.isEnabled(context, SideGestureService::class.java)
        PrivilegedPermission.NOTIFICATION_LISTENER ->
            AutomationNotificationListener.isAccessGranted(context)
        PrivilegedPermission.WRITE_SECURE_SETTINGS ->
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        PrivilegedPermission.BATTERY -> runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
        PrivilegedPermission.NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun settingsIntent(context: Context, permission: PrivilegedPermission): Intent? = when (permission) {
        PrivilegedPermission.OVERLAY ->
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        PrivilegedPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PrivilegedPermission.NOTIFICATION_LISTENER ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        PrivilegedPermission.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        PrivilegedPermission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            }
        PrivilegedPermission.WRITE_SECURE_SETTINGS -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 用 Shizuku 只授这一项。 */
    suspend fun grant(context: Context, permission: PrivilegedPermission): Boolean =
        when (permission) {
            PrivilegedPermission.OVERLAY -> PrivilegedGrants.ensureOverlay(context).ok
            PrivilegedPermission.ACCESSIBILITY -> PrivilegedGrants.ensureAccessibilityService(
                context,
                ComponentName(context, SideGestureService::class.java).flattenToShortString(),
            ).ok
            PrivilegedPermission.NOTIFICATION_LISTENER ->
                PrivilegedGrants.ensureNotificationListener(
                    context,
                    ComponentName(context, AutomationNotificationListener::class.java).flattenToShortString(),
                ).ok
            PrivilegedPermission.WRITE_SECURE_SETTINGS ->
                PrivilegedGrants.ensureWriteSecureSettings(context).ok
            PrivilegedPermission.BATTERY -> PrivilegedGrants.ensureBatteryWhitelist(context).ok
            PrivilegedPermission.NOTIFICATIONS -> PrivilegedGrants.ensureNotifications(context).ok
        }

    suspend fun grantAll(context: Context): PrivilegedGrantSummary {
        val results = runCatching {
            PrivilegedGrants.grantAll(
                context = context,
                accessibilityComponent = ComponentName(context, SideGestureService::class.java)
                    .flattenToShortString(),
                notificationListenerComponent = ComponentName(
                    context,
                    AutomationNotificationListener::class.java,
                ).flattenToShortString(),
            )
        }.getOrNull() ?: return PrivilegedGrantSummary()
        return PrivilegedGrantSummary(
            total = results.size,
            failed = results.count { !it.ok },
        )
    }
}
