package hunoia.luno.ui.settings.habit

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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ColorSelection
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.resolveColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 习惯编辑：新增（habitId 空）或编辑。字段 = 名字 / 颜色 / 每 x 日 / 近 7 天补改。
 * 保存走底部 FAB，删除在顶栏（仅编辑态）。
 *
 * 字段状态初始为 null、渲染回退到已加载的 [Habit] 值：配置流首帧为空（`initialValue`），
 * 否则 `rememberSaveable` 会用空值初始化导致重进丢内容；用户一编辑即覆盖。
 * 增删改与打卡写入走 [HabitCenter] 门面（配置写入，不走 Action / Dispatcher）。
 */
@Composable
fun HabitEditScreen(
    habitId: String?,
    onBack: () -> Unit,
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitConfig())
    val habit = config.habitById(habitId.orEmpty())
    val today = LocalDate.now().toEpochDay()
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(habitId) { mutableStateOf<String?>(null) }
    var colorArgb by rememberSaveable(habitId) { mutableStateOf<Int?>(null) }
    var interval by rememberSaveable(habitId) { mutableStateOf<Int?>(null) }
    val shownName = name ?: habit?.name.orEmpty()
    val shownColor = colorArgb ?: habit?.color ?: Habit().color
    val shownInterval = interval ?: habit?.interval ?: 1

    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun save() {
        val trimmed = shownName.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            if (habit == null) {
                HabitCenter.addHabit(
                    Habit(
                        name = trimmed,
                        color = shownColor,
                        interval = shownInterval,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                HabitCenter.updateHabit(
                    habit.copy(name = trimmed, color = shownColor, interval = shownInterval),
                )
            }
            onBack()
        }
    }

    val scheme = MaterialTheme.colorScheme
    val themeColorArgb = remember(scheme) {
        ThemeColorKey.entries.associateWith { it.resolveColor(scheme).toArgb() }
    }

    LunoScreen(
        title = stringResource(
            if (habit == null) R.string.habit_create_title else R.string.habit_edit_title,
        ),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (habit != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
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
                .size(64.dp)
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
            if (showDeleteConfirm && habit != null) {
                ConfirmDialog(
                    title = stringResource(R.string.habit_delete),
                    text = stringResource(R.string.habit_delete_confirm),
                    destructive = true,
                    onDismissRequest = { showDeleteConfirm = false },
                    onConfirm = {
                        scope.launch {
                            HabitCenter.deleteHabit(habit.id)
                            onBack()
                        }
                    },
                )
            }
        },
        sheets = {
            if (showColorPicker) {
                ColorPickerBottomSheet(
                    onDismissRequest = { showColorPicker = false },
                    initialColor = Color(shownColor),
                    onColorSelected = { selection ->
                        colorArgb = when (selection) {
                            is ColorSelection.Custom -> selection.color.toArgb()
                            is ColorSelection.Theme -> themeColorArgb[selection.key] ?: colorArgb
                        }
                        showColorPicker = false
                    },
                )
            }
        },
    ) {
        LunoGroup(title = stringResource(R.string.habit_name)) {
            OutlinedTextField(
                value = shownName,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.habit_name_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LunoGroup {
            LunoSettingsRow(
                title = stringResource(R.string.habit_color),
                leading = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(shownColor)),
                    )
                },
                onClick = { showColorPicker = true },
            )
        }
        LunoGroup(title = stringResource(R.string.habit_interval)) {
            LunoTextSlider(
                value = shownInterval.toFloat(),
                onValueChange = { interval = it.roundToInt().coerceIn(1, 30) },
                text = stringResource(R.string.habit_interval_summary, shownInterval),
                valueRange = 1f..30f,
                steps = 28,
            )
        }
        if (habit != null) {
            RecentChecksGrid(
                habitId = habit.id,
                color = Color(shownColor),
                config = config,
                today = today,
            )
        }
    }
}

/** 近 30 天补 / 改打卡：标准日历网格（星期头固定一~日，today 落真实星期列、前导空格对齐首日）。 */
@Composable
private fun RecentChecksGrid(
    habitId: String,
    color: Color,
    config: HabitConfig,
    today: Long,
) {
    val scope = rememberCoroutineScope()
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val weekdays = stringArrayResource(R.array.habit_weekdays)
    // 标准日历：today 在末行、其真实星期列（一=0…日=6）；窗口 today-29..today，
    // today 右侧（未来）与窗口前（前导对齐）留空。
    val todayPos = 4 * 7 + (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    LunoGroup(title = stringResource(R.string.habit_recent_checks)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 12.dp),
        ) {
            // 顶部星期单字（一~日），与下方列对齐。
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { weekday ->
                    Text(
                        text = weekday,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = outline.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // 35 槽：pos = row*7+col；dayOffset = todayPos-pos（0=today，>0=过去，<0=未来）。
            for (row in 0 until 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (col in 0 until 7) {
                        val dayOffset = todayPos - (row * 7 + col)
                        if (dayOffset < 0 || dayOffset > 29) {
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp),
                            )
                        } else {
                            val day = today - dayOffset
                            val checked = config.isChecked(habitId, day)
                            DayDotCell(
                                day = LocalDate.ofEpochDay(day).dayOfMonth,
                                checked = checked,
                                isToday = dayOffset == 0,
                                color = color,
                                outline = outline,
                                onClick = {
                                    scope.launch { HabitCenter.setHabitCheck(habitId, day, !checked) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDotCell(
    day: Int,
    checked: Boolean,
    isToday: Boolean,
    color: Color,
    outline: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, color, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) color else outline.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (checked) color else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = when {
                        checked -> color
                        isToday -> color
                        else -> outline.copy(alpha = 0.4f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
