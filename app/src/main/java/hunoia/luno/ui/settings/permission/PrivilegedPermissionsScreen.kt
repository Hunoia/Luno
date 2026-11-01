package hunoia.luno.ui.settings.permission

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.R
import hunoia.luno.domain.AppGraph
import hunoia.luno.domain.system.PrivilegedPermission
import hunoia.luno.domain.system.PrivilegedPermissionGroup
import hunoia.luno.domain.system.PrivilegedPermissionStatus
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

@Composable
private fun PrivilegedPermission.title(): String = stringResource(
    when (this) {
        PrivilegedPermission.OVERLAY -> R.string.perm_overlay
        PrivilegedPermission.ACCESSIBILITY -> R.string.perm_accessibility
        PrivilegedPermission.NOTIFICATION_LISTENER -> R.string.perm_notification_listener
        PrivilegedPermission.WRITE_SECURE_SETTINGS -> R.string.perm_write_secure
        PrivilegedPermission.BATTERY -> R.string.perm_battery
        PrivilegedPermission.NOTIFICATIONS -> R.string.perm_notifications
        PrivilegedPermission.MANAGE_FILES -> R.string.perm_manage_files
        PrivilegedPermission.WRITE_SYSTEM_SETTINGS -> R.string.perm_write_system
        PrivilegedPermission.NOTIFICATION_POLICY -> R.string.perm_notification_policy
        PrivilegedPermission.LOCATION -> R.string.perm_location
        PrivilegedPermission.CAMERA -> R.string.perm_camera
        PrivilegedPermission.READ_CONTACTS -> R.string.perm_read_contacts
        PrivilegedPermission.READ_CALENDAR -> R.string.perm_read_calendar
        PrivilegedPermission.READ_SMS -> R.string.perm_read_sms
        PrivilegedPermission.READ_CALL_LOG -> R.string.perm_read_call_log
        PrivilegedPermission.READ_MEDIA_IMAGES -> R.string.perm_read_media_images
        PrivilegedPermission.READ_MEDIA_VIDEO -> R.string.perm_read_media_video
        PrivilegedPermission.READ_MEDIA_AUDIO -> R.string.perm_read_media_audio
    },
)

@Composable
private fun PrivilegedPermission.hint(): String = stringResource(
    when (this) {
        PrivilegedPermission.OVERLAY -> R.string.perm_overlay_hint
        PrivilegedPermission.ACCESSIBILITY -> R.string.perm_accessibility_hint
        PrivilegedPermission.NOTIFICATION_LISTENER -> R.string.perm_notification_listener_hint
        PrivilegedPermission.WRITE_SECURE_SETTINGS -> R.string.perm_write_secure_hint
        PrivilegedPermission.BATTERY -> R.string.perm_battery_hint
        PrivilegedPermission.NOTIFICATIONS -> R.string.perm_notifications_hint
        PrivilegedPermission.MANAGE_FILES -> R.string.perm_manage_files_hint
        PrivilegedPermission.WRITE_SYSTEM_SETTINGS -> R.string.perm_write_system_hint
        PrivilegedPermission.NOTIFICATION_POLICY -> R.string.perm_notification_policy_hint
        PrivilegedPermission.LOCATION -> R.string.perm_location_hint
        PrivilegedPermission.CAMERA -> R.string.perm_camera_hint
        PrivilegedPermission.READ_CONTACTS -> R.string.perm_read_contacts_hint
        PrivilegedPermission.READ_CALENDAR -> R.string.perm_read_calendar_hint
        PrivilegedPermission.READ_SMS -> R.string.perm_read_sms_hint
        PrivilegedPermission.READ_CALL_LOG -> R.string.perm_read_call_log_hint
        PrivilegedPermission.READ_MEDIA_IMAGES -> R.string.perm_read_media_images_hint
        PrivilegedPermission.READ_MEDIA_VIDEO -> R.string.perm_read_media_video_hint
        PrivilegedPermission.READ_MEDIA_AUDIO -> R.string.perm_read_media_audio_hint
    },
)

