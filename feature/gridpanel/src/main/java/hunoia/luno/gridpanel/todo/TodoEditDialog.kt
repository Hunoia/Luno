package hunoia.luno.gridpanel.todo

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoReminderFormat
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.ReminderPickerHeight
import hunoia.luno.ui.theme.SubMinInteractiveSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.resume
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.pressClickable

/**
 * 待办编辑弹窗（应用内列表页 / 面板部件 / 桌面部件共用）。
 *
 * [todoId] 为空 = 新增；非空 = 编辑（预填文字与提醒，额外提供删除）。
 * 右上角闹钟图标打开「提醒」视图：日期月历与时间表盘在**同一块固定区域**里切换，
 * 不嵌套二级弹层（ui-guidelines §3.2-5）；提醒视图不含文本输入，键盘全程不参与。
 * 已完成的待办隐藏闹钟入口。
 *
 * 窗口 / 遮罩 / 进出场动画由 [AnchoredDialog] 统一提供；写回 [TodoCenter] 后先播退场再关闭，
 * [onChanged] 在写成功后调用（桌面部件用来立即刷新）。
 */
@Composable
fun TodoEditDialog(
    todoId: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
) {
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
    // 等 IME 收完再切提醒视图（ui-guidelines §3.2-7）：见内容里的 pendingEnterReminder。
    var pendingEnterReminder by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val isEdit = existing != null
    val trimmed = textValue.text.trim()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val curOnChanged by rememberUpdatedState(onChanged)
    // 主视图高度上限取稳定值（屏幕高），不取 imePadding 内的实时高度：
    // 键盘动画期间后者逐帧变小，卡片会跟着逐帧重排（§3.2-8）。键盘占位由 imePadding 的父约束夹住。
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp.dp - PageGutter * 2)
        .coerceAtLeast(240.dp)
    val mainScroll = rememberScrollState()

    AnchoredDialog(
        onDismiss = onDismiss,
        contentPadding = PaddingValues(PageGutter),
        // 提醒视图内容高度固定（月历 / 表盘同区域切换），不需要滚动；
        // 主视图在大字体 + 键盘下仍可能超高，保留滚动兜底。
        cardModifier = if (editingReminder) {
            Modifier
        } else {
            Modifier
                .heightIn(max = maxCardHeight)
                .verticalScroll(mainScroll)
        },
    ) { dismiss ->
        val density = LocalDensity.current
        val dialogView = LocalView.current
        // 键盘控制器必须在弹窗内容里取：外面取到的是宿主 Activity 窗口的控制器，
        // 在弹窗窗口上调 show() 拉不起来（部件弹窗就是这种情况）。
        val keyboard = LocalSoftwareKeyboardController.current
        // 派生状态：IME inset 在键盘动画期间逐帧变化，只在「可见 / 不可见」翻转时触发组合。
        val imeInsets = WindowInsets.ime
        val imeVisible by remember(density, imeInsets) {
            derivedStateOf { imeInsets.getBottom(density) > 0 }
        }

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
                dismiss()
            }
        }

        // 进提醒视图 = 先收键盘，等 IME 真正隐藏后再换内容：
        // 键盘下滑（逐帧改 inset）+ 月历重排挤在同一段动画里，表现为底部键盘闪一下、切页卡一下。
        fun enterReminder() {
            if (imeVisible) {
                keyboard?.hide()
                pendingEnterReminder = true
            } else {
                editingReminder = true
            }
        }
        LaunchedEffect(pendingEnterReminder, imeVisible) {
            if (pendingEnterReminder && !imeVisible) {
                pendingEnterReminder = false
                editingReminder = true
            }
        }
        LaunchedEffect(editingReminder) {
            // 提醒视图没有任何输入框：清掉焦点，避免焦点游走后再次拉起键盘。
            if (editingReminder) focusManager.clearFocus(force = true)
        }

        // 自动聚焦 + 弹键盘。弹窗是独立窗口：首帧窗口还没有焦点，此时 requestFocus 拉不起 IME
        // （输入框有光标、键盘不弹）。所以先等 window focus 事件（不轮询），再请求焦点并显式 show。
        LaunchedEffect(Unit) {
            dialogView.awaitWindowFocus()
            focusRequester.requestFocus()
            keyboard?.show()
            withFrameNanos { }
            // 少数 ROM 首帧 show 被吞：改用窗口 insets 控制器再补一次，仍失败就等用户点输入框。
            if (imeInsets.getBottom(density) == 0) {
                (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                    WindowInsetsControllerCompat(window, dialogView)
                        .show(WindowInsetsCompat.Type.ime())
                }
            }
        }

        if (editingReminder) {
            TodoReminderEditor(
                initial = reminder,
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
                    PressIconButton(onClick = ::enterReminder) {
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
                shape = LargeShape,
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
                        .pressClickable(
                            enabled = existing?.done != true,
                            onClick = ::enterReminder,
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
            TodoDialogActions(
                deleteLabel = if (isEdit) stringResource(R.string.todo_delete) else null,
                onDelete = {
                    existing?.let { todo ->
                        scope.launch {
                            TodoCenter.deleteTodo(todo.id)
                            curOnChanged()
                            dismiss()
                        }
                    }
                },
                confirmLabel = stringResource(R.string.todo_save),
                confirmEnabled = trimmed.isNotEmpty(),
                onCancel = dismiss,
                onConfirm = { submit() },
            )
        }
    }
}

/**
 * 提醒编辑视图（同一张卡片内切换）：日期月历与 24 小时制表盘在**同一块固定高度区域**里切换。
 *
 * 用表盘而不是文本输入：提醒这一屏不需要键盘，切换页面时也就不会有
 * 「键盘下滑 + 内容重排」叠在一起；紧凑布局在小屏上不再需要滚动，表盘拖动也不会和滚动抢手势。
 * 已过去的时间禁止确定，并给出提示；[initial] 为空或已过期时默认「一小时后」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoReminderEditor(
    initial: TodoReminder?,
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
    var page by rememberSaveable(initial) { mutableStateOf(ReminderPage.Date) }
    val selected = TodoReminder(
        day = selectedDay,
        minuteOfDay = timeState.hour * 60 + timeState.minute,
    )
    val past = selected.isPast(now, zone)

    Text(
        text = stringResource(R.string.todo_reminder_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ReminderPage.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = page == entry,
                onClick = { page = entry },
                shape = SegmentedButtonDefaults.itemShape(index, ReminderPage.entries.size),
                // 不叠选中勾：选中态已由容器色表达（ui-guidelines §5.3），勾还会挤掉文字。
                icon = {},
            ) {
                Text(stringResource(entry.labelRes))
            }
        }
    }
    // 固定高度：两页同区域切换，卡片高度不变（不跳、不闪）。
    // 极小屏再按屏幕高度收一档：提醒视图不滚动，卡片不能超出屏幕（§3.2-8 用稳定值算上限）。
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val pickerHeight = minOf(ReminderPickerHeight, screenHeight * 0.4f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(pickerHeight),
        contentAlignment = Alignment.Center,
    ) {
        // M3 1.4 的 TimePicker 是「大号时间显示 + 表盘」一整块（自然尺寸约 272 × 372dp）：
        // 直接塞进固定区域会被压扁（表盘变椭圆、刻度重叠），所以整体等比缩放，保持方形。
        val pickerScale = minOf(
            maxWidth / TimePickerNaturalWidth,
            maxHeight / TimePickerNaturalHeight,
        )
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                fadeIn(tween(MotionSwapIn)) togetherWith fadeOut(tween(MotionSwapOut))
            },
            label = "todo-reminder-page",
        ) { target ->
            when (target) {
                ReminderPage.Date -> TodoDatePicker(
                    selectedDay = selectedDay,
                    todayEpochDay = todayEpochDay,
                    onSelect = { selectedDay = it },
                )

                // 必须显式指定纵向布局：默认布局类型在近方形容器里会选「横排」，
                // 变成大号时间框 + 被压扁的表盘（刻度重叠）。纵向 = 纯表盘，方形约束下才不椭。
                ReminderPage.Time -> Box(
                    modifier = Modifier.size(
                        TimePickerNaturalWidth * pickerScale,
                        TimePickerNaturalHeight * pickerScale,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(TimePickerNaturalWidth, TimePickerNaturalHeight)
                            .graphicsLayer {
                                scaleX = pickerScale
                                scaleY = pickerScale
                                transformOrigin = TransformOrigin.Center
                            },
                    ) {
                        TimePicker(state = timeState, layoutType = TimePickerLayoutType.Vertical)
                    }
                }
            }
        }
    }
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
    TodoDialogActions(
        deleteLabel = if (initial != null) stringResource(R.string.todo_reminder_remove) else null,
        // 删除提醒：清空数据并回到主视图（保存时随待办一起写回）。
        onDelete = { onConfirm(null) },
        confirmLabel = stringResource(R.string.todo_reminder_confirm),
        confirmEnabled = !past,
        onCancel = onDismiss,
        onConfirm = { onConfirm(selected) },
    )
}

/** M3 24 小时纵向 TimePicker 的自然尺寸（时间显示 + 表盘，实测约 272 × 372dp）。 */
private val TimePickerNaturalWidth = 272.dp
private val TimePickerNaturalHeight = 372.dp

/** 提醒视图的两页：日期月历 / 时间表盘（同一块区域，高度固定）。 */
private enum class ReminderPage(val labelRes: Int) {
    Date(R.string.todo_reminder_pick_date),
    Time(R.string.todo_reminder_pick_time),
}

/**
 * 弹窗底部操作行（主视图与提醒视图共用）：可选破坏性操作 + 取消 + 确认。
 *
 * 单一实现，避免两个视图各写一份几乎相同的 `Row`（宽窄、间距、禁用态口径会分叉）。
 */
@Composable
private fun TodoDialogActions(
    deleteLabel: String?,
    onDelete: () -> Unit,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (deleteLabel != null) {
            PressTextButton(onClick = onDelete) {
                Text(
                    text = deleteLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PressTextButton(onClick = onCancel) {
            Text(stringResource(hunoia.luno.design.R.string.cancel))
        }
        PressTextButton(onClick = onConfirm, enabled = confirmEnabled) {
            Text(confirmLabel)
        }
    }
}

/**
 * 紧凑月历：卡片内自绘 7 列 × N 行，窄屏也能完整显示。
 *
 * 单元格固定 [SubMinInteractiveSize] 见方（正圆角色，1:1 不被拉伸）：密排自绘命中区的下限档，
 * 相邻命中区不重叠、边界由圆形选中态区分（ui-guidelines §1.6）。
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
            PressIconButton(
                onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                enabled = visibleMonth > currentMonth,
                modifier = Modifier.size(SubMinInteractiveSize),
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
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
                color = scheme.onSurface,
            )
            PressIconButton(
                onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                modifier = Modifier.size(SubMinInteractiveSize),
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
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
                    // 单元格等宽（weight）内放一个固定见方的命中区：窄屏不挤压、宽屏不拉长圆。
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayOfMonth in 1..days) {
                            val epochDay = visibleMonth.atDay(dayOfMonth).toEpochDay()
                            DayCell(
                                dayOfMonth = dayOfMonth,
                                selected = epochDay == selectedDay,
                                isToday = epochDay == todayEpochDay,
                                selectable = epochDay >= todayEpochDay,
                                onClick = { onSelect(epochDay) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 月历单元格：选中 = 主色实心；今天 = 主色文字；今天之前 = 置灰不可点。 */
@Composable
private fun DayCell(
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
            .requiredSize(SubMinInteractiveSize)
            .clip(CircleShape)
            .then(if (selectable) Modifier.pressClickable(onClick = onClick) else Modifier)
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

/**
 * 等窗口真正拿到焦点。部件弹窗是新建的透明 Activity + Dialog 子窗口，
 * 首帧 requestFocus 时窗口还没有焦点，IME 请求会被丢掉（输入框有光标、键盘不弹）。
 */
private suspend fun View.awaitWindowFocus() {
    if (hasWindowFocus()) return
    suspendCancellableCoroutine { continuation ->
        val observer = viewTreeObserver
        val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(this)
                continuation.resume(Unit)
            }
        }
        observer.addOnWindowFocusChangeListener(listener)
        continuation.invokeOnCancellation {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
        }
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
