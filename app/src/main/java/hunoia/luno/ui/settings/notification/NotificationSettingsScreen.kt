package hunoia.luno.ui.settings.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
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
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.domain.habit.HabitReminderNotifier
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.shizuku.ShizukuInputInjection
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通用通知设置页（全局，应用所有提醒类通知的呈现设置；时间 / 开关等业务项归各功能页）。
 *
 * - 呈现模式：全局默认（标准 / 实时（Android 16+ Live Update）/ 超级岛（焦点通知 + XMSF 绕过，
 *   需 Shizuku））与按类型覆盖；不支持时自动回退实时 → 标准。
 * - 岛细节：自动展开大岛、岛超时。
 * - 「发送测试通知」走真实发布链路（含绕过事务）；非标准模式下发完切最近任务看岛效果。
 */
/**
 * 切到最近任务后再发通知的间隔：等窗口切走生效，岛才会浮出。
 * 首帧浮出判定发生在**投递瞬间**——应用在前台时 SystemUI 连胶囊都不给（2026-12 真机验证），
 * 所以测试通知必须先切走再发，否则永远看不到展开效果。
 */
private const val TEST_AWAY_DELAY_MS = 800L

/** 岛超时可选档位（秒）：不限 / 5 分钟 / 30 分钟 / 1 小时。 */
private val ISLAND_TIMEOUT_OPTIONS = listOf(0, 300, 1800, 3600)

/** 大岛展开停留可选档位（秒），与 [NotificationDisplaySettings.ISLAND_EXPAND_SECONDS_OPTIONS] 同源。 */
private val ISLAND_EXPAND_OPTIONS = NotificationDisplaySettings.ISLAND_EXPAND_SECONDS_OPTIONS

/** 断网时长可选档位（毫秒），与设置模型同源。 */
private val ISLAND_DISCONNECT_OPTIONS = NotificationDisplaySettings.ISLAND_DISCONNECT_MS_OPTIONS

/** 岛设置组内的行数（分段圆角按「组内行数」算，不是选项数）。 */
private const val ISLAND_ROWS = 7

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
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

    LunoScreen(
        title = stringResource(R.string.notification_display_settings_title),
        onBack = onBack,
    ) {
        LunoGroup(title = stringResource(R.string.notification_display_mode_group)) {
            ChoiceRow(
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
                shape = segmentedShape(0, 1),
            )
        }

        LunoGroup(title = stringResource(R.string.notification_display_override_group)) {
            NotificationKind.entries.forEachIndexed { index, kind ->
                val override = display.overrides[kind]
                ChoiceRow(
                    title = stringResource(
                        when (kind) {
                            NotificationKind.HABIT -> R.string.notification_display_kind_habit
                            NotificationKind.BACKUP -> R.string.notification_display_kind_backup
                            NotificationKind.TODO -> R.string.notification_display_kind_todo
                        },
                    ),
                    subtitle = stringResource(R.string.notification_display_override_hint),
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

        if (islandInUse) {
            LunoGroup(title = stringResource(R.string.notification_display_island_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.notification_display_island_auto_expand),
                    subtitle = stringResource(R.string.notification_display_island_auto_expand_hint),
                    shape = segmentedShape(0, ISLAND_ROWS),
                    checked = display.islandAutoExpand,
                    onCheckedChange = { checked ->
                        update { it.copy(islandAutoExpand = checked) }
                    },
                )
                ChoiceRow(
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
                    shape = segmentedShape(1, ISLAND_ROWS),
                )
                ChoiceRow(
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
                    shape = segmentedShape(2, ISLAND_ROWS),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.notification_display_island_outer_glow),
                    subtitle = stringResource(R.string.notification_display_island_outer_glow_hint),
                    shape = segmentedShape(3, ISLAND_ROWS),
                    checked = display.islandOuterGlow,
                    onCheckedChange = { checked ->
                        update { it.copy(islandOuterGlow = checked) }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.notification_display_island_keep_shade),
                    subtitle = stringResource(R.string.notification_display_island_keep_shade_hint),
                    shape = segmentedShape(4, ISLAND_ROWS),
                    checked = display.islandKeepShadeNotification,
                    onCheckedChange = { checked ->
                        update { it.copy(islandKeepShadeNotification = checked) }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.notification_display_island_drag_share),
                    subtitle = stringResource(R.string.notification_display_island_drag_share_hint),
                    shape = segmentedShape(5, ISLAND_ROWS),
                    checked = display.islandDragShare,
                    onCheckedChange = { checked ->
                        update { it.copy(islandDragShare = checked) }
                    },
                )
                ChoiceRow(
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
                    shape = segmentedShape(6, ISLAND_ROWS),
                )
            }
        }

        LunoGroup(title = stringResource(R.string.notification_display_test_group)) {
            LunoSettingsRow(
                title = stringResource(R.string.notification_display_test_send),
                subtitle = stringResource(R.string.notification_display_test_send_hint),
                shape = segmentedShape(0, 1),
                onClick = {
                    scope.launch {
                        // 非标准模式：先切走再投递，否则前台应用拿不到岛（生产路径都是后台发）。
                        if (testMode != NotificationMode.STANDARD) {
                            openRecents(context)
                            delay(TEST_AWAY_DELAY_MS)
                        }
                        HabitReminderNotifier.showTest(context, testMode)
                        showToast(R.string.notification_display_test_sent)
                    }
                },
            )
        }

        LunoGroup(title = stringResource(R.string.notification_display_perm_group)) {
            LunoSettingsRow(
                title = stringResource(R.string.habit_reminder_perm_notification),
                subtitle = stringResource(
                    if (notificationsEnabled) {
                        R.string.habit_reminder_perm_notification_on_hint
                    } else {
                        R.string.habit_reminder_perm_notification_off_hint
                    },
                ),
                shape = segmentedShape(0, 3),
                onClick = { openAppNotificationSettings(context) },
            )
            LunoSettingsRow(
                title = stringResource(R.string.habit_reminder_perm_exact_alarm),
                subtitle = stringResource(
                    if (exactAlarmEnabled) {
                        R.string.habit_reminder_perm_exact_on_hint
                    } else {
                        R.string.habit_reminder_perm_exact_off_hint
                    },
                ),
                shape = segmentedShape(1, 3),
                onClick = { openExactAlarmSettings(context) },
            )
            LunoSettingsRow(
                title = stringResource(R.string.habit_reminder_perm_live_update),
                subtitle = stringResource(
                    when {
                        !liveUpdateSupported -> R.string.habit_reminder_perm_live_unsupported
                        liveUpdateEnabled -> R.string.habit_reminder_perm_live_on_hint
                        else -> R.string.habit_reminder_perm_live_off_hint
                    },
                ),
                shape = segmentedShape(2, 3),
                enabled = liveUpdateSupported,
                onClick = if (liveUpdateSupported) {
                    { openAppNotificationSettings(context) }
                } else {
                    null
                },
            )
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
 * 发完测试通知后切到最近任务。
 *
 * 优选 Shizuku 注入 KEYCODE_APP_SWITCH（就是「最近任务」键，不受无障碍开关影响）；
 * 没装 / 没授权 Shizuku 时退到桌面——岛同样可见，只是不是任务列表。
 */
private suspend fun openRecents(context: Context) {
    if (ShizukuManager.currentStatus().isReady &&
        ShizukuInputInjection.keyEvent(KeyEvent.KEYCODE_APP_SWITCH)
    ) {
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
