package hunoia.luno.gridpanel.habit

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDefaultSnoozeMinutes
import hunoia.luno.config.model.HabitMaxDailyTarget
import hunoia.luno.config.model.HabitMaxReminders
import hunoia.luno.config.model.HabitMaxSnoozeMinutes
import hunoia.luno.config.model.HabitMinSnoozeMinutes
import hunoia.luno.config.model.HabitReminderTime
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.config.model.habitDayState
import hunoia.luno.config.model.normalizeReminderTimes
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.internal.rememberTodayEpochDay
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.gridpanel.ui.SnoozeDurationDialog
import hunoia.luno.gridpanel.ui.snoozeDurationLabel
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.HabitCalendarCellGap
import hunoia.luno.ui.theme.HabitCalendarHeaderGap
import hunoia.luno.ui.theme.HabitCalendarPaddingVertical
import hunoia.luno.ui.theme.HabitDayCellMinHeight
import hunoia.luno.ui.theme.HabitDayCellPaddingVertical
import hunoia.luno.ui.theme.HabitDayCheckIconSize
import hunoia.luno.ui.theme.HabitDayDotSize
import hunoia.luno.ui.theme.HabitDayDotStroke
import hunoia.luno.ui.theme.HabitDayTextAlpha
import hunoia.luno.ui.theme.HabitDayTextDotGap
import hunoia.luno.ui.theme.HabitWeekdayAlpha
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SmallShape
import hunoia.luno.ui.theme.toColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.pressClickable

