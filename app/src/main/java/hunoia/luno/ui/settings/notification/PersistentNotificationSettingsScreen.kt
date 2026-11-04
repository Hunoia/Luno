package hunoia.luno.ui.settings.notification

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.R
import hunoia.luno.config.model.HiddenNotificationTarget
import hunoia.luno.config.model.isTargetHidden
import hunoia.luno.core.LunoLog
import hunoia.luno.core.domain.PersistentNotificationEntry
import hunoia.luno.domain.system.PersistentNotificationHidingUseCase
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.icon.rememberPackageIconBitmap
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 固定通知隐藏设置页。
 *
 * 列表由 Shizuku 读取当前通知栏快照；开关只写配置并即时 snooze / unsnooze。
 * 真正的持续隐藏由 `NotificationPulseListener` 完成，本页不持有监听器状态。
 */
@Composable
fun PersistentNotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(PersistentNotificationHidingUseCase.PageState()) }

    suspend fun reload() {
        state = withContext(Dispatchers.IO) {
            runCatching { PersistentNotificationHidingUseCase.load(context) }
                .getOrDefault(PersistentNotificationHidingUseCase.PageState())
        }
        loaded = true
    }

    /**
     * 开关先改本地状态再落盘 / 调 Shizuku：不等 shell 往返，避免开关卡住与列表重载跳顶。
     * 失败时重新读一次配置并以落盘结果为准。
     */
    fun update(
        transform: (PersistentNotificationHidingUseCase.PageState) -> PersistentNotificationHidingUseCase.PageState,
        block: suspend () -> Unit,
    ) {
        state = transform(state)
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                LunoLog.w("PersistentNotificationHiding", "toggle failed", e)
                reload()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentLoaded by rememberUpdatedState(loaded)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentLoaded) {
                scope.launch { reload() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LunoScreen(
        title = stringResource(R.string.persistent_notification_settings_title),
        onBack = onBack,
    ) {
        LunoGroup(title = stringResource(R.string.persistent_notification_status_group)) {
            LunoSettingsRow(
                title = stringResource(
                    if (state.notificationAccessGranted) {
                        R.string.persistent_notification_access_on
                    } else {
                        R.string.persistent_notification_access_off
                    },
                ),
                subtitle = stringResource(
                    if (state.notificationAccessGranted) {
                        R.string.persistent_notification_access_hint_on
                    } else {
                        R.string.persistent_notification_access_hint_off
                    },
                ),
                shape = segmentedShape(0, 2),
            )
            LunoSettingsRow(
                title = stringResource(
                    if (state.shizukuReady) {
                        R.string.persistent_notification_shizuku_on
                    } else {
                        R.string.persistent_notification_shizuku_off
                    },
                ),
                subtitle = stringResource(
                    if (state.shizukuReady) {
                        R.string.persistent_notification_shizuku_hint_on
                    } else {
                        R.string.persistent_notification_shizuku_hint_off
                    },
                ),
                shape = segmentedShape(1, 2),
            )
        }

        LunoGroup(title = stringResource(R.string.persistent_notification_master_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.persistent_notification_master),
                subtitle = stringResource(R.string.persistent_notification_master_hint),
                checked = state.settings.enabled,
                onCheckedChange = { enabled ->
                    update({ current ->
                        current.copy(settings = current.settings.copy(enabled = enabled))
                    }) {
                        PersistentNotificationHidingUseCase.setEnabled(context, enabled)
                    }
                },
                shape = segmentedShape(0, 1),
            )
        }

        LunoGroup(title = stringResource(R.string.persistent_notification_luno_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.persistent_notification_luno_hide),
                subtitle = stringResource(R.string.persistent_notification_luno_hide_hint),
                checked = state.settings.hideLunoNotifications,
                enabled = state.settings.enabled,
                onCheckedChange = { hidden ->
                    update({ current ->
                        current.copy(
                            settings = current.settings.copy(hideLunoNotifications = hidden),
                        )
                    }) {
                        PersistentNotificationHidingUseCase.setLunoHidden(context, hidden)
                    }
                },
                shape = segmentedShape(0, 1),
            )
        }

        if (loaded) {
            if (state.notifications.isEmpty()) {
                EmptyState(message = stringResource(R.string.persistent_notification_empty))
            } else {
                LunoGroup(title = stringResource(R.string.persistent_notification_others_group)) {
                    val iconPx = with(LocalDensity.current) { RowIconSize.toPx() }.toInt()
                    state.notifications.forEachIndexed { index, notification ->
                        val icon = rememberPackageIconBitmap(notification.packageName, iconPx)
                        val label = remember(notification.packageName) {
                            appLabel(context, notification.packageName)
                        }
                        val channelId = notification.channelId
                        val subtitle = if (channelId.isNullOrBlank()) {
                            stringResource(R.string.persistent_notification_target_subtitle, label)
                        } else {
                            stringResource(
                                R.string.persistent_notification_target_subtitle_channel,
                                label,
                                channelId,
                            )
                        }
                        LunoSwitchRow(
                            title = notification.title.ifBlank { label },
                            subtitle = subtitle,
                            iconBitmap = icon,
                            checked = state.settings.isTargetHidden(
                                packageName = notification.packageName,
                                notificationId = notification.notificationId,
                                tag = notification.tag,
                            ),
                            enabled = state.settings.enabled,
                            onCheckedChange = { hidden ->
                                val target = notification.toTarget()
                                update({ current ->
                                    val targets = if (hidden) {
                                        current.settings.hiddenTargets
                                            .filterNot { it.sameNotificationAs(target) } + target
                                    } else {
                                        current.settings.hiddenTargets
                                            .filterNot { it.sameNotificationAs(target) }
                                    }
                                    current.copy(
                                        settings = current.settings.copy(hiddenTargets = targets),
                                    )
                                }) {
                                    PersistentNotificationHidingUseCase.setTargetHidden(
                                        context = context,
                                        notification = notification,
                                        hidden = hidden,
                                    )
                                }
                            },
                            shape = segmentedShape(index, state.notifications.size),
                        )
                    }
                }
            }
        }
    }
}

private fun appLabel(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

private fun PersistentNotificationEntry.toTarget(): HiddenNotificationTarget =
    HiddenNotificationTarget(
        packageName = packageName,
        notificationId = notificationId,
        tag = tag,
        channelId = channelId,
    )

private fun HiddenNotificationTarget.sameNotificationAs(other: HiddenNotificationTarget): Boolean =
    packageName == other.packageName &&
        notificationId == other.notificationId &&
        tag == other.tag
