package hunoia.luno.ui.settings.habit

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
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
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.HabitNotificationStyle
import hunoia.luno.domain.habit.HabitReminderNotifier
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.shizuku.ShizukuInputInjection
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 习惯提醒设置页（全局）。
 *
 * - 通知样式：标准 / 实时活动（HyperOS 上即系统渲染的超级岛）；
 *   单个习惯只决定「开 / 关 + 时间」，样式全局统一。
 * - 权限状态：通知权限、精确闹钟、Android 16+ 实时活动（Live Update）权限，点按跳系统设置。
 * - 「发送测试通知」在实时活动样式下会自动切到最近任务：设置页在前台时系统会隐藏本应用的岛。
 *
 * 样式选择写 [AdvancedSettings.habitReminderStyle]；下一次提醒用新样式展示，
 * 不支持时通知器自动回退标准通知。
 */
/** 发完测试通知到切走的间隔：等系统把通知落到 SystemUI 并建好岛，太短会错过岛的出现。 */
private const val TEST_RECENTS_DELAY_MS = 600L

@Composable
fun HabitReminderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    var resumeTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 权限状态都会在系统页里被改，回前台按 resumeTick 重读。
    val notificationsEnabled = remember(resumeTick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactAlarmEnabled = remember(resumeTick) { canScheduleExactAlarms(context) }
    val liveUpdateSupported = Build.VERSION.SDK_INT >= 36
    val liveUpdateEnabled = remember(resumeTick) {
        liveUpdateSupported && canPostPromotedNotifications(context)
    }

    fun selectStyle(style: HabitNotificationStyle) {
        scope.launch {
            TuneConfig.updateAdvanced { it.copy(habitReminderStyle = style) }
        }
    }

    LunoScreen(
        title = stringResource(R.string.habit_reminder_settings_title),
        onBack = onBack,
    ) {
        LunoGroup(title = stringResource(R.string.habit_reminder_style)) {
            ChoiceRow(
                title = stringResource(R.string.habit_reminder_style_row),
                subtitle = stringResource(R.string.habit_reminder_style_hint),
                options = listOf(
                    stringResource(R.string.habit_reminder_style_standard),
                    stringResource(R.string.habit_reminder_style_live_activity),
                ),
                selectedIndex = advanced.habitReminderStyle.ordinal,
                onSelect = { index -> selectStyle(HabitNotificationStyle.entries[index]) },
                shape = segmentedShape(0, 1),
            )
        }

        LunoGroup(title = stringResource(R.string.habit_reminder_test_group)) {
            LunoSettingsRow(
                title = stringResource(R.string.habit_reminder_test_send),
                subtitle = stringResource(R.string.habit_reminder_test_send_hint),
                shape = segmentedShape(0, 1),
                onClick = {
                    HabitReminderNotifier.showTest(context, advanced.habitReminderStyle)
                    Toast.makeText(context, R.string.habit_reminder_test_sent, Toast.LENGTH_SHORT).show()
                    // 实时活动/岛的摘要态由 SystemUI 在「本应用不在前台」时才展示，
                    // 设置页自身在前台时岛会被系统隐藏；发完立刻切到最近任务，才能看到真实效果。
                    if (advanced.habitReminderStyle == HabitNotificationStyle.LIVE_ACTIVITY) {
                        scope.launch {
                            delay(TEST_RECENTS_DELAY_MS)
                            openRecents(context)
                        }
                    }
                },
            )
        }

        LunoGroup(title = stringResource(R.string.habit_reminder_perm_group)) {
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
