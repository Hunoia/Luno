package hunoia.luno.ui.settings.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.ToastPresentation
import hunoia.luno.gridpanel.reminder.habit.HabitReminderNotifier
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.group
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通用通知设置页（全局，应用所有提醒类通知的呈现设置；时间 / 开关等业务项归各功能页）。
 *
 * - 呈现模式：全局默认（标准 / 实时（Android 16+ Live Update）/ 超级岛（焦点通知 + XMSF 绕过，
 *   需 Shizuku））与按类型覆盖；不支持时自动回退实时 → 标准。
 * - 岛细节：自动展开大岛、岛超时。
 * - 「发送测试通知」走真实发布链路（含绕过事务）；非标准模式下发完切桌面看岛效果。
 */
/**
 * 切到桌面后再发通知的间隔：等桌面切换动画完成，岛才会浮出。
 * 首帧浮出判定发生在**投递瞬间**——应用在前台时 SystemUI 连胶囊都不给（2026-12 真机验证），
 * 且投递时桌面切换必须已结束；1s 足够 HOME 动画（约 300-500ms）完成。
 */
private const val TEST_AWAY_DELAY_MS = 1000L

/** 岛超时可选档位（秒）：不限 / 5 分钟 / 30 分钟 / 1 小时。 */
private val ISLAND_TIMEOUT_OPTIONS = listOf(0, 300, 1800, 3600)

/** 大岛展开停留可选档位（秒），与 [NotificationDisplaySettings.ISLAND_EXPAND_SECONDS_OPTIONS] 同源。 */
private val ISLAND_EXPAND_OPTIONS = NotificationDisplaySettings.ISLAND_EXPAND_SECONDS_OPTIONS

/** 断网时长可选档位（毫秒），与设置模型同源。 */
private val ISLAND_DISCONNECT_OPTIONS = NotificationDisplaySettings.ISLAND_DISCONNECT_MS_OPTIONS

/** 岛设置组内的行数（分段圆角按「组内行数」算，不是选项数）。 */
private const val ISLAND_ROWS = 7

/** 进度类通知点击后的自动移除延时（秒）；0 = 不移除。 */
private val PROGRESS_DISMISS_OPTIONS = NotificationDisplaySettings.PROGRESS_DISMISS_SECONDS_OPTIONS

