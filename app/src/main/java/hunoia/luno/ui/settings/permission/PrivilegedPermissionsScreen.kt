package hunoia.luno.ui.settings.permission

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.R
import hunoia.luno.facade.PrivilegedPermission
import hunoia.luno.facade.PrivilegedPermissionGroup
import hunoia.luno.quickaccess.QuickLaunchFacade
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SectionBadge
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.PressTextButton

@Composable
internal fun PrivilegedPermission.title(): String = stringResource(
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
        PrivilegedPermission.CAMERA -> R.string.perm_camera
        PrivilegedPermission.READ_CONTACTS -> R.string.perm_read_contacts
        PrivilegedPermission.READ_CALENDAR -> R.string.perm_read_calendar
        PrivilegedPermission.READ_SMS -> R.string.perm_read_sms
        PrivilegedPermission.READ_CALL_LOG -> R.string.perm_read_call_log
        PrivilegedPermission.READ_MEDIA_IMAGES -> R.string.perm_read_media_images
        PrivilegedPermission.READ_MEDIA_VIDEO -> R.string.perm_read_media_video
        PrivilegedPermission.READ_MEDIA_AUDIO -> R.string.perm_read_media_audio
        PrivilegedPermission.GET_INSTALLED_APPS -> R.string.perm_app_list
    },
)

@Composable
internal fun PrivilegedPermission.hint(): String = stringResource(
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
        PrivilegedPermission.CAMERA -> R.string.perm_camera_hint
        PrivilegedPermission.READ_CONTACTS -> R.string.perm_read_contacts_hint
        PrivilegedPermission.READ_CALENDAR -> R.string.perm_read_calendar_hint
        PrivilegedPermission.READ_SMS -> R.string.perm_read_sms_hint
        PrivilegedPermission.READ_CALL_LOG -> R.string.perm_read_call_log_hint
        PrivilegedPermission.READ_MEDIA_IMAGES -> R.string.perm_read_media_images_hint
        PrivilegedPermission.READ_MEDIA_VIDEO -> R.string.perm_read_media_video_hint
        PrivilegedPermission.READ_MEDIA_AUDIO -> R.string.perm_read_media_audio_hint
        PrivilegedPermission.GET_INSTALLED_APPS -> R.string.perm_app_list_hint
    },
)

@Composable
private fun PrivilegedPermissionGroup.title(): String = stringResource(
    when (this) {
        PrivilegedPermissionGroup.CORE -> R.string.perm_group_core
        PrivilegedPermissionGroup.NOTIFICATION -> R.string.perm_group_notification
        PrivilegedPermissionGroup.SYSTEM -> R.string.perm_group_system
        PrivilegedPermissionGroup.APP -> R.string.perm_group_app
    },
)

/**
 * 授权总览页：顶部一行「一键补齐」+ 按功能域分组的明细，每项一行。
 *
 * 与主页摘要卡的差异是**可解释性**：这里能看到每一项到底授没授、去哪儿改、失败了为什么。
 * 两个页面的动作形态一致（同一行组件 + 右侧文本按钮 + 徽标表达状态），主页不复制这里的明细。
 *
 * - 状态不用颜色编码：已授权行给 [SectionBadge]，未授权行给动作按钮；颜色只做辅助。
 * - 未授权行按「能不能自动授」分两种动作：「授权」（Shizuku）与「去设置」（Shizuku 授不了的项）。
 * - Shizuku 不可用时顶部按钮降级为「启动 Shizuku」/「授权 Shizuku」，**不是**点了没反应的授权。
 *
 * 例外：`ACCESSIBILITY` 与 `WRITE_SECURE_SETTINGS` 两行改跳**无障碍管理页**——
 * 前者的系统设置入口移到了该页状态卡，后者本来就没有系统页可跳。
 */
