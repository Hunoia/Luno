package hunoia.luno.gridpanel.todo

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.gridpanel.R
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch

/**
 * 代办管理列表：主设置 → 代办。
 * 数据源 [TodoCenter.todoConfig]；切换完成 / 增删改走 domain 门面（配置写入，不走 Action）。
 * 单列合并（未完成在前、完成在后置灰划线）；添加/编辑都在弹窗完成（无独立编辑页）。
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
    var editing by remember { mutableStateOf<TodoItem?>(null) }

    // 代办面板部件的「添加/编辑」请求：进入列表页后自动开对应弹窗（消费一次即清）。
    // 已在列表页时收到新请求（open 从 null 变非空）也会再次触发。
    val pendingOpen by TodoListRequests.open.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        val open = pendingOpen ?: return@LaunchedEffect
        TodoListRequests.consume()
        if (open.todoId == null) {
            // 新增。
            creating = true
        } else {
            // 编辑：从持久层读最新配置（collectAsState 初始为空，find 会落空 → 误判新增）。
            val target = TodoCenter.getTodoConfig().todoById(open.todoId)
            if (target != null) {
                editing = target
            } else {
                // 指定 id 已不存在（被删）→ 回退为新增。
                creating = true
            }
        }
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
    ) {
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
                            onToggle = {
                                scope.launch { TodoCenter.toggleTodo(todo.id, !todo.done) }
                            },
                            onEdit = { editing = todo },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TodoEditDialog(
            isNew = true,
            onDismiss = { creating = false },
            onConfirm = { text ->
                scope.launch {
                    TodoCenter.addTodo(
                        TodoItem(text = text, createdAt = System.currentTimeMillis()),
                    )
                    creating = false
                }
            },
        )
    }
    editing?.let { todo ->
        TodoEditDialog(
            isNew = false,
            initialText = todo.text,
            onDismiss = { editing = null },
            onConfirm = { text ->
                scope.launch {
                    TodoCenter.updateTodo(todo.copy(text = text))
                    editing = null
                }
            },
            onDelete = {
                scope.launch {
                    TodoCenter.deleteTodo(todo.id)
                    editing = null
                }
            },
        )
    }
}

/** 一行代办：左圆点切换完成（单击）+ 文字（点行进编辑弹窗）；已完成 = 实心勾 + 置灰划线。 */
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

/** 添加 / 编辑代办弹窗：底部锚定（贴键盘上方）+ 自动弹键盘；编辑态额外提供删除。 */
@Composable
private fun TodoEditDialog(
    isNew: Boolean,
    initialText: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    // Dialog 是独立窗口且半透明：adjustResize 在这种窗口上不可靠（键盘盖住内容）。
    // 改 ADJUST_NOTHING 让窗口不自动缩放/平移，改由 imePadding() 把内容顶到键盘上方。
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
    // 按 initialText 复位：同一调用点切换新增/编辑目标时不能残留上次的输入。
    // 光标放末尾（编辑预填后直接续写，而不是从最前插入）。
    var textValue by rememberSaveable(initialText, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    val trimmed = textValue.text.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val curOnConfirm by rememberUpdatedState(onConfirm)

    fun submit() {
        if (trimmed.isEmpty()) return
        keyboard?.hide()
        curOnConfirm(trimmed)
    }

    // 自动聚焦：键盘随之弹出。
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
        Box(
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
                    .padding(LargeGutter),
                verticalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
                Text(
                    text = stringResource(
                        if (isNew) R.string.todo_create_title else R.string.todo_edit_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(
                                text = stringResource(R.string.todo_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = { submit() },
                        enabled = trimmed.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.todo_save))
                    }
                }
            }
        }
    }
}
