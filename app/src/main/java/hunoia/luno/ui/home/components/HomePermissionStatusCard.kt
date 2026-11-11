package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.facade.PrivilegedPermission
import hunoia.luno.facade.PrivilegedPermissionStatus
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.settings.permission.hint
import hunoia.luno.ui.settings.permission.title

/** 主页权限状态卡关注的权限：核心（悬浮窗/无障碍）、通知（使用权/通知）、系统（写设置/电池）。 */
internal val homeTrackedPermissions: List<PrivilegedPermission> = listOf(
    PrivilegedPermission.OVERLAY,
    PrivilegedPermission.ACCESSIBILITY,
    PrivilegedPermission.NOTIFICATION_LISTENER,
    PrivilegedPermission.NOTIFICATIONS,
    PrivilegedPermission.WRITE_SECURE_SETTINGS,
    PrivilegedPermission.BATTERY,
)

/** 从权限快照挑出「关注且未授权」的项；有缺失时主页才显示权限状态卡。 */
internal fun List<PrivilegedPermissionStatus>.missingHomePermissions(): List<PrivilegedPermission> {
    val granted = this.filter { it.granted }.map { it.permission }.toSet()
    return homeTrackedPermissions.filterNot { it in granted }
}

/**
 * 主页权限状态卡：仅在存在缺失权限时出现，列出每一项缺失权限，点任意行去授权总览页。
 * 放在运行状态卡下方；无缺失时不组合（不占空间）。
 */
@Composable
fun HomePermissionStatusCard(
    missing: List<PrivilegedPermission>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(
        title = stringResource(R.string.home_permission_status_title),
        modifier = modifier,
    ) {
        LunoSegmentedGroup {
            missing.forEach { permission ->
                item { shape ->
                    LunoSettingsRow(
                        title = permission.title(),
                        subtitle = permission.hint(),
                        icon = Icons.Filled.Warning,
                        shape = shape,
                        onClick = onOpen,
                    )
                }
            }
        }
    }
}