@Composable
fun PrivilegedPermissionsScreen(
    onBack: () -> Unit,
    onNavToAccessibilityManager: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val context = LocalContext.current
    // 复数文案要按数量取：用 LocalResources 而不是 LocalContext.resources（后者不随配置变化）。
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<PrivilegedPermission?>(null) }
    var bulkBusy by remember { mutableStateOf(false) }
    val shizukuStatus by ShizukuManager.statusFlow.collectAsStateWithLifecycle()
    val shizukuReady = shizukuStatus.isReady

    // 从系统设置页回来时重查（授权与撤销都要跟手），顺带刷新 Shizuku 连接态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ShizukuManager.updateStatus()
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
        facades.system.privilegedPermissionIntent(context, permission)
            ?.let { intent -> runCatching { context.startActivity(intent) } }
    }

    /** 让 Shizuku 可用；没装 Shizuku 时给一条可理解的提示，不留「点了没反应」。 */
    fun fixShizuku() {
        scope.launch {
            val acted = runCatching { ShizukuManager.requestOrLaunch() }.getOrDefault(false)
            if (!acted) showToast(R.string.perm_shizuku_unavailable)
            ShizukuManager.updateStatus()
            refresh()
        }
    }

    fun grantOne(permission: PrivilegedPermission) {
        if (busy != null || bulkBusy) return
        scope.launch {
            busy = permission
            val ok = runCatching { facades.system.grantPrivilegedPermission(context, permission) }
                .getOrDefault(false)
            busy = null
            // 成功不提示，只靠行内状态变化；失败说清是「缺 Shizuku」还是「只能手动开」。
            if (!ok) {
                showToast(
                    if (ShizukuManager.currentStatus().isReady) {
                        R.string.perm_grant_failed_manual
                    } else {
                        R.string.perm_grant_failed_shizuku
                    },
                )
            }
            refresh()
        }
    }

    fun grantAll() {
        if (bulkBusy || busy != null) return
        scope.launch {
            bulkBusy = true
            val summary = runCatching { facades.system.grantAllPrivilegedPermissions(context) }
                .getOrNull()
            bulkBusy = false
            when {
                summary == null || summary.total == 0 ->
                    showToast(R.string.privileged_grant_failed)
                summary.failed > 0 -> showToast(
                    resources.getQuantityString(
                        R.plurals.privileged_grant_partial,
                        summary.failed,
                        summary.failed,
                    ),
                )
            }
            refresh()
        }
    }

    // 厂商「获取应用列表」权限没有可直达的系统页：行内直接拉起系统对话框；被永久拒绝时退回应用详情。
    val installedAppsPermission = rememberGetInstalledAppsPermissionState { granted ->
        if (granted) QuickLaunchFacade.invalidateAppListCaches(context)
        refresh()
    }

    val statuses = remember(tick) { facades.system.privilegedPermissionStatus(context) }
    // 按功能域分组：组顺序取枚举声明顺序，组内取 status 顺序。
    val missingCount = remember(statuses) { statuses.count { !it.granted } }
    val grouped = remember(statuses) {
        PrivilegedPermissionGroup.entries.mapNotNull { g ->
            statuses.filter { it.permission.group == g }.takeIf { it.isNotEmpty() }
        }
    }

    LunoScreen(
        title = stringResource(R.string.perm_page_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
    ) {
        // 顶部一行摘要：与主页卡同一形态——标题只说状态（还有几项），按钮只说动作。
        LunoGroup {
            LunoSettingsRow(
                title = pluralStringResource(R.plurals.home_permission_pending, missingCount, missingCount),
                // 副标题只在 Shizuku 不可用时给原因；可用时标题已说尽，不再附说明。
                subtitle = when {
                    shizukuReady -> ""
                    shizukuStatus.binderAlive -> stringResource(R.string.perm_shizuku_not_authorized)
                    else -> stringResource(R.string.perm_shizuku_not_running)
                },
                icon = Icons.Filled.VerifiedUser,
                onClick = { if (missingCount > 0) { if (shizukuReady) grantAll() else fixShizuku() } },
                trailing = when {
                    missingCount == 0 -> {
                        { SectionBadge(text = stringResource(R.string.perm_state_granted)) }
                    }
                    else -> {
                        {
                            PressTextButton(
                                onClick = { if (shizukuReady) grantAll() else fixShizuku() },
                                enabled = !bulkBusy && busy == null,
                            ) {
                                Text(
                                    text = stringResource(
                                        when {
                                            bulkBusy -> R.string.privileged_granting
                                            !shizukuReady -> R.string.perm_fix_shizuku
                                            else -> R.string.perm_grant_one
                                        },
                                    ),
                                )
                            }
                        }
                    }
                },
            )
        }

        grouped.fastForEach { groupStatuses ->
            LunoGroup(title = groupStatuses.first().permission.group.title()) {
                groupStatuses.forEachIndexed { index, status ->
                    val permission = status.permission
                    val granted = status.granted
                    val stateDesc = stringResource(
                        if (granted) R.string.perm_state_granted else R.string.perm_state_missing,
                    )
                    // 获取应用列表没有直达系统页：应用内拉起系统对话框，永久拒绝后才退回应用详情。
                    val requestInstalledApps: () -> Unit = {
                        if (installedAppsPermission.deniedForever) openSettings(permission)
                        else installedAppsPermission.launchPermissionRequest()
                    }
                    LunoSettingsRow(
                        modifier = Modifier.semantics { stateDescription = stateDesc },
                        title = permission.title(),
                        subtitle = permission.hint(),
                        shape = segmentedShape(index, groupStatuses.size),
                        onClick = when {
                            permission == PrivilegedPermission.GET_INSTALLED_APPS ->
                                requestInstalledApps
                            permission == PrivilegedPermission.ACCESSIBILITY ||
                                permission == PrivilegedPermission.WRITE_SECURE_SETTINGS ->
                                onNavToAccessibilityManager
                            permission.hasSettingsPage -> { { openSettings(permission) } }
                            else -> null
                        },
                        trailing = when {
                            // 已授权：用徽标说明状态，不再靠副标题变色。
                            granted -> { { SectionBadge(text = stringResource(R.string.perm_state_granted)) } }
                            // 厂商权限：系统对话框才是正路，不给「去设置」这种多跳一次的死路。
                            permission == PrivilegedPermission.GET_INSTALLED_APPS -> {
                                {
                                    PressTextButton(onClick = requestInstalledApps) {
                                        Text(
                                            text = stringResource(
                                                if (installedAppsPermission.deniedForever) R.string.perm_go_settings
                                                else R.string.perm_grant_one,
                                            ),
                                        )
                                    }
                                }
                            }
                            // Shizuku 授不了（特殊 / 隐私敏感权限）：给看得见的「去设置」，不让人猜。
                            !permission.shizukuGrantable -> {
                                {
                                    PressTextButton(onClick = { openSettings(permission) }) {
                                        Text(text = stringResource(R.string.perm_go_settings))
                                    }
                                }
                            }
                            else -> {
                                {
                                    PressTextButton(
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
                            }
                        },
                    )
                }
            }
        }
    }
}