/** 测试分组行数（普通 / 进度）。 */
private const val TEST_ROWS = 3

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = TuneConfig.latestAdvanced())
    val display = advanced.notificationDisplay
    var resumeTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsEnabled = remember(resumeTick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactAlarmEnabled = remember(resumeTick) { canScheduleExactAlarms(context) }
    val liveUpdateSupported = Build.VERSION.SDK_INT >= 36
    val liveUpdateEnabled = remember(resumeTick) {
        liveUpdateSupported && canPostPromotedNotifications(context)
    }

    fun update(transform: (NotificationDisplaySettings) -> NotificationDisplaySettings) {
        // 基于当前配置变换，不闭包捕获组合期快照：连续点击不会用旧值覆盖上一步。
        scope.launch {
            TuneConfig.updateAdvanced { current ->
                current.copy(notificationDisplay = transform(current.notificationDisplay))
            }
        }
    }

    // 测试通知复用习惯提醒链路（与待办提醒同样带按钮）；非标准模式发完切最近任务看岛。
    val testMode = display.modeFor(NotificationKind.HABIT)
    val islandInUse = display.mode == NotificationMode.ISLAND ||
        display.overrides.values.any { it == NotificationMode.ISLAND }

    // 测试通知发前先切走：应用在前台时 SystemUI 不建岛，首帧浮出判定在投递瞬间。
    // goHome 必须在主线程 + activity context（后台线程发 HOME 会被系统拒；applicationContext
    // 在「隐藏 Luno 后台」下是受限后台启动，切不出去）——用户点击回调即主线程，直接先切走。
    // delay + showTest 挂进程级 scope：回桌面后 task/Activity 销毁不取消（前台服务保活），
    // 岛照常浮出；且发布用 applicationContext，不依赖 Activity 存活。
    fun sendTestNotification(progress: Boolean) {
        val app = context.applicationContext
        val away = testMode != NotificationMode.STANDARD
        if (away) goHome(context)
        AppContext.applicationScope?.launch {
            if (away) delay(TEST_AWAY_DELAY_MS)
            HabitReminderNotifier.showTest(app, testMode, progress = progress)
        }
    }

    // 测试 Toast 提示：只走全局 showToast（读 toastPresentation 呈现），与通知测试分开，
    // 单测系统 Toast / 标准 / 实时 / 岛 / 胶囊。岛 / 胶囊同样要先切走（前台不建岛）。
    fun sendTestToast() {
        val app = context.applicationContext
        val toastMode = display.toastPresentation
        val away = toastMode == ToastPresentation.ISLAND || toastMode == ToastPresentation.CAPSULE
        if (away) goHome(context)
        AppContext.applicationScope?.launch {
            if (away) delay(TEST_AWAY_DELAY_MS)
            showToast(R.string.notification_display_test_toast_sent)
        }
    }

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.notification_display_settings_title),
onBack = onBack) {
hunoia.luno.ui.component.page.LunoPageColumn() {
    block {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(hunoia.luno.ui.theme.ListSpacing),
        ) {

        group(title = stringResource(R.string.notification_display_mode_group)) {
dropdownRow(
title = stringResource(R.string.notification_display_mode_row),
subtitle = stringResource(R.string.notification_display_mode_hint),
options = listOf(
                    stringResource(R.string.notification_display_mode_standard),
                    stringResource(R.string.notification_display_mode_live),
                    stringResource(R.string.notification_display_mode_island),
                ),
selectedIndex = display.mode.ordinal,
onSelect = { index ->
                    val mode = NotificationMode.entries[index]
                    update { it.copy(mode = mode) }
                },

)        }

        LunoGroup(
            title = stringResource(R.string.notification_display_override_group),
            subtitle = stringResource(R.string.notification_display_override_hint),
        ) {
            NotificationKind.entries.forEachIndexed { index, kind ->
                val override = display.overrides[kind]
                LunoDropdownRow(
                    title = stringResource(
                        when (kind) {
                            NotificationKind.HABIT -> R.string.notification_display_kind_habit
                            NotificationKind.BACKUP -> R.string.notification_display_kind_backup
                            NotificationKind.TODO -> R.string.notification_display_kind_todo
                            NotificationKind.RECOGNITION -> R.string.notification_display_kind_recognition
                        },
                    ),
                    options = listOf(
                        stringResource(R.string.notification_display_override_follow),
                        stringResource(R.string.notification_display_mode_standard),
                        stringResource(R.string.notification_display_mode_live),
                        stringResource(R.string.notification_display_mode_island),
                    ),
                    selectedIndex = override?.let { it.ordinal + 1 } ?: 0,
                    onSelect = { selected ->
                        update { current ->
                            current.copy(
                                overrides = if (selected == 0) {
                                    current.overrides - kind
                                } else {
                                    current.overrides + (kind to NotificationMode.entries[selected - 1])
                                },
                            )
                        }
                    },
                    shape = segmentedShape(index, NotificationKind.entries.size),
                )
            }
        }

        group(
            title = stringResource(R.string.toast_presentation_group),
            subtitle = stringResource(R.string.toast_presentation_hint),
        ) {
dropdownRow(
title = stringResource(R.string.toast_presentation_row),
options = listOf(
                    stringResource(R.string.toast_presentation_toast),
                    stringResource(R.string.toast_presentation_standard),
                    stringResource(R.string.toast_presentation_live),
                    stringResource(R.string.toast_presentation_island),
                    stringResource(R.string.toast_presentation_capsule),
                ),
selectedIndex = display.toastPresentation.ordinal,
onSelect = { index ->
                    update { it.copy(toastPresentation = ToastPresentation.entries[index]) }
                },

)        }

        if (islandInUse) {
            group(title = stringResource(R.string.notification_display_island_group)) {
switchRow(
title = stringResource(R.string.notification_display_island_auto_expand),
subtitle = stringResource(R.string.notification_display_island_auto_expand_hint),
checked = display.islandAutoExpand,
onCheckedChange = { checked ->
                        update { it.copy(islandAutoExpand = checked) }
                    },

)
dropdownRow(
title = stringResource(R.string.notification_display_island_expand_seconds),
subtitle = stringResource(R.string.notification_display_island_expand_seconds_hint),
options = listOf(
                        stringResource(R.string.notification_display_island_expand_3s),
                        stringResource(R.string.notification_display_island_expand_5s),
                        stringResource(R.string.notification_display_island_expand_10s),
                    ),
selectedIndex = ISLAND_EXPAND_OPTIONS
                        .indexOf(display.islandExpandSeconds).coerceAtLeast(0),
onSelect = { index ->
                        update { it.copy(islandExpandSeconds = ISLAND_EXPAND_OPTIONS[index]) }
                    },

)
dropdownRow(
title = stringResource(R.string.notification_display_island_disconnect),
subtitle = stringResource(R.string.notification_display_island_disconnect_hint),
options = ISLAND_DISCONNECT_OPTIONS.map {
                        stringResource(R.string.notification_display_island_disconnect_value, it)
                    },
selectedIndex = ISLAND_DISCONNECT_OPTIONS
                        .indexOf(display.islandDisconnectMs).coerceAtLeast(0),
onSelect = { index ->
                        update { it.copy(islandDisconnectMs = ISLAND_DISCONNECT_OPTIONS[index]) }
                    },

)
switchRow(
title = stringResource(R.string.notification_display_island_outer_glow),
subtitle = stringResource(R.string.notification_display_island_outer_glow_hint),
checked = display.islandOuterGlow,
onCheckedChange = { checked ->
                        update { it.copy(islandOuterGlow = checked) }
                    },

)
switchRow(
title = stringResource(R.string.notification_display_island_keep_shade),
subtitle = stringResource(R.string.notification_display_island_keep_shade_hint),
checked = display.islandKeepShadeNotification,
onCheckedChange = { checked ->
                        update { it.copy(islandKeepShadeNotification = checked) }
                    },

)
switchRow(
title = stringResource(R.string.notification_display_island_drag_share),
subtitle = stringResource(R.string.notification_display_island_drag_share_hint),
checked = display.islandDragShare,
onCheckedChange = { checked ->
                        update { it.copy(islandDragShare = checked) }
                    },

)
dropdownRow(
title = stringResource(R.string.notification_display_island_timeout),
subtitle = stringResource(R.string.notification_display_island_timeout_hint),
options = listOf(
                        stringResource(R.string.notification_display_island_timeout_none),
                        stringResource(R.string.notification_display_island_timeout_5m),
                        stringResource(R.string.notification_display_island_timeout_30m),
                        stringResource(R.string.notification_display_island_timeout_1h),
                    ),
selectedIndex = ISLAND_TIMEOUT_OPTIONS.indexOf(display.islandTimeoutSeconds)
                        .coerceAtLeast(0),
onSelect = { index ->
                        update { it.copy(islandTimeoutSeconds = ISLAND_TIMEOUT_OPTIONS[index]) }
                    },

)            }
        }

        // 进度类通知的收尾行为：与呈现模式无关（标准 / 实时 / 岛都适用），所以不放在岛分组里。
        group(title = stringResource(R.string.notification_display_progress_group)) {
dropdownRow(
title = stringResource(R.string.notification_display_progress_dismiss),
subtitle = stringResource(R.string.notification_display_progress_dismiss_hint),
options = PROGRESS_DISMISS_OPTIONS.map {
                    if (it == 0) {
                        stringResource(R.string.notification_display_progress_dismiss_none)
                    } else {
                        stringResource(R.string.notification_display_progress_dismiss_value, it)
                    }
                },
selectedIndex = PROGRESS_DISMISS_OPTIONS
                    .indexOf(display.progressDismissSeconds).coerceAtLeast(0),
onSelect = { index ->
                    update { it.copy(progressDismissSeconds = PROGRESS_DISMISS_OPTIONS[index]) }
                },

)        }

        group(title = stringResource(R.string.notification_display_test_group)) {
row(
title = stringResource(R.string.notification_display_test_send),
subtitle = stringResource(R.string.notification_display_test_send_hint),
onClick = {
                    scope.launch {

                        sendTestNotification(progress = false)
                    }
                },

)
row(
title = stringResource(R.string.notification_display_test_progress),
subtitle = stringResource(R.string.notification_display_test_progress_hint),
onClick = {
                    scope.launch {
                        sendTestNotification(progress = true)
                    }
                },

)
row(
title = stringResource(R.string.notification_display_test_toast),
subtitle = stringResource(R.string.notification_display_test_toast_hint),
onClick = {
                    scope.launch { sendTestToast() }
                },

)        }

        // 权限提示：只在对应权限缺失时出现（与悬浮球设置页同模式）；已授权/不适用（旧系统不支持）不显示。
        val needNotifPerm = !notificationsEnabled
        val needExactAlarm = !exactAlarmEnabled
        val needLiveUpdate = liveUpdateSupported && !liveUpdateEnabled
        if (needNotifPerm || needExactAlarm || needLiveUpdate) {
            val rowCount = (if (needNotifPerm) 1 else 0) +
                (if (needExactAlarm) 1 else 0) +
                (if (needLiveUpdate) 1 else 0)
            LunoGroup(title = stringResource(R.string.notification_display_perm_group)) {
                if (needNotifPerm) {
                    LunoSettingsRow(
                        title = stringResource(R.string.habit_reminder_perm_notification),
                        subtitle = stringResource(R.string.habit_reminder_perm_notification_off_hint),
                        shape = segmentedShape(0, rowCount),
                        onClick = { openAppNotificationSettings(context) },
                    )
                }
                if (needExactAlarm) {
                    LunoSettingsRow(
                        title = stringResource(R.string.habit_reminder_perm_exact_alarm),
                        subtitle = stringResource(R.string.habit_reminder_perm_exact_off_hint),
                        shape = segmentedShape(if (needNotifPerm) 1 else 0, rowCount),
                        onClick = { openExactAlarmSettings(context) },
                    )
                }
                if (needLiveUpdate) {
                    LunoSettingsRow(
                        title = stringResource(R.string.habit_reminder_perm_live_update),
                        subtitle = stringResource(R.string.habit_reminder_perm_live_off_hint),
                        shape = segmentedShape(
                            (if (needNotifPerm) 1 else 0) + (if (needExactAlarm) 1 else 0),
                            rowCount,
                        ),
                        onClick = { openAppNotificationSettings(context) },
                    )
                }
            }
        }

        }
    }
}
}
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
}

private fun canPostPromotedNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
    return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetails(context) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetails(context) }
}

private fun openAppDetails(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * 发完测试通知后回桌面。
 *
 * 直接发 HOME intent 切走前台；应用在前台时 SystemUI 不建岛，回桌面后岛才浮出。
 * 必须在主线程 + activity context 调用（后台线程发 startActivity 会被系统拒）。
 */
private fun goHome(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
