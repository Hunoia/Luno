package hunoia.luno.gridpanel.habit

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitDefaultSnoozeMinutes
import hunoia.luno.config.model.HabitSnoozeMinuteOptions
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.config.model.habitDayState
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.internal.rememberTodayEpochDay
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.FabSize
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.HabitCalendarCellGap
import hunoia.luno.ui.theme.LargeShape
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 习惯编辑：新增（habitId 空）或编辑。字段 = 名字 / 颜色 / 每 x 日 / 近 30 天补改。
 * 保存走底部 FAB，删除在顶栏（仅编辑态）。
 *
 * 一屏一个状态类型 [HabitEditState]（architecture §8.4）：字段状态初始为 null、渲染回退到
 * 已加载的 [Habit] 值——配置流首帧为空（`initialValue`），否则会被空值初始化导致重进丢内容。
 * 增删改与打卡写入走 [HabitCenter] 门面（配置写入，不走 Action / Dispatcher）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    habitId: String?,
    onBack: () -> Unit,
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitConfig())
    val habit = config.habitById(habitId.orEmpty())
    val today = rememberTodayEpochDay()
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(habitId, saver = HabitEditStateSaver) { HabitEditState() }
    val shownName = state.name ?: habit?.name.orEmpty()
    val shownColor = state.colorArgb ?: habit?.color ?: Habit().color
    val shownColorSpec = ColorSpec(
        source = state.colorSourceName?.let { ColorSource.valueOf(it) }
            ?: habit?.colorSource ?: ColorSource.Custom,
        themeKey = state.colorThemeKeyName?.let { ThemeColorKey.valueOf(it) }
            ?: habit?.colorThemeKey ?: ThemeColorKey.Primary,
        customColor = shownColor,
    )
    // 日历点阵用「当前编辑中的间隔」即时预览，保存前所见即所得。
    val shownInterval = state.interval ?: habit?.interval ?: 1
    val shownReminderEnabled = state.reminderEnabled ?: habit?.reminderEnabled ?: false
    val shownReminderHour = state.reminderHour ?: habit?.reminderHour ?: 20
    val shownReminderMinute = state.reminderMinute ?: habit?.reminderMinute ?: 0
    val shownSnoozeMinutes = state.snoozeMinutes ?: habit?.snoozeMinutes ?: HabitDefaultSnoozeMinutes

    fun save() {
        val trimmed = shownName.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            if (habit == null) {
                HabitCenter.addHabit(
                    Habit(
                        name = trimmed,
                        color = shownColorSpec.customColor,
                        colorSource = shownColorSpec.source,
                        colorThemeKey = shownColorSpec.themeKey,
                        interval = shownInterval,
                        reminderEnabled = shownReminderEnabled,
                        reminderHour = shownReminderHour,
                        reminderMinute = shownReminderMinute,
                        snoozeMinutes = shownSnoozeMinutes,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                HabitCenter.updateHabit(
                    habit.copy(
                        name = trimmed,
                        color = shownColorSpec.customColor,
                        colorSource = shownColorSpec.source,
                        colorThemeKey = shownColorSpec.themeKey,
                        interval = shownInterval,
                        reminderEnabled = shownReminderEnabled,
                        reminderHour = shownReminderHour,
                        reminderMinute = shownReminderMinute,
                        snoozeMinutes = shownSnoozeMinutes,
                    ),
                )
            }
            onBack()
        }
    }

    val scheme = MaterialTheme.colorScheme

    LunoScreen(
        title = stringResource(
            if (habit == null) R.string.habit_create_title else R.string.habit_edit_title,
        ),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (habit != null) {
                IconButton(
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
        floatingContent = { _ ->
            val fabModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PageGutter, bottom = ContentBottom)
                .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                .size(FabSize)
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FloatingActionButton(
                    onClick = ::save,
                    modifier = fabModifier,
                    shape = FloatingActionButtonDefaults.shape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.habit_save),
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
                HabitTimePickerDialog(
                    initialHour = shownReminderHour,
                    initialMinute = shownReminderMinute,
                    onDismiss = { state.showTimePicker = false },
                    onConfirm = { hour, minute ->
                        state.reminderHour = hour
                        state.reminderMinute = minute
                        state.showTimePicker = false
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
                    },
                )
            }
        },
    ) {
        LunoGroup(title = stringResource(R.string.habit_name)) {
            OutlinedTextField(
                value = shownName,
                onValueChange = { state.name = it },
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
                onValueChange = { state.interval = it.roundToInt().coerceIn(1, 30) },
                text = stringResource(R.string.habit_interval_summary, shownInterval),
                valueRange = 1f..30f,
                steps = 28,
            )
        }
        LunoGroup(title = stringResource(R.string.habit_reminder)) {
            LunoSwitchRow(
                title = stringResource(R.string.habit_reminder_enable),
                checked = shownReminderEnabled,
                onCheckedChange = { state.reminderEnabled = it },
            )
            if (shownReminderEnabled) {
                LunoSettingsRow(
                    title = stringResource(R.string.habit_reminder_time),
                    subtitle = stringResource(
                        R.string.habit_reminder_time_value,
                        shownReminderHour,
                        shownReminderMinute,
                    ),
                    onClick = { state.showTimePicker = true },
                )
                ChoiceRow(
                    title = stringResource(R.string.habit_snooze),
                    subtitle = stringResource(R.string.habit_snooze_hint),
                    options = HabitSnoozeMinuteOptions.map {
                        stringResource(R.string.habit_snooze_minutes, it)
                    },
                    selectedIndex = HabitSnoozeMinuteOptions
                        .indexOf(shownSnoozeMinutes).coerceAtLeast(0),
                    onSelect = { state.snoozeMinutes = HabitSnoozeMinuteOptions[it] },
                    shape = LargeShape,
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
                onDayClick = { day, targetChecked ->
                    state.showColorPicker = false
                    state.dialog = HabitEditDialog.Check(day, targetChecked)
                },
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
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) {
                Text(stringResource(hunoia.luno.design.R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.habit_reminder_cancel))
            }
        },
    )
}

/** 一屏一个状态类型：字段草稿、颜色选择、确认弹层全在这里（architecture §8.4）。 */
@Stable
internal class HabitEditState(
    name: String? = null,
    colorArgb: Int? = null,
    colorSourceName: String? = null,
    colorThemeKeyName: String? = null,
    interval: Int? = null,
    reminderEnabled: Boolean? = null,
    reminderHour: Int? = null,
    reminderMinute: Int? = null,
    snoozeMinutes: Int? = null,
) {
    var name by mutableStateOf(name)
    var colorArgb by mutableStateOf(colorArgb)
    var colorSourceName by mutableStateOf(colorSourceName)
    var colorThemeKeyName by mutableStateOf(colorThemeKeyName)
    var interval by mutableStateOf(interval)
    var reminderEnabled by mutableStateOf(reminderEnabled)
    var reminderHour by mutableStateOf(reminderHour)
    var reminderMinute by mutableStateOf(reminderMinute)
    var snoozeMinutes by mutableStateOf(snoozeMinutes)
    var showColorPicker by mutableStateOf(false)
    var showTimePicker by mutableStateOf(false)
    var dialog by mutableStateOf<HabitEditDialog?>(null)
}

/** 弹层互斥：删除 / 补卡 / 取消打卡，任一时刻只存在一个（ui-guidelines §3.2-5）。 */
internal sealed interface HabitEditDialog {
    object Delete : HabitEditDialog

    /** [targetChecked] = 确认后要写入的目标状态；文案与破坏性语义都由此派生。 */
    data class Check(val day: Long, val targetChecked: Boolean) : HabitEditDialog
}

/** 只保存字段草稿；弹层是瞬态，重建后不恢复（与改造前 remember 的行为一致）。 */
private val HabitEditStateSaver: Saver<HabitEditState, Bundle> = Saver(
    save = { state ->
        Bundle().apply {
            putString("name", state.name)
            state.colorArgb?.let { putInt("color", it) }
            putString("colorSource", state.colorSourceName)
            putString("colorThemeKey", state.colorThemeKeyName)
            state.interval?.let { putInt("interval", it) }
            state.reminderEnabled?.let { putBoolean("reminderEnabled", it) }
            state.reminderHour?.let { putInt("reminderHour", it) }
            state.reminderMinute?.let { putInt("reminderMinute", it) }
            state.snoozeMinutes?.let { putInt("snoozeMinutes", it) }
        }
    },
    restore = { bundle ->
        HabitEditState(
            name = bundle.getString("name"),
            colorArgb = if (bundle.containsKey("color")) bundle.getInt("color") else null,
            colorSourceName = bundle.getString("colorSource"),
            colorThemeKeyName = bundle.getString("colorThemeKey"),
            interval = if (bundle.containsKey("interval")) bundle.getInt("interval") else null,
            reminderEnabled = if (bundle.containsKey("reminderEnabled")) bundle.getBoolean("reminderEnabled") else null,
            reminderHour = if (bundle.containsKey("reminderHour")) bundle.getInt("reminderHour") else null,
            reminderMinute = if (bundle.containsKey("reminderMinute")) bundle.getInt("reminderMinute") else null,
            snoozeMinutes = if (bundle.containsKey("snoozeMinutes")) bundle.getInt("snoozeMinutes") else null,
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
                            val date = LocalDate.ofEpochDay(day)
                            DayDotCell(
                                month = date.monthValue,
                                day = date.dayOfMonth,
                                visual = state.visual(),
                                stateLabel = state.label(),
                                isToday = dayOffset == 0,
                                palette = palette,
                                onClick = { onDayClick(day, state != HabitDayState.CHECKED) },
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
 * （跳过日 = 半透明主色 + 横杠）。点击只发事件，写盘在确认弹层之后。
 */
@Composable
private fun DayDotCell(
    month: Int,
    day: Int,
    visual: HabitDayVisual,
    stateLabel: String,
    isToday: Boolean,
    palette: HabitDayPalette,
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
            .clickable(onClick = onClick)
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
        }
    }
}
