package hunoia.luno.ui.settings.accessibility

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.a11y.A11yManagerFacade
import hunoia.luno.a11y.A11yServiceEntry
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.facade.PrivilegedPermission
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.NoticeBanner
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.icon.rememberPackageIconBitmap
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 无障碍管理页：列出设备上**全部**已安装无障碍服务并就地启停。
 *
 * 边界（架构 §1.3 / §10）：这里是配置写入，不构造 `ActionRequest`；启停只写
 * `Settings.Secure`（经 `:core:bridge`），**不碰 `DaemonService`** —— 保活回写仍是
 * 守护的单一实现，因此关掉本服务且保活生效时会出现「关了又开」，用行内警示说明。
 *
 * 降级：没有 `WRITE_SECURE_SETTINGS` 时列表照常只读展示，开关置灰，顶部提示条
 * 区分「Shizuku 未运行」与「已运行但未授权」两种原因，授权入口都在本页。
 */
@Composable
fun AccessibilityManagerScreen(
    onBack: () -> Unit,
) {
    val facades = LocalFacades.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var togglingId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<A11yServiceEntry?>(null) }
    var keepAliveOn by remember { mutableStateOf(false) }

    val shizukuStatus by ShizukuManager.statusFlow.collectAsStateWithLifecycle()

    // 从系统设置页回来时重查（启用与撤销都要跟手），顺带刷新 Shizuku 连接态。
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

    // 保活开关在 DataStore 里，只能异步读；其余状态（服务启用 / 写权限）同步可得，首帧即准。
    // isKeepAliveEnabled = 保活开关 && WRITE_SECURE_SETTINGS：两者齐备守护才写得回去。
    LaunchedEffect(tick) {
        keepAliveOn = runCatching {
            facades.system.loadHomePermissionState(context).isKeepAliveEnabled
        }.getOrDefault(false)
    }

    val hasWriteSecure = remember(tick) { context.hasWriteSecureSettingsPermission() }
    val serviceOn = remember(tick) {
        facades.system.privilegedPermissionStatus(context)
            .firstOrNull { it.permission == PrivilegedPermission.ACCESSIBILITY }
            ?.granted == true
    }
    val services = remember(tick) { A11yManagerFacade.loadServices(context) }
    val iconPx = with(LocalDensity.current) { RowIconSize.toPx() }.toInt()

    fun refresh() {
        tick++
    }

    fun toggle(entry: A11yServiceEntry, target: Boolean) {
        if (togglingId != null) return
        scope.launch {
            togglingId = entry.id
            val ok = withContext(Dispatchers.IO) {
                runCatching { A11yManagerFacade.setEnabled(context, entry.id, target) }
                    .getOrDefault(false)
            }
            togglingId = null
            if (!ok) {
                Toast.makeText(context, R.string.a11y_toggle_failed, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    LunoScreen(
        title = stringResource(R.string.a11y_manager_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        sheets = {
            detail?.let { entry ->
                ServiceDetailSheet(entry = entry, onDismissRequest = { detail = null })
            }
        },
    ) {
        when {
            !shizukuStatus.isReady -> NoticeBanner(
                message = stringResource(R.string.a11y_banner_shizuku),
                actionLabel = stringResource(R.string.a11y_banner_shizuku_action),
                onAction = {
                    scope.launch {
                        runCatching { ShizukuManager.autoRequestPermissionIfNeeded() }
                        ShizukuManager.updateStatus()
                        refresh()
                    }
                },
            )
            !hasWriteSecure -> NoticeBanner(
                message = stringResource(R.string.a11y_banner_write_secure),
                actionLabel = stringResource(R.string.a11y_banner_write_secure_action),
                onAction = {
                    scope.launch {
                        runCatching { ShizukuManager.ensureWriteSecureSettings() }
                        refresh()
                    }
                },
            )
        }

        LunoGroup(title = stringResource(R.string.a11y_status_group)) {
            // 标题本身带状态文案（TalkBack 直接读得到），不再叠加 stateDescription。
            LunoSettingsRow(
                title = stringResource(
                    if (serviceOn) R.string.a11y_status_service_on else R.string.a11y_status_service_off,
                ),
                subtitle = stringResource(R.string.a11y_status_service_hint),
                subtitleColor = if (serviceOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = segmentedShape(0, 3),
                onClick = {
                    facades.system
                        .privilegedPermissionIntent(context, PrivilegedPermission.ACCESSIBILITY)
                        ?.let { intent -> runCatching { context.startActivity(intent) } }
                },
            )
            LunoSettingsRow(
                title = stringResource(
                    if (hasWriteSecure) R.string.a11y_status_write_secure_on
                    else R.string.a11y_status_write_secure_off,
                ),
                subtitle = stringResource(R.string.a11y_status_write_secure_hint),
                subtitleColor = if (hasWriteSecure) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = segmentedShape(1, 3),
            )
            LunoSettingsRow(
                title = stringResource(
                    if (keepAliveOn) R.string.a11y_status_keep_alive_on
                    else R.string.a11y_status_keep_alive_off,
                ),
                subtitle = stringResource(R.string.a11y_status_keep_alive_hint),
                subtitleColor = if (keepAliveOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = segmentedShape(2, 3),
            )
        }

        if (services.isEmpty()) {
            EmptyState(message = stringResource(R.string.a11y_services_empty))
        } else {
            LunoGroup(title = stringResource(R.string.a11y_services_group, services.size)) {
                services.forEachIndexed { index, entry ->
                    val icon = rememberPackageIconBitmap(entry.packageName, iconPx)
                    val stateDesc = stringResource(
                        if (entry.enabled) R.string.a11y_state_on else R.string.a11y_state_off,
                    )
                    // 本服务行优先给「关了会被保活写回」的警示，其次才是服务描述。
                    val subtitle = when {
                        entry.isOwnService && keepAliveOn ->
                            stringResource(R.string.a11y_own_service_warning)
                        entry.description.isNotBlank() -> entry.description
                        else -> stringResource(R.string.a11y_detail_no_description)
                    }
                    LunoSwitchRow(
                        modifier = Modifier.semantics { stateDescription = stateDesc },
                        title = entry.label,
                        subtitle = subtitle,
                        iconBitmap = icon,
                        checked = entry.enabled,
                        // 没有 WRITE_SECURE_SETTINGS 时开关只能看不能动（页面顶部有授权入口）。
                        enabled = hasWriteSecure && togglingId == null,
                        onClick = { detail = entry },
                        onCheckedChange = { target -> toggle(entry, target) },
                        shape = segmentedShape(index, services.size),
                    )
                }
            }
        }
    }
}

/** 服务详情：描述 + 包名 + 组件名（组件名即启停用的 token）。 */
@Composable
private fun ServiceDetailSheet(
    entry: A11yServiceEntry,
    onDismissRequest: () -> Unit,
) {
    LunoSheet(onDismissRequest = onDismissRequest, showDragHandle = true) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(
                    start = PageGutter,
                    end = PageGutter,
                    bottom = ListSpacing,
                ),
            ) {
                Text(text = entry.label, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.size(CardInnerSpacing))
                Text(
                    text = entry.description.ifBlank {
                        stringResource(R.string.a11y_detail_no_description)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LunoGroup {
                LunoSettingsRow(
                    title = stringResource(R.string.a11y_detail_package),
                    subtitle = entry.packageName,
                    shape = segmentedShape(0, 2),
                )
                LunoSettingsRow(
                    title = stringResource(R.string.a11y_detail_component),
                    subtitle = entry.id,
                    shape = segmentedShape(1, 2),
                )
            }
            Spacer(modifier = Modifier.size(ListSpacing))
        }
    }
}
