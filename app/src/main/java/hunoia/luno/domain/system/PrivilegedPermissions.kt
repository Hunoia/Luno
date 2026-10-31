package hunoia.luno.domain.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.runtime.automation.AutomationNotificationListener
import hunoia.luno.service.SideGestureService
import hunoia.luno.shizuku.PrivilegedGrants

/** 授权项的分组：按功能域归类，UI 据此把权限列拆成多个组展示。 */
enum class PrivilegedPermissionGroup {
    /** 核心：悬浮窗、无障碍，缺了 Luno 无法运行。 */
    CORE,
    /** 通知：通知使用权、App 自身通知权限、勿扰访问。 */
    NOTIFICATION,
    /** 系统增强：系统设置写入、电池白名单、存储与改系统设置。 */
    SYSTEM,
    /** 定位：位置权限，只能手动开。 */
    LOCATION,
    /** 应用权限：普通运行期危险权限（相机 / 媒体 / 联系人等），Shizuku 可 `pm grant`。 */
    APP,
}

/**
 * 一项特权授权：能查状态、能跳对应系统设置页、能用 Shizuku 直接授予。
 *
 * 放在 domain：UI 只认门面，不直接触达 `:service` 的组件类与 `runtime` 的实现。
 */
enum class PrivilegedPermission(
    /** 除 Shizuku 外还能去系统设置页自己开的项；`WRITE_SECURE_SETTINGS` 只能经 shell 授予。 */
    val hasSettingsPage: Boolean,
    /** 分组：UI 据此把权限列拆成多个组。 */
    val group: PrivilegedPermissionGroup,
    /** Shizuku 能否直接授予：不能的（特殊 / 隐私敏感权限）只能手动去系统设置开，但仍要列出来。 */
    val shizukuGrantable: Boolean,
    /** 标准 `android.permission.*` 运行期权限名；非空走 checkSelfPermission / `pm grant`，null 走上面的特殊判定。 */
    val androidPermission: String? = null,
) {
    OVERLAY(hasSettingsPage = true, group = PrivilegedPermissionGroup.CORE, shizukuGrantable = true),
    ACCESSIBILITY(hasSettingsPage = true, group = PrivilegedPermissionGroup.CORE, shizukuGrantable = true),
    NOTIFICATION_LISTENER(hasSettingsPage = true, group = PrivilegedPermissionGroup.NOTIFICATION, shizukuGrantable = true),
    WRITE_SECURE_SETTINGS(hasSettingsPage = false, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = true),
    BATTERY(hasSettingsPage = true, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = true),
    NOTIFICATIONS(hasSettingsPage = true, group = PrivilegedPermissionGroup.NOTIFICATION, shizukuGrantable = true),
    // 以下 Shizuku 授不了，只能手动去系统设置开，但仍要列出来让用户知道 / 去开。
    MANAGE_FILES(hasSettingsPage = true, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = false),
    WRITE_SYSTEM_SETTINGS(hasSettingsPage = true, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = false),
    NOTIFICATION_POLICY(hasSettingsPage = true, group = PrivilegedPermissionGroup.NOTIFICATION, shizukuGrantable = false),
    LOCATION(hasSettingsPage = true, group = PrivilegedPermissionGroup.LOCATION, shizukuGrantable = false),
    // 普通运行期危险权限：清单已声明，Shizuku 可一键 `pm grant`，未授权也能点行去应用设置开。
    CAMERA(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.CAMERA),
    READ_CONTACTS(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_CONTACTS),
    READ_CALENDAR(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_CALENDAR),
    READ_SMS(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_SMS),
    READ_CALL_LOG(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_CALL_LOG),
    READ_MEDIA_IMAGES(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_MEDIA_IMAGES),
    READ_MEDIA_VIDEO(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_MEDIA_VIDEO),
    READ_MEDIA_AUDIO(hasSettingsPage = true, group = PrivilegedPermissionGroup.APP, shizukuGrantable = true, androidPermission = Manifest.permission.READ_MEDIA_AUDIO),
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
        PrivilegedPermission.MANAGE_FILES -> Environment.isExternalStorageManager()
        PrivilegedPermission.WRITE_SYSTEM_SETTINGS -> Settings.System.canWrite(context)
        PrivilegedPermission.NOTIFICATION_POLICY ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        PrivilegedPermission.LOCATION ->
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        // 其余标准运行期权限：直接查 checkSelfPermission。
        else -> permission.androidPermission?.let {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } ?: false
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
        PrivilegedPermission.MANAGE_FILES ->
            Intent(
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        PrivilegedPermission.WRITE_SYSTEM_SETTINGS ->
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        PrivilegedPermission.NOTIFICATION_POLICY ->
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        PrivilegedPermission.LOCATION ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        // 其余标准运行期权限：去应用设置页手动开（无直达 intent）。
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
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
            // 以下 Shizuku 授不了，只能手动去系统设置开，UI 走「去设置」。
            PrivilegedPermission.MANAGE_FILES,
            PrivilegedPermission.WRITE_SYSTEM_SETTINGS,
            PrivilegedPermission.NOTIFICATION_POLICY,
            PrivilegedPermission.LOCATION -> false
            // 标准运行期权限：Shizuku `pm grant`。
            else -> permission.androidPermission?.let {
                PrivilegedGrants.ensurePermission(context, it).ok
            } ?: false
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
