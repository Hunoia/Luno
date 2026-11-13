package hunoia.luno.domain.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.facade.PrivilegedGrantSummary
import hunoia.luno.facade.PrivilegedPermission
import hunoia.luno.facade.PrivilegedPermissionStatus
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.bridge.system.NotificationAccess
import hunoia.luno.service.SideGestureService
import hunoia.luno.shizuku.PrivilegedGrants
import hunoia.luno.core.LunoLog

object PrivilegedPermissionUseCase {

    private const val LOG_TAG = "PrivilegedPermissions"

    fun status(context: Context): List<PrivilegedPermissionStatus> =
        PrivilegedPermission.entries.map { permission ->
            PrivilegedPermissionStatus(permission, isGranted(context, permission))
        }

    fun isGranted(context: Context, permission: PrivilegedPermission): Boolean = when (permission) {
        PrivilegedPermission.OVERLAY -> Settings.canDrawOverlays(context)
        PrivilegedPermission.ACCESSIBILITY ->
            AccessibilitySettings.isEnabled(context, SideGestureService::class.java)
        PrivilegedPermission.NOTIFICATION_LISTENER ->
            NotificationAccess.isGranted(context)
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
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
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
        // 其余标准运行期权限：去应用设置页手动开（无直达 intent）。
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 用 Shizuku 只授这一项。 */
    suspend fun grant(context: Context, permission: PrivilegedPermission): Boolean =
        grant(context, listOf(permission)).let { it.total > 0 && it.failed == 0 }

    /**
     * 只授指定项：逐项走各自的 shell 命令，已授权项直接成功（幂等）。
     * 与 [grantAll] 的区别是**不额外扩权**到未请求的运行期权限。
     */
    suspend fun grant(
        context: Context,
        permissions: List<PrivilegedPermission>,
    ): PrivilegedGrantSummary {
        if (permissions.isEmpty()) return PrivilegedGrantSummary()
        val results = permissions.map { grantOne(context, it) }
        return PrivilegedGrantSummary(
            total = results.size,
            failed = results.count { !it.ok },
        ).also { summary ->
            LunoLog.i(
                LOG_TAG,
                "grant pkg=${context.packageName} requested=${permissions.joinToString()} " +
                    "failed=${summary.failed} results=" +
                    results.joinToString { "${it.item}:${it.ok}:${it.detail}" },
            )
        }
    }

    private suspend fun grantOne(context: Context, permission: PrivilegedPermission): PrivilegedGrants.Result =
        when (permission) {
            PrivilegedPermission.OVERLAY -> PrivilegedGrants.ensureOverlay(context)
            PrivilegedPermission.ACCESSIBILITY -> PrivilegedGrants.ensureAccessibilityService(
                context,
                ComponentName(context, SideGestureService::class.java).flattenToString(),
            )
            PrivilegedPermission.NOTIFICATION_LISTENER ->
                PrivilegedGrants.ensureNotificationListener(
                    context,
                    NotificationAccess.listenerComponent(context).flattenToString(),
                )
            PrivilegedPermission.WRITE_SECURE_SETTINGS ->
                PrivilegedGrants.ensureWriteSecureSettings(context)
            PrivilegedPermission.BATTERY -> PrivilegedGrants.ensureBatteryWhitelist(context)
            PrivilegedPermission.NOTIFICATIONS -> PrivilegedGrants.ensureNotifications(context)
            // Shizuku 授不了的特殊 / 隐私敏感权限：只能手动去系统设置开，UI 走「去设置」。
            PrivilegedPermission.MANAGE_FILES,
            PrivilegedPermission.WRITE_SYSTEM_SETTINGS,
            PrivilegedPermission.NOTIFICATION_POLICY ->
                PrivilegedGrants.Result(
                    item = PrivilegedGrants.Item.Permission(permission.name),
                    ok = false,
                    detail = "只能手动去系统设置开启",
                )
            // 标准运行期权限：Shizuku `pm grant`。
            else -> permission.androidPermission?.let {
                PrivilegedGrants.ensurePermission(context, it)
            } ?: PrivilegedGrants.Result(
                item = PrivilegedGrants.Item.Permission(permission.name),
                ok = false,
                detail = "没有可用的授权途径",
            )
        }

    suspend fun grantAll(context: Context): PrivilegedGrantSummary {
        val results = runCatching {
            PrivilegedGrants.grantAll(
                context = context,
                accessibilityComponent = ComponentName(context, SideGestureService::class.java)
                    .flattenToString(),
                notificationListenerComponent = NotificationAccess
                    .listenerComponent(context)
                    .flattenToString(),
            )
        }.getOrNull() ?: return PrivilegedGrantSummary()
        return PrivilegedGrantSummary(
            total = results.size,
            failed = results.count { !it.ok },
        ).also { summary ->
            LunoLog.i(
                LOG_TAG,
                "grantAll pkg=${context.packageName} total=${summary.total} failed=${summary.failed} " +
                    "results=${results.joinToString { "${it.item}:${it.ok}:${it.detail}" }}",
            )
        }
    }
}
