package hunoia.luno.gridpanel.todo

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
 * 输入框下方是闹钟图标 / 提醒入口：点一下把「日期 / 时 / 分」三列滚轮**内联展开**在卡片里，
 * 再点一次确认并收起；不换页、不嵌套二级弹层（ui-guidelines §3.2-5 / §4.4）。
 * 主取消 / 保存操作行始终位于卡片底部；已完成的待办隐藏闹钟入口。
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
    var reminderDraft by rememberSaveable(todoId, stateSaver = ReminderSaver) {
        mutableStateOf<TodoReminder?>(null)
    }

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
            val targetReminder = if (reminderExpanded) reminderDraft ?: reminder else reminder
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
        fun toggleReminder() {
            if (reminderExpanded) {
                // 第二次点闹钟 = 确认当前滚轮选择并收起；没收到滚轮回调就保留原值。
                reminderDraft?.let { reminder = it }
                reminderDraft = null
                reminderExpanded = false
            } else {
                reminderDraft = null
                reminderExpanded = true
            }
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
        // 已完成的待办不提醒：隐藏闹钟入口。
        if (existing?.done != true) {
            ReminderEntryRow(
                expanded = reminderExpanded,
                reminder = reminder,
                draft = reminderDraft,
                onToggle = { toggleReminder() },
                onClear = {
                    reminder = null
                    reminderDraft = null
                    reminderExpanded = false
                },
            )
        }
        // 展开 / 收起：滚轮留在原卡里，主操作行始终是底部的取消 / 保存。
        if (reminderExpanded) {
            TodoReminderWheels(
                initial = reminder,
                onSelectionChange = { reminderDraft = it },
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
 * 提醒入口（输入框下方）：闹钟图标 + 收起态时间 / 展开态滚轮当前值 + 清除按钮。
 *
 * 点图标展开；展开后再点 = 确认当前滚轮值并收起。展开态给图标加浅色圆底，
 * 表示“这次点击会确认并收起”，不换图标。
 */
@Composable
private fun ReminderEntryRow(
    expanded: Boolean,
    reminder: TodoReminder?,
    draft: TodoReminder?,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val shown = if (expanded) draft ?: reminder else reminder
    val past = shown?.isPast(System.currentTimeMillis(), ZoneId.systemDefault()) == true
    val label = shown?.let {
        TodoReminderFormat.dialogSummary(context.applicationContext, it)
    } ?: stringResource(R.string.todo_reminder_entry)
    val labelColor = when {
        past -> scheme.error
        shown != null -> scheme.onSurface
        else -> scheme.onSurfaceVariant
    }
    val iconTint = if (expanded || shown != null) scheme.primary else scheme.onSurfaceVariant
    val toggleLabel = stringResource(
        when {
            expanded -> R.string.todo_reminder_confirm_collapse
            reminder == null -> R.string.todo_reminder_add
            else -> R.string.todo_reminder_edit
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = MinInteractiveSize)
                .pressClickable(onClickLabel = toggleLabel, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (expanded) scheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
            )
        }
        if (reminder != null) {
            PressIconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.todo_reminder_clear),
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 提醒选择器（展开态）：日期 / 时 / 分三列滚轮，全部在同一张卡片里。
 *
 * 今天只列未来时间：时列从当前小时开始，分列在当前小时只给下一分钟起；
 * 今天已经没有未来分钟时日期列直接从明天开始。滚轮本身滚不到过去，
 * 不再需要单独的“已过期”状态行或确认校验。
 */
@Composable
private fun TodoReminderWheels(
    initial: TodoReminder?,
    onSelectionChange: (TodoReminder) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    // 展开时取一次“现在”：滚轮列不随后续分钟跳动收缩；本次弹窗内选到的时间始终是未来。
    val nowDateTime = remember { LocalDateTime.now(zone) }
    val nowMillis = remember(nowDateTime) { nowDateTime.atZone(zone).toInstant().toEpochMilli() }
    val todayEpochDay = nowDateTime.toLocalDate().toEpochDay()
    val earliestTodayMinute = nowDateTime.hour * 60 + nowDateTime.minute + 1
    val todayHasFuture = earliestTodayMinute < TodoReminder.MinutesPerDay
    val firstDay = if (todayHasFuture) todayEpochDay else todayEpochDay + 1
    val days = remember(firstDay) { (0 until ReminderDayCount).map { firstDay + it } }
    val fallback = remember(nowDateTime, days) {
        val oneHourLater = nowDateTime.plusHours(1)
        TodoReminder(
            day = oneHourLater.toLocalDate().toEpochDay().coerceIn(days.first(), days.last()),
            minuteOfDay = oneHourLater.hour * 60 + oneHourLater.minute,
        )
    }
    // 已过期或超出一年的旧提醒不作初值；收起态仍显示“已过期”，展开后默认“一小时后”。
    val seed = initial
        ?.takeIf { !it.isPast(nowMillis, zone) && it.day in days.first()..days.last() }
        ?: fallback
    var day by rememberSaveable(initial, firstDay) { mutableLongStateOf(seed.day) }
    var minuteOfDay by rememberSaveable(initial, firstDay) { mutableIntStateOf(seed.minuteOfDay) }
    val firstHour = earliestTodayMinute / 60
    val firstMinute = earliestTodayMinute % 60
    val effectiveMinuteOfDay = if (day == todayEpochDay) {
        minuteOfDay.coerceAtLeast(earliestTodayMinute)
            .coerceAtMost(TodoReminder.MinutesPerDay - 1)
    } else {
        minuteOfDay.coerceIn(0, TodoReminder.MinutesPerDay - 1)
    }
    val selected = TodoReminder(day = day, minuteOfDay = effectiveMinuteOfDay)
    val selectedHour = selected.hour
    val selectedMinute = selected.minute
    val hours = remember(day, todayEpochDay, firstHour) {
        if (day == todayEpochDay) (firstHour..23).toList() else (0..23).toList()
    }
    val minutes = remember(day, todayEpochDay, selectedHour, firstHour, firstMinute) {
        if (day == todayEpochDay && selectedHour == firstHour) {
            (firstMinute..59).toList()
        } else {
            (0..59).toList()
        }
    }
    // 日期切回今天时把选中值夹到第一个合法分钟；显示与回调都用 effective 值。
    LaunchedEffect(effectiveMinuteOfDay) {
        if (effectiveMinuteOfDay != minuteOfDay) minuteOfDay = effectiveMinuteOfDay
    }
    LaunchedEffect(selected) { onSelectionChange(selected) }

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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        key(firstDay) {
            WheelColumn(
                values = days,
                initialIndex = days.indexOf(day).coerceAtLeast(0),
                onSelected = { day = days[it] },
                label = dayLabel,
                modifier = Modifier.weight(1.6f),
            )
        }
        key(day) {
            WheelColumn(
                values = hours,
                initialIndex = hours.indexOf(selectedHour).coerceAtLeast(0),
                onSelected = { hour -> minuteOfDay = hours[hour] * 60 + selectedMinute },
                label = ::wheelNumber,
                modifier = Modifier.weight(1f),
            )
        }
        key(day, selectedHour) {
            WheelColumn(
                values = minutes,
                initialIndex = minutes.indexOf(selectedMinute).coerceAtLeast(0),
                onSelected = { minute -> minuteOfDay = selectedHour * 60 + minutes[minute] },
                label = ::wheelNumber,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
