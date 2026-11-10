package hunoia.luno.gridpanel.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoReminderFormat
import hunoia.luno.gridpanel.ui.SnoozeDurationDialog
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.MinInteractiveSize
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 代办管理列表：主设置 → 代办。
 * 数据源 [TodoCenter.todoConfig]；切换完成 / 增删改走 domain 门面（配置写入，不走 Action）。
 * 单列合并（未完成在前、完成在后置灰划线）；添加/编辑都在弹窗完成（无独立编辑页）。
 * 完成 / 撤销完成都先走确认弹层，与桌面部件、面板内置待办同口径。
 */
@Composable
fun TodoListScreen(
    onBack: () -> Unit,
) {
    val config by TodoCenter.todoConfig.collectAsStateWithLifecycle(initialValue = TodoConfig())
    val scope = rememberCoroutineScope()
    val pending = config.todos.filter { !it.done }
    val done = config.todos.filter { it.done }
    val all = pending + done

    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    // 完成 / 撤销完成都先确认：与桌面部件、面板内置待办的确认口径一致。
    var pendingToggle by remember { mutableStateOf<TodoItem?>(null) }
    var showSnoozeDialog by remember { mutableStateOf(false) }

    // 待办部件的「标题 → 列表页」或「某条 → 编辑」请求：进入列表页后消费一次。
    // todoId 为空 = 只进列表页（不自动开弹窗）；非空 = 再打开该条编辑。
    val pendingOpen by TodoListRequests.open.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        val open = pendingOpen ?: return@LaunchedEffect
        TodoListRequests.consume()
        val todoId = open.todoId ?: return@LaunchedEffect
        // 从持久层读最新配置（collectAsState 初始为空，find 会落空 → 误判新建）。
        val target = TodoCenter.getTodoConfig().todoById(todoId)
        if (target != null) editingId = target.id
    }

    LunoScreenLazy(
        title = stringResource(R.string.todo_list_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = { creating = true },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.todo_add),
            )
        },
        dialogs = {
            // 同一时刻最多一个弹层（ui-guidelines §3.2-5）；确认后才写回完成态。
            pendingToggle?.let { todo ->
                ConfirmDialog(
                    title = stringResource(
                        if (todo.done) {
                            R.string.todo_uncomplete_confirm_title
                        } else {
                            R.string.todo_complete_confirm_title
                        },
                    ),
                    text = stringResource(
                        if (todo.done) {
                            R.string.todo_uncomplete_confirm_text
                        } else {
                            R.string.todo_complete_confirm_text
                        },
                        todo.text,
                    ),
                    confirmLabel = stringResource(
                        if (todo.done) {
                            R.string.todo_uncomplete_confirm_action
                        } else {
                            R.string.todo_complete_confirm_action
                        },
                    ),
                    onDismissRequest = { pendingToggle = null },
                    onConfirm = {
                        scope.launch { TodoCenter.toggleTodo(todo.id, !todo.done) }
                    },
                )
            }
            if (showSnoozeDialog) {
                SnoozeDurationDialog(
                    initialMinutes = config.reminderSnoozeMinutes,
                    onDismiss = { showSnoozeDialog = false },
                    onConfirm = { minutes ->
                        scope.launch { TodoCenter.setReminderSnoozeMinutes(minutes) }
                        showSnoozeDialog = false
                    },
                )
            }
        },
    ) {
        item {
            LunoGroup {
                LunoSettingsRow(
                    title = stringResource(R.string.todo_reminder_setting),
                    subtitle = TodoReminderFormat.snoozeDurationLabel(
                        LocalContext.current,
                        config.reminderSnoozeMinutes,
                    ),
                    onClick = { showSnoozeDialog = true },
                )
            }
        }
        if (config.todos.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.todo_empty))
            }
        } else {
            item {
                // 直接渲染行（镜像 HabitListScreen）：LunoGroup 的 ColumnScope 里逐行
                // forEachIndexed + segmentedShape，响应式正确（不用 LunoSegmentedGroup——
                // 它靠 composition 期间改 mutableListOf，刷新可能偶发失效）。
                LunoGroup {
                    all.forEachIndexed { index, todo ->
                        TodoListRow(
                            todo = todo,
                            shape = segmentedShape(index, all.size),
                            onToggle = { pendingToggle = todo },
                            onEdit = { editingId = todo.id },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TodoEditDialog(
            todoId = null,
            onDismiss = { creating = false },
        )
    }
    editingId?.let { id ->
        TodoEditDialog(
            todoId = id,
            onDismiss = { editingId = null },
        )
    }
}

/** 一行代办：左圆点切换完成（先确认）+ 文字（点行进编辑弹窗）+ 可选提醒徽标。 */
@Composable
private fun TodoListRow(
    todo: TodoItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveSize)
            .clip(shape)
            .background(scheme.surfaceBright)
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodoCheckToggle(
            done = todo.done,
            onToggle = onToggle,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = todo.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = if (todo.done) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 已完成的待办不再显示闹钟：提醒已被取消，显示未来时间会误导用户。
        todo.reminder?.takeIf { !todo.done }?.let { reminder ->
            val past = reminder.isPast(System.currentTimeMillis(), ZoneId.systemDefault())
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (past) scheme.error else scheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = TodoReminderFormat.label(LocalContext.current, reminder),
                style = MaterialTheme.typography.labelMedium,
                color = if (past) scheme.error else scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 完成切换圆点：未完成 = 空心描边；已完成 = 实心主色 + 白勾。点按只发切换请求，确认在列表页弹层。 */
@Composable
private fun TodoCheckToggle(
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val size = 24.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, scheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
}
