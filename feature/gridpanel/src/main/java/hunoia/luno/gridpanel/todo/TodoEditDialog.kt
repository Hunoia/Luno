package hunoia.luno.gridpanel.todo

import android.view.WindowManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoReminderFormat
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * 待办编辑弹窗（应用内列表页 / 面板部件 / 桌面部件共用）。
 *
 * [todoId] 为空 = 新增；非空 = 编辑（预填文字与提醒，额外提供删除）。
 * 右上角闹钟图标打开「提醒」视图：日期 + 时间都在**同一张卡片内切换**，
 * 不再嵌套二级弹层（ui-guidelines §3.2-5）。已完成的待办隐藏闹钟入口。
 *
 * 写回 [TodoCenter]；[onChanged] 在写成功后调用（桌面部件用来立即刷新）。
 */
@Composable
fun TodoEditDialog(
    todoId: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
) {
    // Dialog 是独立窗口且半透明：adjustResize 在这种窗口上不可靠（键盘盖住内容）。
    // 改 ADJUST_NOTHING 让窗口不自动缩放/平移，改由 imePadding() 把内容顶到键盘上方。
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    val config by TodoCenter.todoConfig.collectAsState(initial = TodoConfig())
    val existing = todoId?.let { config.todoById(it) }
    var textValue by rememberSaveable(todoId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    // 数据流到达后回填：首帧 config 尚未加载（existing=null），直接初始化会得到空串。
    var filled by rememberSaveable(todoId) { mutableStateOf(false) }
    LaunchedEffect(existing?.id) {
        if (!filled && existing != null) {
            textValue = TextFieldValue(existing.text, selection = TextRange(existing.text.length))
            filled = true
        }
    }
    var reminder by rememberSaveable(todoId, stateSaver = ReminderSaver) {
        mutableStateOf(existing?.reminder)
    }
    var reminderFilled by rememberSaveable(todoId) { mutableStateOf(false) }
    LaunchedEffect(existing?.id) {
        if (!reminderFilled && existing != null) {
            reminder = existing.reminder
            reminderFilled = true
        }
    }
    var editingReminder by rememberSaveable(todoId) { mutableStateOf(false) }
    // 提醒视图比主视图高，需要滚动；状态提到这里，进入提醒视图时才能主动滚回顶部。
    val reminderScroll = rememberScrollState()

    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val isEdit = existing != null
    val trimmed = textValue.text.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val curOnDismiss by rememberUpdatedState(onDismiss)
    val curOnChanged by rememberUpdatedState(onChanged)

    fun submit() {
        if (trimmed.isEmpty()) return
        keyboard?.hide()
        val text = trimmed
        val targetReminder = reminder
        scope.launch {
            if (existing == null) {
                TodoCenter.addTodo(
                    TodoItem(
                        text = text,
                        createdAt = System.currentTimeMillis(),
                        reminder = targetReminder,
                    ),
                )
            } else {
                TodoCenter.updateTodo(existing.copy(text = text, reminder = targetReminder))
            }
            curOnChanged()
            curOnDismiss()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 边到边：内容延伸到状态栏/导航栏区域，IME inset 才会派发下来（imePadding 才有值）。
            decorFitsSystemWindows = false,
        ),
    ) {
        // 遮罩点按关闭；卡片本体不响应遮罩点击（镜像 ConfirmDialog）。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // 键盘上方：IME inset 撑开内容底边，卡片（BottomCenter）贴住键盘上沿。
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxCardHeight = (maxHeight - PageGutter * 2).coerceAtLeast(240.dp)
            Column(
                modifier = Modifier
                    .padding(horizontal = PageGutter, vertical = PageGutter)
                    .fillMaxWidth()
                    .clip(ExtraLargeShape)
                    .background(MaterialTheme.colorScheme.surface, ExtraLargeShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .then(
                        if (editingReminder) {
                            Modifier
                                .heightIn(max = maxCardHeight)
                                .verticalScroll(reminderScroll)
                        } else {
                            Modifier
                        },
                    )
                    .padding(LargeGutter),
                verticalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
                if (editingReminder) {
                    TodoReminderEditor(
                        initial = reminder,
                        scrollState = reminderScroll,
                        onDismiss = { editingReminder = false },
                        onConfirm = {
                            reminder = it
                            editingReminder = false
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                if (isEdit) R.string.todo_edit_title else R.string.todo_create_title,
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // 已完成的待办不提醒：隐藏闹钟入口。
                        if (existing?.done != true) {
                            IconButton(
                                onClick = {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    editingReminder = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = stringResource(
                                        if (reminder == null) {
                                            R.string.todo_reminder_add
                                        } else {
                                            R.string.todo_reminder_edit
                                        },
                                    ),
                                    tint = if (reminder == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.todo_text_hint)) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    reminder?.let {
                        val past = it.isPast(System.currentTimeMillis(), ZoneId.systemDefault())
                        val label = if (past) {
                            stringResource(R.string.todo_reminder_expired_short)
                        } else {
                            TodoReminderFormat.label(appContext, it)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = existing?.done != true,
                                    onClick = {
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        editingReminder = true
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (past) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (past) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isEdit) {
                            // 直删（与应用内一致，无二次确认）。
                            TextButton(onClick = {
                                scope.launch {
                                    TodoCenter.deleteTodo(existing.id)
                                    curOnChanged()
                                    curOnDismiss()
                                }
                            }) {
                                Text(
                                    text = stringResource(R.string.todo_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(hunoia.luno.design.R.string.cancel))
                        }
                        TextButton(onClick = { submit() }, enabled = trimmed.isNotEmpty()) {
                            Text(stringResource(R.string.todo_save))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 提醒编辑视图（同一张卡片内切换）：日期选择 + 时间选择 + 校验 + 删除。
 *
 * 日期用卡片内自绘的紧凑月历（Material3 DatePicker 固定 360dp 宽，窄屏卡片会被裁掉）；
 * 时间用 24 小时制 [TimeInput]（数字键盘，避免表盘拖动与卡片滚动抢手势）。
 * 已过去的时间禁止确定，并给出提示；[initial] 为空或已过期时默认「一小时后」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoReminderEditor(
    initial: TodoReminder?,
    scrollState: ScrollState,
    onDismiss: () -> Unit,
    onConfirm: (TodoReminder?) -> Unit,
) {
    // 每次重组重取「现在」：弹窗停留几分钟后，过期校验仍按真实时间判断。
    val now = System.currentTimeMillis()
    val zone = remember { ZoneId.systemDefault() }
    val fallback = remember {
        val oneHourLater = LocalDateTime.now(zone).plusHours(1)
        TodoReminder(
            day = oneHourLater.toLocalDate().toEpochDay(),
            minuteOfDay = oneHourLater.hour * 60 + oneHourLater.minute,
        )
    }
    // 已过期的旧提醒不作为初值：一进编辑视图就卡在「不能确定」没有意义，
    // 直接给「一小时后」；原提醒仍在主视图显示「已过期」，取消即可保留。
    val seed = initial?.takeIf { !it.isPast(now, zone) } ?: fallback
    val todayEpochDay = LocalDate.now(zone).toEpochDay()
    var selectedDay by rememberSaveable(initial) { mutableLongStateOf(seed.day) }
    val timeState = rememberTimePickerState(
        initialHour = seed.hour,
        initialMinute = seed.minute,
        is24Hour = true,
    )
    val selected = TodoReminder(
        day = selectedDay,
        minuteOfDay = timeState.hour * 60 + timeState.minute,
    )
    val past = selected.isPast(now, zone)

    // 文本输入框被移出组合后焦点会落到时间输入框，系统立刻弹数字键盘并把卡片滚走顶部；
    // 进入提醒视图先清焦点 / 收键盘，用户点时间输入框时再弹。
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        delay(80)
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        scrollState.scrollTo(0)
    }

    Text(
        text = stringResource(R.string.todo_reminder_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.todo_reminder_pick_date),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TodoDatePicker(
        selectedDay = selectedDay,
        todayEpochDay = todayEpochDay,
        onSelect = { selectedDay = it },
    )
    Text(
        text = stringResource(R.string.todo_reminder_pick_time),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TimeInput(state = timeState)
    Text(
        text = if (past) {
            stringResource(R.string.todo_reminder_expired)
        } else {
            TodoReminderFormat.dialogStatus(LocalContext.current.applicationContext, selected)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (past) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (initial != null) {
            TextButton(onClick = {
                // 删除提醒：清空数据并回到主视图（保存时随待办一起写回）。
                onConfirm(null)
            }) {
                Text(
                    text = stringResource(R.string.todo_reminder_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text(stringResource(hunoia.luno.design.R.string.cancel))
        }
        TextButton(
            onClick = { onConfirm(selected) },
            enabled = !past,
        ) {
            Text(stringResource(R.string.todo_reminder_confirm))
        }
    }
}

/**
 * 紧凑月历：卡片内自绘 7 列 × N 行，窄屏也能完整显示。
 *
 * 只允许选今天及以后（与提醒语义一致）；当前月不能再往前翻。
 */
@Composable
private fun TodoDatePicker(
    selectedDay: Long,
    todayEpochDay: Long,
    onSelect: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = remember { Locale.getDefault() }
    val today = remember(todayEpochDay) { LocalDate.ofEpochDay(todayEpochDay) }
    val currentMonth = remember(today) { YearMonth.from(today) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(selectedDay))) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                enabled = visibleMonth > currentMonth,
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
            }
            Text(
                text = stringResource(
                    R.string.todo_reminder_month,
                    visibleMonth.year,
                    visibleMonth.monthValue,
                ),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (weekday in 1..7) {
                Text(
                    text = DayOfWeek.of(weekday).getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        val leading = visibleMonth.atDay(1).dayOfWeek.value - 1
        val days = visibleMonth.lengthOfMonth()
        val weeks = (leading + days + 6) / 7
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val dayOfMonth = week * 7 + column - leading + 1
                    if (dayOfMonth in 1..days) {
                        val epochDay = visibleMonth.atDay(dayOfMonth).toEpochDay()
                        DayCell(
                            dayOfMonth = dayOfMonth,
                            selected = epochDay == selectedDay,
                            isToday = epochDay == todayEpochDay,
                            selectable = epochDay >= todayEpochDay,
                            onClick = { onSelect(epochDay) },
                        )
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 月历单元格：选中 = 主色实心；今天 = 主色文字；今天之前 = 置灰不可点。 */
@Composable
private fun RowScope.DayCell(
    dayOfMonth: Int,
    selected: Boolean,
    isToday: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val textColor = when {
        selected -> scheme.onPrimary
        !selectable -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
        isToday -> scheme.primary
        else -> scheme.onSurface
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (selectable) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (selected) scheme.primary else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}


/** `TodoReminder?` 的 Bundle 友好编码：`day:minuteOfDay`，空串 = 无提醒。 */
private val ReminderSaver: Saver<TodoReminder?, String> = Saver(
    save = { reminder ->
        reminder?.let { "${it.day}:${it.minuteOfDay}" } ?: ""
    },
    restore = { raw ->
        if (raw.isEmpty()) {
            null
        } else {
            val separator = raw.indexOf(':')
            if (separator <= 0) {
                null
            } else {
                val day = raw.substring(0, separator).toLongOrNull()
                val minute = raw.substring(separator + 1).toIntOrNull()
                if (day != null && minute != null) TodoReminder(day, minute) else null
            }
        }
    },
)