@Composable
private fun PrivilegedPermissionGroup.title(): String = stringResource(
    when (this) {
        PrivilegedPermissionGroup.CORE -> R.string.perm_group_core
        PrivilegedPermissionGroup.NOTIFICATION -> R.string.perm_group_notification
        PrivilegedPermissionGroup.SYSTEM -> R.string.perm_group_system
        PrivilegedPermissionGroup.LOCATION -> R.string.perm_group_location
        PrivilegedPermissionGroup.APP -> R.string.perm_group_app
    },
)

/**
 * 授权总览页：每项一行，右侧「授权」只授该项，点行跳对应系统设置页；顶部一键补齐全部。
 *
 * 与「一键补齐」的区别是**可解释性**：这里能看到每一项当前到底授没授、去哪儿改。
 */
@Composable
fun PrivilegedPermissionsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<PrivilegedPermission?>(null) }
    var bulkBusy by remember { mutableStateOf(false) }

    // 从系统设置页回来时重查（授权与撤销都要跟手）。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refresh() {
        tick++
    }

    fun openSettings(permission: PrivilegedPermission) {
        AppGraph.system.privilegedPermissionIntent(context, permission)
            ?.let { intent -> runCatching { context.startActivity(intent) } }
    }

    fun grantOne(permission: PrivilegedPermission) {
        if (busy != null || bulkBusy) return
        scope.launch {
            busy = permission
            if (!ShizukuManager.currentStatus().isReady) {
                runCatching { ShizukuManager.autoRequestPermissionIfNeeded() }
            }
            val ok = runCatching { AppGraph.system.grantPrivilegedPermission(context, permission) }
                .getOrDefault(false)
            busy = null
            // 成功不提示，只靠行内状态色变化；失败用 toast 提示原因。
            if (!ok) {
                Toast.makeText(context, R.string.perm_grant_one_failed, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    fun grantAll() {
        if (bulkBusy || busy != null) return
        scope.launch {
            bulkBusy = true
            if (!ShizukuManager.currentStatus().isReady) {
                runCatching { ShizukuManager.autoRequestPermissionIfNeeded() }
            }
            val summary = runCatching { AppGraph.system.grantAllPrivilegedPermissions(context) }
                .getOrNull()
            bulkBusy = false
            when {
                summary == null || summary.total == 0 ->
                    Toast.makeText(context, R.string.privileged_grant_failed, Toast.LENGTH_SHORT).show()
                summary.failed > 0 -> Toast.makeText(
                    context,
                    context.getString(R.string.privileged_grant_partial, summary.failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            refresh()
        }
    }

    val statuses = remember(tick) { AppGraph.system.privilegedPermissionStatus(context) }
    // 按功能域分组：组顺序取枚举声明顺序，组内取 status 顺序。
    val grouped = remember(statuses) {
        PrivilegedPermissionGroup.entries.mapNotNull { g ->
            statuses.filter { it.permission.group == g }.takeIf { it.isNotEmpty() }
        }
    }

    LunoScreen(
        title = stringResource(R.string.perm_page_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = { _ ->
            // 一键补齐：底部 FAB，参考主页添加 / 动作库保存按钮。
            LunoFab(
                onClick = ::grantAll,
                icon = Icons.Filled.VerifiedUser,
                contentDescription = stringResource(R.string.perm_grant_all),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        },
    ) {
        grouped.fastForEach { groupStatuses ->
            LunoGroup(title = groupStatuses.first().permission.group.title()) {
                groupStatuses.forEachIndexed { index, status ->
                    val permission = status.permission
                    val granted = status.granted
                    val stateDesc = stringResource(
                        if (granted) R.string.perm_state_granted else R.string.perm_state_missing,
                    )
                    LunoSettingsRow(
                        modifier = Modifier.semantics { stateDescription = stateDesc },
                        title = permission.title(),
                        subtitle = permission.hint(),
                        subtitleColor = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = segmentedShape(index, groupStatuses.size),
                        onClick = if (permission.hasSettingsPage) {
                            { openSettings(permission) }
                        } else {
                            null
                        },
                        trailing = if (!granted && permission.shizukuGrantable) {
                            {
                                TextButton(
                                    onClick = { grantOne(permission) },
                                    enabled = !bulkBusy && busy == null,
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (busy == permission) R.string.privileged_granting
                                            else R.string.perm_grant_one,
                                        ),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
