package hunoia.luno.facade

import android.Manifest

/** 授权项的分组：按功能域归类，UI 据此把权限列拆成多个组展示。 */
enum class PrivilegedPermissionGroup {
    /** 核心：悬浮窗、无障碍，缺了 Luno 无法运行。 */
    CORE,
    /** 通知：通知使用权、App 自身通知权限、勿扰访问。 */
    NOTIFICATION,
    /** 系统增强：系统设置写入、电池白名单、存储与改系统设置。 */
    SYSTEM,
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
    /** 是否进主页摘要卡：缺了会影响 Luno 自身功能。其余项只在授权页列出，不打扰主页。 */
    val homeTracked: Boolean = false,
) {
    OVERLAY(hasSettingsPage = true, group = PrivilegedPermissionGroup.CORE, shizukuGrantable = true, homeTracked = true),
    ACCESSIBILITY(hasSettingsPage = true, group = PrivilegedPermissionGroup.CORE, shizukuGrantable = true, homeTracked = true),
    NOTIFICATION_LISTENER(hasSettingsPage = true, group = PrivilegedPermissionGroup.NOTIFICATION, shizukuGrantable = true, homeTracked = true),
    WRITE_SECURE_SETTINGS(hasSettingsPage = false, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = true, homeTracked = true),
    BATTERY(hasSettingsPage = true, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = true, homeTracked = true),
    NOTIFICATIONS(hasSettingsPage = true, group = PrivilegedPermissionGroup.NOTIFICATION, shizukuGrantable = true, homeTracked = true),
    // 以下 Shizuku 授不了，只能手动去系统设置开，但仍要列出来让用户知道 / 去开。
    MANAGE_FILES(hasSettingsPage = true, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = false),
    WRITE_SYSTEM_SETTINGS(hasSettingsPage = true, group = PrivilegedPermissionGroup.SYSTEM, shizukuGrantable = false),
    NOTIFICATION_POLICY(hasSettingsPage = true, group = PrivilegedPermissionGroup.NOTIFICATION, shizukuGrantable = false),
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

/** 主页摘要卡跟踪的权限：枚举声明顺序即展示顺序。 */
val homeTrackedPermissions: List<PrivilegedPermission> =
    PrivilegedPermission.entries.filter { it.homeTracked }

/**
 * 主页摘要卡要提示的缺失项："必须" 与 "降级" 不分开，只数缺口——主页只给一句摘要，
 * 逐项解释留给授权页。
 */
fun List<PrivilegedPermissionStatus>.missingHomePermissions(): List<PrivilegedPermission> {
    val granted = filter { it.granted }.map { it.permission }.toSet()
    return homeTrackedPermissions.filterNot { it in granted }
}

/** 一键补齐的结果：没有 Shizuku 时 [results] 为空，调用方按失败处理。 */
data class PrivilegedGrantSummary(
    val total: Int = 0,
    val failed: Int = 0,
) {
    val succeeded: Boolean get() = total > 0 && failed == 0
}
