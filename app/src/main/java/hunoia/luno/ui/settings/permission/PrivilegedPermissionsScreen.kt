package hunoia.luno.ui.settings.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.R
import hunoia.luno.domain.AppGraph
import hunoia.luno.domain.system.PrivilegedPermission
import hunoia.luno.domain.system.PrivilegedPermissionStatus
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
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
    var shizukuReady by remember { mutableStateOf(ShizukuManager.currentStatus().isReady) }
    var busy by remember { mutableStateOf<PrivilegedPermission?>(null) }
    var bulkBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 从系统设置页回来时重查（授权与撤销都要跟手）。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tick++
                shizukuReady = ShizukuManager.currentStatus().isReady
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refresh() {
        tick++
        shizukuReady = ShizukuManager.currentStatus().isReady
    }

    fun grantOne(permission: PrivilegedPermission) {
        if (busy != null || bulkBusy) return
        scope.launch {
            busy = permission
            message = null
            if (!ShizukuManager.currentStatus().isReady) {
                runCatching { ShizukuManager.autoRequestPermissionIfNeeded() }
            }
            val ok = runCatching { AppGraph.system.grantPrivilegedPermission(context, permission) }
                .getOrDefault(false)
            busy = null
            message = context.getString(
                if (ok) R.string.perm_grant_one_done else R.string.perm_grant_one_failed,
            )
            refresh()
        }
    }

    fun grantAll() {
        if (bulkBusy || busy != null) return
        scope.launch {
            bulkBusy = true
            message = null
            if (!ShizukuManager.currentStatus().isReady) {
                runCatching { ShizukuManager.autoRequestPermissionIfNeeded() }
            }
            val summary = runCatching { AppGraph.system.grantAllPrivilegedPermissions(context) }
                .getOrNull()
            bulkBusy = false
            message = when {
                summary == null || summary.total == 0 ->
                    context.getString(R.string.privileged_grant_failed)
                summary.failed == 0 ->
                    context.getString(R.string.privileged_grant_done, summary.total)
                else -> context.getString(R.string.privileged_grant_partial, summary.failed)
            }
            refresh()
        }
    }

    val statuses = remember(tick) { AppGraph.system.privilegedPermissionStatus(context) }

    LunoScreen(title = stringResource(R.string.perm_page_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = ListSpacing),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            Button(
                onClick = ::grantAll,
                enabled = !bulkBusy && busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (bulkBusy) R.string.privileged_granting else R.string.perm_grant_all,
                    ),
                )
            }
            Text(
                text = message ?: stringResource(
                    if (shizukuReady) {
                        R.string.perm_page_hint
                    } else {
                        R.string.privileged_grant_shizuku_missing
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LunoGroup(title = stringResource(R.string.perm_group_title)) {
            statuses.fastForEach { status: PrivilegedPermissionStatus ->
                val permission = status.permission
                val granted = status.granted
                LunoSettingsRow(
                    title = permission.title(),
                    subtitle = buildString {
                        append(
                            stringResource(
                                if (granted) R.string.perm_state_granted else R.string.perm_state_missing,
                            ),
                        )
                        append(" · ")
                        append(permission.hint())
                    },
                    subtitleColor = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = segmentedShape(permission.ordinal, statuses.size),
                    enabled = permission.hasSettingsPage,
                    onClick = {
                        AppGraph.system.privilegedPermissionIntent(context, permission)
                            ?.let { intent -> runCatching { context.startActivity(intent) } }
                    },
                    trailing = {
                        if (!granted) {
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
                    },
                )
            }
        }
    }
}