/**
 * 习惯编辑：新增（habitId 空）或编辑。字段 = 名字 / 颜色 / 每 x 日 / 每日次数 /
 * 多个提醒时间 / 推迟时长 / 近 30 天补改。
 *
 * **即时保存**：没有保存按钮。字段改动防抖 400ms 写盘，返回 / 系统返回 / onStop
 * 都会先 flush；新建习惯在名字首次非空时立即创建（空名退出不落库）。删除前先停
 * 自动保存，避免删除后又被 onStop 写回来。
 *
 * 一屏一个状态类型 [HabitEditState]（architecture §8.4）：字段草稿优先，缺省回退到
 * 已加载的 [Habit]——配置流首帧为空（`initialValue`），否则会被空值初始化导致重进丢内容。
 * 增删改与打卡写入走 [HabitCenter] 门面（配置写入，不走 Action / Dispatcher）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    habitId: String?,
    onBack: () -> Unit,
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitConfig())
    val today = rememberTodayEpochDay()
    val scope = rememberCoroutineScope()
    val writeMutex = remember { Mutex() }
    val flushHolder = remember { FlushJobHolder() }
    val state = rememberSaveable(habitId, saver = HabitEditStateSaver) { HabitEditState() }
    val isNew = habitId == null
    val persistedId = state.createdId ?: habitId
    val habit = persistedId?.let { config.habitById(it) }

    val shownName = state.name ?: habit?.name.orEmpty()
    val shownColorSpec = ColorSpec(
        source = state.colorSourceName?.let { ColorSource.valueOf(it) }
            ?: habit?.colorSource ?: ColorSource.Custom,
        themeKey = state.colorThemeKeyName?.let { ThemeColorKey.valueOf(it) }
            ?: habit?.colorThemeKey ?: ThemeColorKey.Primary,
        customColor = state.colorArgb ?: habit?.color ?: Habit().color,
    )
    // 日历点阵用「当前编辑中的值」即时预览，写盘前所见即所得。
    val shownInterval = state.interval ?: habit?.interval ?: 1
    val shownTarget = state.dailyTarget ?: habit?.dailyTarget ?: 1
    val shownReminderEnabled = state.reminderEnabled ?: habit?.reminderEnabled ?: false
    val shownReminders = normalizeReminderTimes(
        state.reminders ?: habit?.reminders ?: emptyList(),
    )
    val shownSnoozeMinutes = state.snoozeMinutes ?: habit?.snoozeMinutes ?: HabitDefaultSnoozeMinutes

    fun draftHabit(base: Habit?, newId: String? = null): Habit {
        val seed = base ?: Habit(
            id = newId ?: UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
        )
        val colorSpec = ColorSpec(
            source = state.colorSourceName?.let { ColorSource.valueOf(it) }
                ?: seed.colorSource,
            themeKey = state.colorThemeKeyName?.let { ThemeColorKey.valueOf(it) }
                ?: seed.colorThemeKey,
            customColor = state.colorArgb ?: seed.color,
        )
        val typedName = state.name ?: seed.name
        // 已创建后清空名字保留最后有效名：typedName 为空时回退到 seed.name。
        val name = typedName.trim().ifBlank { seed.name }
        return seed.copy(
            name = name,
            color = colorSpec.customColor,
            colorSource = colorSpec.source,
            colorThemeKey = colorSpec.themeKey,
            interval = (state.interval ?: seed.interval).coerceIn(1, 30),
            dailyTarget = (state.dailyTarget ?: seed.dailyTarget)
                .coerceIn(1, HabitMaxDailyTarget),
            reminderEnabled = state.reminderEnabled ?: seed.reminderEnabled,
            reminders = normalizeReminderTimes(state.reminders ?: seed.reminders),
            snoozeMinutes = (state.snoozeMinutes ?: seed.snoozeMinutes)
                .coerceIn(HabitMinSnoozeMinutes, HabitMaxSnoozeMinutes),
        )
    }

    suspend fun flush() {
        if (state.deleted) return
        writeMutex.withLock {
            if (state.deleted) return
            val latest = runCatching { HabitCenter.getHabitConfig() }.getOrNull() ?: return
            val targetId = state.createdId ?: habitId
            val base = targetId?.let { latest.habitById(it) }
            // 正在编辑的既有习惯被外部删除了：不要把它重新创建出来。
            if (habitId != null && state.createdId == null && base == null) return
            val draft = draftHabit(base, newId = state.createdId)
            if (draft.name.isBlank()) return
            if (base != null) {
                val updated = draft.copy(id = base.id, createdAt = base.createdAt)
                if (updated != base) HabitCenter.updateHabit(updated)
            } else if (state.createdId != null && !state.createdPersisted) {
                HabitCenter.addHabit(draft)
                state.createdPersisted = true
            }
        }
    }

    fun scheduleFlush(delayMs: Long = 400L) {
        flushHolder.job?.cancel()
        flushHolder.job = scope.launch {
            delay(delayMs)
            flush()
        }
    }

    fun flushNow() {
        flushHolder.job?.cancel()
        flushHolder.job = scope.launch { flush() }
    }

    fun handleBack() {
        flushHolder.job?.cancel()
        scope.launch {
            flush()
            onBack()
        }
    }

    // 应用退到后台 / 被系统收起时也要把最后一次编辑落盘。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flushNow() }
    BackHandler(onBack = ::handleBack)

    val scheme = MaterialTheme.colorScheme

    LunoScreen(
        title = stringResource(
            if (isNew && state.createdId == null) {
                R.string.habit_create_title
            } else {
                R.string.habit_edit_title
            },
        ),
        onBack = ::handleBack,
        bottomContentPadding = ContentBottom,
        actions = {
            if (habit != null) {
                PressIconButton(
                    onClick = {
                        state.showColorPicker = false
                        state.dialog = HabitEditDialog.Delete
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.habit_delete),
                    )
                }
            }
        },
        dialogs = {
            // 同一时刻最多一个弹层；删除与补卡/取消打卡共用一个 dialog 槽位（ui-guidelines §3.2-5 / §4.4）。
            when (val dialog = state.dialog) {
                HabitEditDialog.Delete -> {
                    if (habit != null) {
                        ConfirmDialog(
                            title = stringResource(R.string.habit_delete),
                            text = stringResource(R.string.habit_delete_confirm),
                            destructive = true,
                            onDismissRequest = { state.dialog = null },
                            onConfirm = {
                                // 先停自动保存，避免删除后 onStop 又把习惯写回来。
                                state.deleted = true
                                flushHolder.job?.cancel()
                                scope.launch {
                                    HabitCenter.deleteHabit(habit.id)
                                    onBack()
                                }
                            },
                        )
                    }
                }
                is HabitEditDialog.Check -> {
                    if (habit != null) {
                        val date = LocalDate.ofEpochDay(dialog.day)
                        ConfirmDialog(
                            title = stringResource(
                                if (dialog.targetChecked) {
                                    R.string.habit_check_confirm_title
                                } else {
                                    R.string.habit_uncheck_confirm_title
                                },
                            ),
                            text = stringResource(
                                if (dialog.targetChecked) {
                                    R.string.habit_check_confirm_text
                                } else {
                                    R.string.habit_uncheck_confirm_text
                                },
                                date.monthValue,
                                date.dayOfMonth,
                            ),
                            confirmLabel = stringResource(
                                if (dialog.targetChecked) {
                                    R.string.habit_check_confirm_action
                                } else {
                                    R.string.habit_uncheck_confirm_action
                                },
                            ),
                            destructive = !dialog.targetChecked,
                            onDismissRequest = { state.dialog = null },
                            onConfirm = {
                                scope.launch {
                                    HabitCenter.setHabitCheck(
                                        habit.id,
                                        dialog.day,
                                        dialog.targetChecked,
                                    )
                                }
                            },
                        )
                    }
                }
                null -> Unit
            }
            if (state.showTimePicker) {
                val initial = state.editingReminderIndex?.let { shownReminders.getOrNull(it) }
                    ?: nextReminderTime(shownReminders)
                HabitTimePickerDialog(
                    initialHour = initial.hour,
                    initialMinute = initial.minute,
                    onDismiss = {
                        state.showTimePicker = false
                        state.editingReminderIndex = null
                    },
                    onConfirm = { hour, minute ->
                        val index = state.editingReminderIndex
                        val updated = if (index != null && index in shownReminders.indices) {
                            shownReminders.toMutableList().also {
                                it[index] = HabitReminderTime(hour, minute)
                            }
                        } else {
                            shownReminders + HabitReminderTime(hour, minute)
                        }
                        state.reminders = normalizeReminderTimes(updated)
                        state.showTimePicker = false
                        state.editingReminderIndex = null
                        flushNow()
                    },
                )
            }
            if (state.showSnoozeDialog) {
                SnoozeDurationDialog(
                    initialMinutes = shownSnoozeMinutes,
                    onDismiss = { state.showSnoozeDialog = false },
                    onConfirm = { minutes ->
                        state.snoozeMinutes = minutes
                        state.showSnoozeDialog = false
                        flushNow()
                    },
                )
            }
        },
        sheets = {
            if (state.showColorPicker) {
                ColorPickerBottomSheet(
                    onDismissRequest = { state.showColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = shownColorSpec.toInitialSelection(scheme),
                    onColorSelected = { selection ->
                        val colorSpec = selection.toColorSpec(scheme)
                        state.colorArgb = colorSpec.customColor
                        state.colorSourceName = colorSpec.source.name
                        state.colorThemeKeyName = colorSpec.themeKey.name
                        state.showColorPicker = false
                        scheduleFlush()
                    },
                )
            }
        },
    ) {
        LunoGroup(title = stringResource(R.string.habit_name)) {
            OutlinedTextField(
                value = shownName,
                onValueChange = { value ->
                    state.name = value
                    val needsCreate = state.createdId == null && isNew && value.trim().isNotEmpty()
                    if (needsCreate) {
                        // 新建习惯名字首次非空即创建，先落一个稳定 id。
                        state.createdId = UUID.randomUUID().toString()
                        state.createdPersisted = false
                        flushNow()
                    } else {
                        scheduleFlush()
                    }
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.habit_name_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
            )
        }
        LunoGroup {
            LunoSettingsRow(
                title = stringResource(R.string.habit_color),
                leading = {
                    Box(
                        Modifier
                            .size(MarkColorSize)
                            .clip(CircleShape)
                            .background(shownColorSpec.toColor()),
                    )
                },
                onClick = {
                    state.dialog = null
                    state.showColorPicker = true
                },
            )
        }
        LunoGroup(title = stringResource(R.string.habit_interval)) {
            LunoTextSlider(
                value = shownInterval.toFloat(),
                onValueChange = {
                    state.interval = it.roundToInt().coerceIn(1, 30)
                    scheduleFlush()
                },
                text = stringResource(R.string.habit_interval_summary, shownInterval),
                valueRange = 1f..30f,
                steps = 28,
            )
        }
        LunoGroup(title = stringResource(R.string.habit_daily_target)) {
            LunoTextSlider(
                value = shownTarget.toFloat(),
                onValueChange = {
                    state.dailyTarget = it.roundToInt().coerceIn(1, HabitMaxDailyTarget)
                    scheduleFlush()
                },
                text = stringResource(R.string.habit_daily_target_summary, shownTarget),
                valueRange = 1f..HabitMaxDailyTarget.toFloat(),
                steps = HabitMaxDailyTarget - 2,
            )
        }
        LunoGroup(title = stringResource(R.string.habit_reminder)) {
            LunoSwitchRow(
                title = stringResource(R.string.habit_reminder_enable),
                checked = shownReminderEnabled,
                onCheckedChange = { enabled ->
                    state.reminderEnabled = enabled
                    if (enabled && shownReminders.isEmpty()) {
                        state.reminders = listOf(HabitReminderTime())
                    }
                    scheduleFlush()
                },
            )
            if (shownReminderEnabled) {
                shownReminders.forEachIndexed { index, reminder ->
                    LunoSettingsRow(
                        title = stringResource(R.string.habit_reminder_time),
                        subtitle = stringResource(
                            R.string.habit_reminder_time_value,
                            reminder.hour,
                            reminder.minute,
                        ),
                        onClick = {
                            state.dialog = null
                            state.showSnoozeDialog = false
                            state.editingReminderIndex = index
                            state.showTimePicker = true
                        },
                        trailing = {
                            PressIconButton(
                                onClick = {
                                    state.reminders = normalizeReminderTimes(
                                        shownReminders.filterIndexed { i, _ -> i != index },
                                    )
                                    flushNow()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.habit_reminder_delete),
                                )
                            }
                        },
                    )
                }
                val atMax = shownReminders.size >= HabitMaxReminders
                LunoSettingsRow(
                    title = stringResource(R.string.habit_reminder_add),
                    subtitle = if (atMax) stringResource(R.string.habit_reminder_max) else "",
                    icon = Icons.Default.Add,
                    enabled = !atMax,
                    onClick = {
                        state.editingReminderIndex = null
                        state.showTimePicker = true
                    },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.habit_snooze),
                    subtitle = snoozeDurationLabel(shownSnoozeMinutes),
                    onClick = { state.showSnoozeDialog = true },
                )
            }
        }
        if (habit != null) {
            RecentChecksGrid(
                habitId = habit.id,
                color = shownColorSpec.toColor(),
                config = config,
                today = today,
                interval = shownInterval,
                target = shownTarget,
                onDayClick = { day, targetChecked ->
                    state.showColorPicker = false
                    state.dialog = HabitEditDialog.Check(day, targetChecked)
                },
            )
            HabitHeatmapCard(
                habitId = habit.id,
                color = shownColorSpec.toColor(),
                config = config,
                today = today,
                target = shownTarget,
            )
        }
    }
}

/**
 * 近 90 天热力图：纯展示，不响应点击；颜色 / 目标用编辑中的草稿值即时预览。
 *
 * 与面板热力图部件共用 [HabitHeatmapGrid] 与同一套几何 / 分档；这里按可用宽度铺满
 * （最多 13 列），窄屏自动减列，不做横向滚动。
 */
