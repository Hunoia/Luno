package hunoia.luno.gridpanel.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 代办执行面：主页「代办」tab 嵌入用。
 *
 * 共享主页外壳的顶栏（右上齿轮 = 待办设置页），没有自己的顶栏 / 返回键 / FAB；
 * 新建入口是底栏的功能球，空态里的按钮走 [onNewTodo]。
 * 数据源 [TodoCenter.todoConfig]；切换 / 增删改走 domain 门面（配置写入，不走 Action）。
 *
 * 版式（页面自有，见 ui-guidelines §4.1 例外记录）：
 * - 顶部一行进度摘要 + 进度线（无卡片）；
 * - 未完成按紧迫度分区（已过期 → 今天 → 即将 → 其他），只有一个分区时不画分区头；
 * - 每行一张独立卡，提醒在第二行；未完成行右滑完成 / 左滑删除，已完成行只左滑删除；
 * - 已完成组默认收起，展开后点行可原地展开全文（不改文案）。
 *
 * 完成 / 撤销完成即时生效（不再弹确认层）——这是待办页与桌面部件 / 面板内置待办
 * 「先确认」口径的**有意分叉**，记录见 ui-guidelines §4.1 例外条目。
 *
 * [bottomContentPadding] / [undoBarBottomInset] 由宿主按停靠栏几何传入：
 * 页面内容画在 Scaffold body 里，底栏是叠在上面的兄弟节点，不抬高就会压住撤销条。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoListContent(
    topPadding: Dp,
    addRequest: Int,
    onAddRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
    onNewTodo: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = FloatingContentBottom,
    undoBarBottomInset: Dp = 12.dp,
) {
    val config by TodoCenter.todoConfig.collectAsStateWithLifecycle(initialValue = TodoConfig())
    val scope = rememberCoroutineScope()
    // 分钟级时钟：跨天 / 提醒到点后紧迫度与提醒文案要自己刷新，不能等下一次无关重组。
    val now = rememberNow()
    val groups = remember(config.todos, now) {
        groupPending(config.todos.filterNot { it.done }, now)
    }
    val pendingCount = groups.sumOf { it.todos.size }
    val overdueCount = groups.firstOrNull { it.urgency == TodoUrgency.Overdue }?.todos?.size ?: 0
    val sectionHeadersVisible = showSectionHeaders(groups)
    // 完成组：按完成时间倒序（刚完成的在上）；无完成时间排最后。
    val done = remember(config.todos) {
        config.todos.filter { it.done }
            .sortedByDescending { it.completedAt ?: Long.MIN_VALUE }
    }

    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    // 已完成组默认收起（计划定稿）；会话内记忆，重进页保持。
    var doneExpanded by rememberSaveable { mutableStateOf(false) }
    // 已完成行一次只展开一条，点行切换；展开后显示全文与完成时间。
    var expandedDoneId by rememberSaveable { mutableStateOf<String?>(null) }
    // 待撤销的动作：完成 / 删除即时生效后弹出撤销条，4s 自动消失；同一时刻一条。
    var undo by remember { mutableStateOf<TodoUndo?>(null) }

    // 撤销条计时：按「当前动作实例」重来，同文案的第二次动作不会沿用上一次的剩余时间。
    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        delay(UndoBarDurationMs)
        if (undo === action) undo = null
    }

    fun complete(todo: TodoItem) {
        scope.launch {
            TodoCenter.toggleTodo(todo.id, true)
            undo = TodoUndo(text = todo.text, kind = TodoUndoKind.Complete) {
                scope.launch { TodoCenter.toggleTodo(todo.id, false) }
            }
        }
    }

    fun delete(todo: TodoItem) {
        scope.launch {
            TodoCenter.deleteTodo(todo.id)
            undo = TodoUndo(text = todo.text, kind = TodoUndoKind.Delete) {
                scope.launch { TodoCenter.addTodo(todo) }
            }
        }
    }

    // 点勾：完成（即时 + 撤销条）；撤销完成也即时（再点一次即可重做，无需撤销条）。
    fun onToggle(todo: TodoItem) {
        if (todo.done) {
            scope.launch { TodoCenter.toggleTodo(todo.id, false) }
        } else {
            complete(todo)
        }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(
                start = PageGutter,
                top = topPadding,
                end = PageGutter,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (config.todos.isEmpty()) {
                item(key = "empty") {
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
                item(key = "summary") {
                    TodoProgressHeader(
                        pending = pendingCount,
                        done = done.size,
                        overdue = overdueCount,
                    )
                }
                groups.forEach { group ->
                    if (sectionHeadersVisible) {
                        stickyHeader(key = "section_${group.urgency}") {
                            TodoSectionHeader(
                                title = urgencyTitle(group.urgency),
                                count = group.todos.size,
                                error = group.urgency == TodoUrgency.Overdue,
                            )
                        }
                    }
                    items(group.todos, key = { "pending_${it.id}" }) { todo ->
                        TodoCard(
                            todo = todo,
                            nowMillis = now,
                            onToggle = { onToggle(todo) },
                            onEdit = { editingId = todo.id },
                            onComplete = { complete(todo) },
                            onDelete = { delete(todo) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done_header") {
                        TodoSectionHeader(
                            title = stringResource(R.string.todo_group_done_plain),
                            count = done.size,
                            onClick = { doneExpanded = !doneExpanded },
                            expanded = doneExpanded,
                        )
                    }
                    if (doneExpanded) {
                        items(done, key = { "done_${it.id}" }) { todo ->
                            TodoCard(
                                todo = todo,
                                nowMillis = now,
                                onToggle = { onToggle(todo) },
                                onDelete = { delete(todo) },
                                expanded = expandedDoneId == todo.id,
                                onToggleExpanded = {
                                    expandedDoneId = if (expandedDoneId == todo.id) null else todo.id
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        // 撤销条浮在停靠栏上方（底部 inset 由宿主给），不占列表布局。
        val undoAction = undo
        AnimatedVisibility(
            visible = undoAction != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = PageGutter, end = PageGutter, bottom = undoBarBottomInset),
            enter = fadeIn(tween(MotionSubtle)) + slideInVertically(tween(MotionSubtle)) { it / 2 },
            exit = fadeOut(tween(MotionSubtle)) + slideOutVertically(tween(MotionSubtle)) { it / 2 },
        ) {
            // 退出动画期间 undoAction 已经是 null，用最后一次非空值把内容留住。
            var shown by remember { mutableStateOf(undoAction) }
            LaunchedEffect(undoAction) {
                if (undoAction != null) shown = undoAction
            }
            shown?.let { action ->
                TodoUndoBar(
                    message = stringResource(
                        when (action.kind) {
                            TodoUndoKind.Complete -> R.string.todo_undo_complete
                            TodoUndoKind.Delete -> R.string.todo_undo_delete
                        },
                        action.text,
                    ),
                    onUndo = {
                        action.onUndo()
                        undo = null
                    },
                )
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

/** 待撤销动作的文案类型：完成 / 删除。文案在渲染时用 `stringResource` 取，避免持有 context。 */
private enum class TodoUndoKind { Complete, Delete }

/** 待撤销的动作：待办文案 + 动作类型 + 恢复回调（完成 → 恢复未完成；删除 → 重新加入）。 */
private class TodoUndo(
    val text: String,
    val kind: TodoUndoKind,
    val onUndo: () -> Unit,
)

/** 分区头文案（按紧迫度）；无提醒归到「其他」，不叫「未完成」（整页都是未完成）。 */
@Composable
private fun urgencyTitle(urgency: TodoUrgency): String = when (urgency) {
    TodoUrgency.Overdue -> stringResource(R.string.todo_group_overdue)
    TodoUrgency.Today -> stringResource(R.string.todo_group_today)
    TodoUrgency.Upcoming -> stringResource(R.string.todo_group_upcoming)
    TodoUrgency.NoReminder -> stringResource(R.string.todo_group_other)
}

/**
 * 当前时刻（分钟对齐）：`System.currentTimeMillis()` 不是 State，直接读会让
 * 跨天 / 提醒到点后的分组与文案停在上一次重组。
 */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val toNextMinute = MinuteMillis - (System.currentTimeMillis() % MinuteMillis)
            delay(toNextMinute.coerceAtLeast(1_000L))
            now = System.currentTimeMillis()
        }
    }
    return now
}

private const val MinuteMillis = 60_000L

/** 撤销条自动消失时长（ms）。 */
private const val UndoBarDurationMs = 4_000L
