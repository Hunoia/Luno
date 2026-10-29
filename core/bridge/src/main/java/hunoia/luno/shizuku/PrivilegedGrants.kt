package hunoia.luno.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * 用 Shizuku 的 shell 身份把「用户本来要手动去设置页点一遍」的授权一次性办掉。
 *
 * 定位：**授权执行体**，不是能力闸。能力闸仍以 `CapabilityGate` 为准，本对象只负责把
 * 闸门的输入侧补齐；调用点在 UI / 组装根（`:app`），执行口不感知。
 *
 * 全部幂等：已授权的项不发命令，直接返回成功。
 */
object PrivilegedGrants {

    sealed interface Item {
        data object Overlay : Item
        data object WriteSecureSettings : Item
        data object AccessibilityService : Item
        data object NotificationListener : Item
        data object BatteryWhitelist : Item
        data object Notifications : Item

        /** Android 运行期权限（`pm grant`）。 */
        data class Permission(val name: String) : Item
    }

    data class Result(
        val item: Item,
        val ok: Boolean,
        val detail: String = "",
    )

    /**
     * 一次性补齐常用授权。
     *
     * @param accessibilityComponent 无障碍服务组件（`pkg/cls`），null 表示不尝试。
     * @param notificationListenerComponent 通知监听组件（`pkg/cls`），null 表示不尝试。
     */
    suspend fun grantAll(
        context: Context,
        accessibilityComponent: String?,
        notificationListenerComponent: String?,
    ): List<Result> = buildList {
        add(ensureOverlay(context))
        add(ensureWriteSecureSettings(context))
        accessibilityComponent?.let { add(ensureAccessibilityService(context, it)) }
        notificationListenerComponent?.let { add(ensureNotificationListener(context, it)) }
        add(ensureBatteryWhitelist(context))
        addAll(grantDeclaredRuntimePermissions(context))
    }

    suspend fun ensureOverlay(context: Context): Result {
        if (Settings.canDrawOverlays(context)) {
            return Result(Item.Overlay, true, "已授权")
        }
        val result = ShizukuManager.executeShell(
            "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
        )
        return Result(
            Item.Overlay,
            Settings.canDrawOverlays(context),
            result.errorMessage.ifBlank { "exit=${result.exitCode}" },
        )
    }

    suspend fun ensureWriteSecureSettings(context: Context): Result {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result(Item.WriteSecureSettings, true, "已授权")
        }
        val result = ShizukuManager.executeShell(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
        )
        return Result(Item.WriteSecureSettings, result.isSuccess, result.errorMessage)
    }

    /** 无障碍服务启用：直写 secure 设置，shell 持有 WRITE_SECURE_SETTINGS。 */
    suspend fun ensureAccessibilityService(context: Context, component: String): Result {
        val enabled = readEnabledServices(context)
        val alreadyOn = enabled.contains(component)
        if (alreadyOn && isAccessibilityEnabled(context)) {
            return Result(Item.AccessibilityService, true, "已启用")
        }
        val next = if (alreadyOn) enabled else listOf(component) + enabled
        val putServices = ShizukuManager.executeShell(
            "settings put secure enabled_accessibility_services '${next.joinToString(":")}'",
        )
        val putEnabled = ShizukuManager.executeShell("settings put secure accessibility_enabled 1")
        val ok = putServices.isSuccess && putEnabled.isSuccess &&
            readEnabledServices(context).contains(component)
        return Result(
            Item.AccessibilityService,
            ok,
            putServices.errorMessage.ifBlank { putEnabled.errorMessage },
        )
    }

    /** 通知监听：同样落在 secure 设置里，启用后系统会自己拉起绑定。 */
    suspend fun ensureNotificationListener(context: Context, component: String): Result {
        val current = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            )
        }.getOrNull().orEmpty()
        if (current.split(':').any { it.equals(component, ignoreCase = true) }) {
            return Result(Item.NotificationListener, true, "已启用")
        }
        val next = if (current.isBlank()) component else "$component:$current"
        val result = ShizukuManager.executeShell(
            "settings put secure enabled_notification_listeners '$next'",
        )
        return Result(Item.NotificationListener, result.isSuccess, result.errorMessage)
    }

    /** 通知权限（13+ 的 POST_NOTIFICATIONS，用 `pm grant` 走 Shizuku）。 */
    suspend fun ensureNotifications(context: Context): Result {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return Result(Item.Notifications, true, "系统版本无需该权限")
        }
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            return Result(Item.Notifications, true, "已授权")
        }
        val result = ShizukuManager.executeShell("pm grant ${context.packageName} $permission")
        return Result(Item.Notifications, result.isSuccess, result.errorMessage)
    }

    /** 电池优化白名单：`deviceidle whitelist +pkg` 幂等，重复执行无副作用。 */
    suspend fun ensureBatteryWhitelist(context: Context): Result {
        val result = ShizukuManager.executeShell(
            "dumpsys deviceidle whitelist +${context.packageName}",
        )
        return Result(Item.BatteryWhitelist, result.isSuccess, result.errorMessage)
    }

    /**
     * 授予**清单里已经声明**的全部运行期权限（不含未声明权限，避免静默扩权）。
     * 只在用户显式点「自动补齐授权」时调用，不在启动路径上跑。
     */
    suspend fun grantDeclaredRuntimePermissions(context: Context): List<Result> = with(context.packageManager) {
        val declared = runCatching {
            getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS,
            ).requestedPermissions?.toList().orEmpty()
        }.getOrDefault(emptyList())
        val targetSdk = runCatching {
            getPackageInfo(context.packageName, 0).applicationInfo?.targetSdkVersion ?: 0
        }.getOrDefault(0)
        if (targetSdk >= android.os.Build.VERSION_CODES.M && declared.isEmpty()) {
            return emptyList()
        }
        declared
            .filter { it.startsWith("android.permission.") }
            // 定位是隐私敏感授权，不给"顺手全授"的路径：需要时由用户自己去系统设置开。
            .filterNot { it == android.Manifest.permission.ACCESS_FINE_LOCATION }
            .filterNot { it == android.Manifest.permission.ACCESS_COARSE_LOCATION }
            .filter { permission ->
                context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }
            .map { permission ->
                val result = ShizukuManager.executeShell(
                    "pm grant ${context.packageName} $permission",
                )
                Result(Item.Permission(permission), result.isSuccess, result.errorMessage)
            }
    }

    private fun readEnabledServices(context: Context): List<String> = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
    }.getOrNull()
        ?.split(':')
        ?.filter { it.isNotBlank() }
        .orEmpty()

    private fun isAccessibilityEnabled(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
    }.getOrDefault(false)
}
