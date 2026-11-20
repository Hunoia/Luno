package hunoia.luno.gridpanel.todo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoReminderFormat
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 代办执行面：主页「代办」tab 嵌入用。
 *
 * 共享主页外壳的顶栏（右上齿轮 = 待办设置页），没有自己的顶栏 / 返回键 / FAB；
 * 新建入口是底栏的功能球，空态里的按钮走 [onNewTodo]。
 * 数据源 [TodoCenter.todoConfig]；切换完成 / 增删改走 domain 门面（配置写入，不走 Action）。
 *
 * 与「设置」彻底分开：设置项（推迟时长 / 保留天数 / 清空）在 [TodoSettingsScreen]。
 * 两组：未完成（保持创建顺序）+ 已完成（按完成时间倒序、默认收起、可展开）。
 * 完成 / 撤销完成都先走确认弹层，与桌面部件、面板内置待办同口径。
 */
@Composable
fun TodoListContent(
    topPadding: Dp,
    addRequest: Int,
    onAddRequestHandled: () -> Unit,
    onNewTodo: () -> Unit = {},
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val config by TodoCenter.todoConfig.collectAsStateWithLifecycle(initialValue = TodoConfig())
    val scope = rememberCoroutineScope()
    val pending = config.todos.filter { !it.done }
    // 完成组：按完成时间倒序（刚完成的在上）；无完成时间排最后。
    val done = config.todos.filter { it.done }
        .sortedByDescending { it.completedAt ?: Long.MIN_VALUE }

    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    // 完成 / 撤销完成都先确认：与桌面部件、面板内置待办的确认口径一致。
    var pendingToggle by remember { mutableStateOf<TodoItem?>(null) }
    // 已完成组默认收起（计划定稿）；会话内记忆，重进页保持。
    var doneExpanded by rememberSaveable { mutableStateOf(false) }

    // 功能球的「添加」：调用方把请求数 +1，这里消费一次后归零，避免重进 tab 又弹窗。
    LaunchedEffect(addRequest) {
        if (addRequest > 0) {
            creating = true
            onAddRequestHandled()
        }
    }

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            start = PageGutter,
            top = topPadding,
            end = PageGutter,
            bottom = FloatingContentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        if (config.todos.isEmpty()) {
            item {
                EmptyState(
                    message = stringResource(R.string.todo_empty),
                    action = {
                        PressTextButton(onClick = onNewTodo) {
                            Text(stringResource(R.string.todo_add))
                        }
                    },
                )
            }
        } else {
            if (pending.isNotEmpty()) {
                item {
                    LunoGroup(title = stringResource(R.string.todo_group_pending)) {
                        pending.forEachIndexed { index, todo ->
                            TodoListRow(
                                todo = todo,
                                shape = segmentedShape(index, pending.size),
                                onToggle = { pendingToggle = todo },
                                onEdit = { editingId = todo.id },
                            )
                        }
                    }
                }
            }
            if (done.isNotEmpty()) {
                item {
                    DoneGroup(
                        done = done,
                        expanded = doneExpanded,
                        onExpandedChange = { doneExpanded = it },
                        onToggle = { pendingToggle = it },
                    )
                }
            }
        }
    }

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

/**
 * 已完成组：可展开的头行 + 展开后的完成行。
 * 头行点按只展开 / 收起；行内点按仍进编辑 / 撤销完成确认。
 */
@Composable
private fun DoneGroup(
    done: List<TodoItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggle: (TodoItem) -> Unit,
) {
    // 头行 + 展开后的 N 行共用一个分段块（ui-guidelines §4.1 可展开组行）。
    // 已完成行只供回看 + 撤销完成，不再进编辑弹窗（编辑会改字又绕过完成时间）。
    val totalRows = if (expanded) done.size + 1 else 1
    LunoGroup {
        DoneHeaderRow(
            count = done.size,
            expanded = expanded,
            shape = segmentedShape(0, totalRows),
            onClick = { onExpandedChange(!expanded) },
        )
        if (expanded) {
            done.forEachIndexed { index, todo ->
                TodoListRow(
                    todo = todo,
                    shape = segmentedShape(index + 1, totalRows),
                    onToggle = { onToggle(todo) },
                )
            }
        }
    }
}

/** 已完成组头行：标题 + 数量 + 展开箭头（180ms 旋转，MotionSubtle）。 */
@Composable
private fun DoneHeaderRow(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "doneChevron",
    )
    // 整行只做展开 / 收起：无障碍给出动作名，而不是笼统的「双击激活」。
    val toggleLabel = stringResource(
        if (expanded) R.string.todo_group_collapse else R.string.todo_group_expand,
    )
    LunoGroupItem(
        shape = shape,
        dividerStartInset = PageGutter,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .pressClickable(onClickLabel = toggleLabel, onClick = onClick)
                .padding(horizontal = PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.todo_group_done, count),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一行代办：左圆点切换完成（先确认）+ 文字 + 可选提醒徽标。
 * [onEdit] 非空时点行进编辑弹窗；null（已完成行）时行不可点，只留圆点撤销。
 */
@Composable
private fun TodoListRow(
    todo: TodoItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    onEdit: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // 大卡分割线风格：行不画自身底色/圆角，由 LunoGroup 的卡片统一托底，
    // 仅非首行顶部画分割线（与 LunoRow 同口径）。分割线左起点越过左侧圆点，
    // 对齐文字列（行内边距 16dp + 48dp 命中区 + 间距 8dp）。
    val dividerStartInset = 16.dp + MinInteractiveSize + 8.dp
    LunoGroupItem(
        shape = shape,
        dividerStartInset = dividerStartInset,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .then(
                    if (onEdit != null) {
                        Modifier.pressClickable(onClick = onEdit)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodoCheckToggle(
                done = todo.done,
                onToggle = onToggle,
            )
            Spacer(Modifier.width(8.dp))
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
}

/**
 * 完成切换圆点：48dp 命中区（视觉 24dp）。未完成 = 空心描边；已完成 = 实心主色 + 白勾。
 * 点按只发切换请求，确认在列表页弹层。
 */
@Composable
private fun TodoCheckToggle(
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val size = 24.dp
    val label = stringResource(
        if (done) {
            R.string.todo_uncomplete_confirm_title
        } else {
            R.string.todo_complete_confirm_title
        },
    )
    Box(
        modifier = modifier
            .size(MinInteractiveSize)
            .clip(CircleShape)
            .pressClickable(onClickLabel = label, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                Modifier
                    .size(size)
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
                    .size(size)
                    .border(2.dp, scheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
}