@Composable
private fun HabitHeatmapCard(
    habitId: String,
    color: Color,
    config: HabitConfig,
    today: Long,
    target: Int,
) {
    val description = stringResource(R.string.habit_heatmap_desc)
    LunoGroup(title = stringResource(R.string.habit_heatmap)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = HabitCalendarPaddingVertical),
            contentAlignment = Alignment.Center,
        ) {
            HabitHeatmapGrid(
                geometry = habitHeatmapGeometryFillWidth(maxWidth),
                today = today,
                checks = config.checks[habitId].orEmpty(),
                counts = config.counts[habitId].orEmpty(),
                target = target,
                habitColor = color,
                baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

/** 提醒时间选择：24 小时制 Material3 TimePicker，确定后回调 [onConfirm]。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_reminder_time)) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            PressTextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) {
                Text(stringResource(hunoia.luno.design.R.string.confirm))
            }
        },
        dismissButton = {
            PressTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.habit_reminder_cancel))
            }
        },
    )
}

/** 新增提醒时的默认时间：最后一个时间 + 1 小时（空列表用 20:00）。 */
private fun nextReminderTime(times: List<HabitReminderTime>): HabitReminderTime {
    val last = times.maxByOrNull { it.minutesOfDay } ?: return HabitReminderTime()
    val total = (last.minutesOfDay + 60) % (24 * 60)
    return HabitReminderTime(total / 60, total % 60)
}

/** 自动保存的挂起任务句柄：不放进 Compose state，避免每次排期都触发重组。 */
private class FlushJobHolder {
    var job: Job? = null
}

/** 一屏一个状态类型：字段草稿、颜色选择、确认弹层全在这里（architecture §8.4）。 */
@Stable
internal class HabitEditState(
    name: String? = null,
    colorArgb: Int? = null,
    colorSourceName: String? = null,
    colorThemeKeyName: String? = null,
    interval: Int? = null,
    dailyTarget: Int? = null,
    reminderEnabled: Boolean? = null,
    reminders: List<HabitReminderTime>? = null,
    snoozeMinutes: Int? = null,
    createdId: String? = null,
    createdPersisted: Boolean = false,
) {
    var name by mutableStateOf(name)
    var colorArgb by mutableStateOf(colorArgb)
    var colorSourceName by mutableStateOf(colorSourceName)
    var colorThemeKeyName by mutableStateOf(colorThemeKeyName)
    var interval by mutableStateOf(interval)
    var dailyTarget by mutableStateOf(dailyTarget)
    var reminderEnabled by mutableStateOf(reminderEnabled)
    var reminders by mutableStateOf(reminders)
    var snoozeMinutes by mutableStateOf(snoozeMinutes)
    var createdId by mutableStateOf(createdId)
    var createdPersisted by mutableStateOf(createdPersisted)
    var deleted by mutableStateOf(false)
    var showColorPicker by mutableStateOf(false)
    var showTimePicker by mutableStateOf(false)
    var showSnoozeDialog by mutableStateOf(false)
    var editingReminderIndex by mutableStateOf<Int?>(null)
    var dialog by mutableStateOf<HabitEditDialog?>(null)
}

/** 弹层互斥：删除 / 补卡 / 取消打卡，任一时刻只存在一个（ui-guidelines §3.2-5）。 */
internal sealed interface HabitEditDialog {
    object Delete : HabitEditDialog

    /** [targetChecked] = 确认后要写入的目标状态；文案与破坏性语义都由此派生。 */
    data class Check(val day: Long, val targetChecked: Boolean) : HabitEditDialog
}

/** 只保存字段草稿；弹层是瞬态，重建后不恢复。新建 id 也要存，避免进程重建后重复创建。 */
private val HabitEditStateSaver: Saver<HabitEditState, Bundle> = Saver(
    save = { state ->
        Bundle().apply {
            putString("name", state.name)
            state.colorArgb?.let { putInt("color", it) }
            putString("colorSource", state.colorSourceName)
            putString("colorThemeKey", state.colorThemeKeyName)
            state.interval?.let { putInt("interval", it) }
            state.dailyTarget?.let { putInt("dailyTarget", it) }
            state.reminderEnabled?.let { putBoolean("reminderEnabled", it) }
            state.reminders?.let { reminders ->
                putStringArrayList(
                    "reminders",
                    ArrayList(reminders.map { "${it.hour}:${it.minute}" }),
                )
            }
            state.snoozeMinutes?.let { putInt("snoozeMinutes", it) }
            putString("createdId", state.createdId)
            putBoolean("createdPersisted", state.createdPersisted)
        }
    },
    restore = { bundle ->
        HabitEditState(
            name = bundle.getString("name"),
            colorArgb = if (bundle.containsKey("color")) bundle.getInt("color") else null,
            colorSourceName = bundle.getString("colorSource"),
            colorThemeKeyName = bundle.getString("colorThemeKey"),
            interval = if (bundle.containsKey("interval")) bundle.getInt("interval") else null,
            dailyTarget = if (bundle.containsKey("dailyTarget")) bundle.getInt("dailyTarget") else null,
            reminderEnabled = if (bundle.containsKey("reminderEnabled")) bundle.getBoolean("reminderEnabled") else null,
            reminders = bundle.getStringArrayList("reminders")?.mapNotNull { entry ->
                val parts = entry.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()
                val minute = parts.getOrNull(1)?.toIntOrNull()
                if (hour != null && minute != null) HabitReminderTime(hour, minute) else null
            },
            snoozeMinutes = if (bundle.containsKey("snoozeMinutes")) bundle.getInt("snoozeMinutes") else null,
            createdId = bundle.getString("createdId"),
            createdPersisted = bundle.getBoolean("createdPersisted", false),
        )
    },
)

/** 近 30 天补 / 改打卡：标准日历网格（星期头固定一~日，today 落真实星期列、前导空格对齐首日）。 */
@Composable
private fun RecentChecksGrid(
    habitId: String,
    color: Color,
    config: HabitConfig,
    today: Long,
    interval: Int,
    target: Int,
    onDayClick: (day: Long, targetChecked: Boolean) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val palette = habitDayPalette(primary = color, neutral = outline)
    val checks = config.checks[habitId].orEmpty()
    val weekdays = stringArrayResource(R.array.habit_weekdays)
    // 标准日历：today 在末行、其真实星期列（一=0…日=6）；窗口 today-29..today，
    // today 右侧（未来）与窗口前（前导对齐）留空。
    val todayPos = 4 * 7 + (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    LunoGroup(title = stringResource(R.string.habit_recent_checks)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = HabitCalendarPaddingVertical),
        ) {
            // 顶部星期单字（一~日），与下方列对齐。
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { weekday ->
                    Text(
                        text = weekday,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = outline.copy(alpha = HabitWeekdayAlpha),
                    )
                }
            }
            Spacer(Modifier.height(HabitCalendarHeaderGap))
            // 35 槽：pos = row*7+col；dayOffset = todayPos-pos（0=today，>0=过去，<0=未来）。
            for (row in 0 until 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HabitCalendarCellGap),
                ) {
                    for (col in 0 until 7) {
                        val dayOffset = todayPos - (row * 7 + col)
                        if (dayOffset < 0 || dayOffset > 29) {
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = HabitDayCellMinHeight),
                            )
                        } else {
                            val day = today - dayOffset
                            val state = habitDayState(checks, day, interval, today)
                            val checked = config.isChecked(habitId, day)
                            val count = config.countOf(habitId, day, target)
                            val date = LocalDate.ofEpochDay(day)
                            DayDotCell(
                                month = date.monthValue,
                                day = date.dayOfMonth,
                                visual = state.visual(),
                                stateLabel = state.label(),
                                isToday = dayOffset == 0,
                                palette = palette,
                                count = count,
                                target = target,
                                onClick = { onDayClick(day, !checked) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日历格子：视觉语义由 [HabitDayVisual] 统一派生，与面板部件的四态口径一致
 * （跳过日 = 半透明主色 + 横杠）。多次习惯在圆点内画进度弧；点击只发事件，
 * 写盘在确认弹层之后。
 */
@Composable
private fun DayDotCell(
    month: Int,
    day: Int,
    visual: HabitDayVisual,
    stateLabel: String,
    isToday: Boolean,
    palette: HabitDayPalette,
    count: Int,
    target: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cellDesc = stringResource(R.string.habit_day_cell_desc, month, day, stateLabel)
    Column(
        modifier = modifier
            // heightIn 而非固定 height：fontScale 1.4 / 2.0 下文字可长高、不裁切（ui-guidelines §1.5）。
            .heightIn(min = HabitDayCellMinHeight)
            .clip(SmallShape)
            .then(
                if (isToday) {
                    Modifier.border(HabitDayDotStroke, palette.primary, SmallShape)
                } else {
                    Modifier
                },
            )
            .pressClickable(onClick = onClick)
            .semantics { contentDescription = cellDesc }
            .padding(vertical = HabitDayCellPaddingVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) palette.primary else palette.neutral.copy(alpha = HabitDayTextAlpha),
        )
        Spacer(Modifier.height(HabitDayTextDotGap))
        Box(
            modifier = Modifier
                .size(HabitDayDotSize)
                .clip(CircleShape)
                .background(visual.fillColor(palette))
                .border(HabitDayDotStroke, visual.borderColor(palette), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HabitDayMarkIcon(
                mark = visual.mark,
                color = visual.markTint(palette),
                modifier = Modifier.size(HabitDayCheckIconSize),
            )
            // 多次习惯的部分完成：与面板点阵 / 桌面部件同一套进度弧语义。
            if (target > 1 && count in 1 until target) {
                HabitProgressArc(
                    progress = count.toFloat() / target,
                    color = palette.primary,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
