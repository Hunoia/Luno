package hunoia.luno.gridpanel.todo

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.input.WheelColumn
import hunoia.luno.ui.component.input.wheelNumber
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.component.sheet.AnchoredDialogContent
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.MotionContentIn
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume

/**
 * 待办编辑弹窗（应用内列表页 / 面板部件 / 桌面部件共用）。
 *
 * [todoId] 为空 = 新增；非空 = 编辑（预填文字与提醒，额外提供删除）。
 * 右上角闹钟图标 / 提醒摘要行把提醒选择器**内联展开**在卡片里：标题与输入框保持可见，
 * 不换页、不嵌套二级弹层（ui-guidelines §3.2-5 / §4.4）；展开内容是「日期 / 时 / 分」三列滚轮，
 * 提醒视图不需要键盘。已完成的待办隐藏闹钟入口。
 *
 * 窗口 / 遮罩 / 进出场动画由 [AnchoredDialog] 统一提供；写回 [TodoCenter] 后先播退场再关闭，
 * [onChanged] 在写成功后调用（桌面部件用来立即刷新）。
 */
@Composable
fun TodoEditDialog(
    todoId: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
    // true = 宿主是透明 Activity（部件入口），弹层直接铺在 Activity 窗口里，不再套 Compose Dialog。
    inlineHost: Boolean = false,
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
    var reminderExpanded by rememberSaveable(todoId) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isEdit = existing != null
    val trimmed = textValue.text.trim()
    val focusRequester = remember { FocusRequester() }
    val curOnChanged by rememberUpdatedState(onChanged)
    // 卡片高度上限取稳定值（屏幕高），不取 imePadding 内的实时高度：
    // 键盘动画期间后者逐帧变小，卡片会跟着逐帧重排（§3.2-8）。键盘占位由 imePadding 的父约束夹住。
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp.dp - PageGutter * 2)
        .coerceAtLeast(240.dp)
    val mainScroll = rememberScrollState()

    val content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = { dismiss ->
        val density = LocalDensity.current
        val dialogView = LocalView.current
        val hostContext = LocalContext.current
        val imeInsets = WindowInsets.ime

        fun submit() {
            if (trimmed.isEmpty()) return
            // 不收键盘：关窗时 AnchoredDialog 统一 hide + 等 IME 落定，键盘和卡片同一段退场。
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

        // 展开提醒不打断输入态：不收键盘、不清焦点（曾试着等 IME 收起再展开，键盘下滑和卡片
        // 长高叠在一起更卡）。键盘在场时卡片整体被 imePadding 顶到键盘上方，超高由卡片内滚动兜底。
        fun expandReminder() {
            reminderExpanded = true
        }

        // 自动聚焦 + 弹键盘。焦点要提前要：等 window focus 再 requestFocus，
        // 键盘就比卡片晚一两帧，看起来就是「先出弹窗、再弹键盘」。透明 Activity 宿主不再
        // 依赖窗口的 ALWAYS_VISIBLE（首次聚焦时输入框还没有编辑会话，系统会忽略）。
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            dialogView.awaitWindowFocus()
            withFrameNanos { }
            // 少数 ROM 首帧拉起被吞：用窗口 insets 控制器再补一次，仍失败就等用户点输入框。
            // Dialog 宿主取 Dialog 窗口；透明 Activity 宿主没有 DialogWindowProvider，回退到 Activity 窗口。
            if (imeInsets.getBottom(density) == 0) {
                val hostWindow = (dialogView.parent as? DialogWindowProvider)?.window
                    ?: (hostContext as? Activity)?.window
                if (hostWindow != null) {
                    WindowInsetsControllerCompat(hostWindow, dialogView)
                        .show(WindowInsetsCompat.Type.ime())
                }
            }
        }

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
                PressIconButton(
                    onClick = {
                        if (reminderExpanded) reminderExpanded = false else expandReminder()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = stringResource(
                            when {
                                reminderExpanded -> R.string.todo_reminder_collapse
                                reminder == null -> R.string.todo_reminder_add
                                else -> R.string.todo_reminder_edit
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
        // 展开 / 收起：内容直接换，卡片高度由 cardModifier 的 animateContentSize 过渡（不做二级弹层）。
        if (reminderExpanded) {
            TodoReminderWheels(
                initial = reminder,
                onCancel = { reminderExpanded = false },
                onConfirm = {
                    reminder = it
                    reminderExpanded = false
                },
            )
        } else {
            reminder?.let {
                ReminderSummary(
                    reminder = it,
                    enabled = existing?.done != true,
                    onClick = ::expandReminder,
                )
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

    // 展开提醒后卡片变高（大字体下更高），统一保留滚动兜底；滚轮自己是嵌套滚动的子节点，
    // 拖动优先由滚轮消费，不会和卡片的 verticalScroll 抢手势。
    // 高度过渡用 animateContentSize 而不是 AnimatedContent 的 SizeTransform：后者在
    // verticalScroll 的无限高度约束下会把新内容按「当前动画高度」测量，卡片停在半程、
    // 子项被压成 0 高后互相叠在一起（实机复现过）。
    val cardModifier = Modifier
        .heightIn(max = maxCardHeight)
        .animateContentSize(animationSpec = tween(MotionContentIn))
        .verticalScroll(mainScroll)

    if (inlineHost) {
        AnchoredDialogContent(
            onDismiss = onDismiss,
            contentPadding = PaddingValues(PageGutter),
            cardModifier = cardModifier,
            startOnIme = true,
            content = content,
        )
    } else {
        AnchoredDialog(
            onDismiss = onDismiss,
            // 进场就让窗口自己拉起键盘：和卡片上滑同一段动画（见 AnchoredDialog 的 imeAutoShow）。
            imeAutoShow = true,
            contentPadding = PaddingValues(PageGutter),
            cardModifier = cardModifier,
            content = content,
        )
    }
}

/**
 * 提醒摘要行（收起态）：图标 + 「今天 10:00」+ 展开箭头，点行展开滚轮。
 *
 * 与闹钟图标同一入口；命中区按 [MinInteractiveSize]（可点行的默认可访问性基线，
 * 不是 §4.8 的紧凑工具条例外）。
 */
@Composable
private fun ReminderSummary(
    reminder: TodoReminder,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val past = reminder.isPast(System.currentTimeMillis(), ZoneId.systemDefault())
    val label = TodoReminderFormat.dialogSummary(
        LocalContext.current.applicationContext,
        reminder,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveSize)
            .pressClickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (past) scheme.error else scheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (past) scheme.error else scheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = scheme.onSurfaceVariant,
        )
    }
}

/**
 * 提醒选择器（展开态）：日期 / 时 / 分三列滚轮 + 状态行 + 操作行，全部在同一张卡片里。
 *
 * 日期列只给未来 [ReminderDayCount] 天（今天起），所以「选到过去」只能来自今天 + 已过的钟点；
 * 该情况保留原有校验：状态行转红、确定禁用。已过期或超出一年的旧提醒不作初值，默认「一小时后」。
 */
@Composable
private fun TodoReminderWheels(
    initial: TodoReminder?,
    onCancel: () -> Unit,
    onConfirm: (TodoReminder?) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    // 每次重组重取「现在」：弹窗停留几分钟后，过期校验仍按真实时间判断。
    val now = System.currentTimeMillis()
    val todayEpochDay = LocalDate.now(zone).toEpochDay()
    val days = remember(todayEpochDay) {
        (0 until ReminderDayCount).map { todayEpochDay + it }
    }
    val hours = remember { (0 until 24).toList() }
    val minutes = remember { (0 until 60).toList() }
    val fallback = remember(days) {
        val oneHourLater = LocalDateTime.now(zone).plusHours(1)
        TodoReminder(
            day = oneHourLater.toLocalDate().toEpochDay().coerceIn(days.first(), days.last()),
            minuteOfDay = oneHourLater.hour * 60 + oneHourLater.minute,
        )
    }
    // 已过期的旧提醒不作为初值：一进展开态就卡在「不能确定」没有意义，直接给「一小时后」；
    // 原提醒仍在收起态的摘要行显示「已过期」，取消即可保留。
    val seed = initial
        ?.takeIf { !it.isPast(now, zone) && it.day in days.first()..days.last() }
        ?: fallback
    var day by rememberSaveable(initial) { mutableLongStateOf(seed.day) }
    var minuteOfDay by rememberSaveable(initial) { mutableIntStateOf(seed.minuteOfDay) }
    val selected = TodoReminder(day = day, minuteOfDay = minuteOfDay)
    val selectedHour = selected.hour
    val selectedMinute = selected.minute
    val past = selected.isPast(now, zone)

    val todayLabel = stringResource(R.string.todo_reminder_day_today)
    val tomorrowLabel = stringResource(R.string.todo_reminder_day_tomorrow)
    val dayLabel = remember(todayEpochDay, todayLabel, tomorrowLabel, context) {
        { epochDay: Long ->
            when (epochDay - todayEpochDay) {
                0L -> todayLabel
                1L -> tomorrowLabel
                else -> {
                    val date = LocalDate.ofEpochDay(epochDay)
                    context.getString(
                        R.string.todo_reminder_day_date,
                        date.monthValue,
                        date.dayOfMonth,
                    )
                }
            }
        }
    }

    Text(
        text = stringResource(R.string.todo_reminder_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(
            values = days,
            initialIndex = (day - todayEpochDay).toInt().coerceIn(0, days.lastIndex),
            onSelected = { day = days[it] },
            label = dayLabel,
            modifier = Modifier.weight(1.6f),
        )
        WheelColumn(
            values = hours,
            initialIndex = selectedHour,
            onSelected = { hour -> minuteOfDay = hour * 60 + selectedMinute },
            label = ::wheelNumber,
            modifier = Modifier.weight(1f),
        )
        WheelColumn(
            values = minutes,
            initialIndex = selectedMinute,
            onSelected = { minute -> minuteOfDay = selectedHour * 60 + minute },
            label = ::wheelNumber,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = if (past) {
            stringResource(R.string.todo_reminder_expired)
        } else {
            TodoReminderFormat.dialogStatus(context.applicationContext, selected)
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
        // 删除提醒：清空数据并收起（保存时随待办一起写回）。
        onDelete = { onConfirm(null) },
        confirmLabel = stringResource(R.string.todo_reminder_confirm),
        confirmEnabled = !past,
        onCancel = onCancel,
        onConfirm = { onConfirm(selected) },
    )
}

/** 日期列向前给多少天：一年足够覆盖「写个待办顺便定个日子」，再多滚轮也不好找。 */
private const val ReminderDayCount = 365

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
 * 等窗口真正拿到焦点。部件弹窗是新建的透明 Activity + Dialog 子窗口，
 * 首帧 requestFocus 时窗口还没有焦点，IME 请求会被丢掉（输入框有光标、键盘不弹）。
 */
private suspend fun View.awaitWindowFocus() {
    if (hasWindowFocus()) return
    suspendCancellableCoroutine { continuation ->
        val observer = viewTreeObserver
        val listener: ViewTreeObserver.OnWindowFocusChangeListener =
            object : ViewTreeObserver.OnWindowFocusChangeListener {
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
