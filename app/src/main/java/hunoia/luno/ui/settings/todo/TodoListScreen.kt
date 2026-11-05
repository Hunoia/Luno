package hunoia.luno.ui.settings.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.MinInteractiveSize
import kotlinx.coroutines.launch

/**
 * 代办管理列表：主设置 → 代办。
 * 数据源 [TodoCenter.todoConfig]；切换完成 / 增删改走 domain 门面（配置写入，不走 Action）。
 * 未完成在前，已完成在后（勾选即完成，点行进编辑页）。
 */
@Composable
fun TodoListScreen(
    onBack: () -> Unit,
    onEditTodo: (todoId: String?) -> Unit,
) {
    val config by TodoCenter.todoConfig.collectAsStateWithLifecycle(initialValue = TodoConfig())
    val scope = rememberCoroutineScope()
    val pending = config.todos.filter { !it.done }
    val done = config.todos.filter { it.done }

    LunoScreenLazy(
        title = stringResource(R.string.todo_list_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = { onEditTodo(null) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.todo_add),
            )
        },
    ) {
        if (config.todos.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.todo_empty))
            }
        } else {
            if (pending.isNotEmpty()) {
                item {
                    LunoGroup(title = stringResource(R.string.todo_group_pending)) {
                        LunoSegmentedGroup {
                            pending.forEach { todo ->
                                item { shape ->
                                    TodoListRow(
                                        todo = todo,
                                        onToggle = { scope.launch { TodoCenter.toggleTodo(todo.id, true) } },
                                        onEdit = { onEditTodo(todo.id) },
                                        shape = shape,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (done.isNotEmpty()) {
                item {
                    LunoGroup(title = stringResource(R.string.todo_group_done)) {
                        LunoSegmentedGroup {
                            done.forEach { todo ->
                                item { shape ->
                                    TodoListRow(
                                        todo = todo,
                                        onToggle = { scope.launch { TodoCenter.toggleTodo(todo.id, false) } },
                                        onEdit = { onEditTodo(todo.id) },
                                        shape = shape,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一行代办：左圆点切换完成 + 文字（点行进编辑页）；已完成 = 实心勾 + 置灰划线。 */
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
    }
}

/** 完成切换圆点：未完成 = 空心描边；已完成 = 实心主色 + 白勾。 */
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
