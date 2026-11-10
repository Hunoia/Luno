package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoExtras
import hunoia.luno.gridpanel.TodoReminderFormat
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.launch
import hunoia.luno.ui.theme.containerShape

/**
 * 代办部件：待办列表（仅未完成，可滚动）。
 *
 * 数据源 [TodoCenter.todoConfig]（JSON DataStore，MultiProcess），经 domain 门面订阅。
 * 完成写回走 [TodoCenter.toggleTodo]——应用内状态变更，不走 Dispatcher（architecture §1.3）。
 * 完成即从部件消失（管理页仍可见、可撤销）；「+」/点条目直达待办编辑弹窗 Activity
 * （[TodoExtras.ACTIVITY_TODO_EDIT]，与桌面部件同一入口，行为一致）。
 */

/** 行高：固定高度，列表用 verticalScroll 滚动（不再截断 +M）。 */
private val TodoRowH = 32.dp

@Composable
internal fun TodoListCell(
    modifier: Modifier = Modifier,
    spanX: Int = 2,
    spanY: Int = 2,
    enabled: Boolean = true,
    params: String? = null,
) {
    val config by TodoCenter.todoConfig.collectAsState(initial = TodoConfig())
    val onCard = gridPanelOnCardColor
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val closePanel = LocalGridPanelClose.current

    // 只展示未完成：完成即从部件消失（管理页仍可见、可撤销）。
    val pending = config.todos.filter { !it.done }

    // 新建 / 编辑都直达待办编辑弹窗 Activity（与桌面部件同一入口、同一 Intent 构造）。
    fun openTodoDialog(todoId: String? = null) {
        context.startActivity(TodoExtras.editIntent(context, todoId))
        closePanel()
    }

    // 标题点击：只进 Luno 待办列表页，不自动开弹窗。
    fun openTodoList() {
        context.startActivity(TodoExtras.openListIntent(context))
        closePanel()
    }

    // 双击确认：单点起圆环倒计时，倒计时内再点标记完成；超时取消。同一时刻只一条倒计时。
    var pendingId by remember { mutableStateOf<String?>(null) }
    val progress = rememberConfirmProgress(
        key = pendingId,
        onTimeout = { pendingId = null },
    )
    // 进入编辑态（enabled=false）时取消未完成的确认。
    LaunchedEffect(enabled) {
        if (!enabled) pendingId = null
    }

    val cellDesc = stringResource(R.string.grid_panel_todo_desc, pending.size)
    val addDesc = stringResource(R.string.grid_panel_todo_add)
    val openListDesc = stringResource(R.string.grid_panel_todo_open_list)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .semantics { contentDescription = cellDesc },
    ) {
        val contentW = (maxWidth - 20.dp).coerceAtLeast(24.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // 标题行：主色点 + 「待办 · N」（N = 未完成数）+ 右侧「+」添加按钮。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.grid_panel_todo_title, pending.size),
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (enabled) {
                                Modifier
                                    .clickable { openTodoList() }
                                    .semantics { contentDescription = openListDesc }
                            } else {
                                Modifier
                            },
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    color = onCard,
                    fontSize = (contentW.value * 0.11f).coerceIn(10f, 14f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (enabled) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = { openTodoDialog() })
                            .semantics { contentDescription = addDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = onCard.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (pending.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.grid_panel_todo_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.5f),
                    )
                }
            } else {
                // 滚动只在运行态启用：编辑态滚动让给层根拖拽（镜像 clipboard userScrollEnabled）。
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (enabled) Modifier.verticalScroll(scrollState) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    pending.forEach { todo ->
                        TodoRow(
                            todo = todo,
                            pending = pendingId == todo.id,
                            progress = progress,
                            accent = accent,
                            onCard = onCard,
                            enabled = enabled,
                            onCircleTap = {
                                if (pendingId == todo.id) {
                                    // 双击确认完成。
                                    pendingId = null
                                    scope.launch { TodoCenter.toggleTodo(todo.id, true) }
                                } else {
                                    pendingId = todo.id
                                }
                            },
                            onOpen = { openTodoDialog(todo.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TodoRowH),
                        )
                    }
                }
            }
        }
    }
}

/** 一行待办：左圆点（双击确认完成）+ 右文字（点条目进编辑弹窗），两目标分离。 */
@Composable
private fun TodoRow(
    todo: TodoItem,
    pending: Boolean,
    progress: Float,
    accent: Color,
    onCard: Color,
    enabled: Boolean,
    onCircleTap: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDesc = stringResource(R.string.grid_panel_todo_open, todo.text)
    Row(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier
                        .clickable(onClick = onOpen)
                        .semantics { contentDescription = openDesc }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodoCheckCircle(
            pending = pending,
            progress = progress,
            accent = accent,
            onCard = onCard,
            enabled = enabled,
            onClick = onCircleTap,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = todo.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        todo.reminder?.let { reminder ->
            val past = reminder.isPast(System.currentTimeMillis())
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (past) {
                    MaterialTheme.colorScheme.error
                } else {
                    onCard.copy(alpha = 0.7f)
                },
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = TodoReminderFormat.label(LocalContext.current, reminder),
                style = MaterialTheme.typography.labelSmall,
                color = if (past) {
                    MaterialTheme.colorScheme.error
                } else {
                    onCard.copy(alpha = 0.7f)
                },
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

/** 圆点：空心描边（双击确认，倒计时 = 主色进度环）。 */
@Composable
private fun TodoCheckCircle(
    pending: Boolean,
    progress: Float,
    accent: Color,
    onCard: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val circleSize = 22.dp
    val strokeWidth = 2.dp
    val completeDesc = stringResource(R.string.grid_panel_todo_complete)
    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = completeDesc }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            Canvas(Modifier.size(circleSize)) {
                drawConfirmRing(
                    progress = progress,
                    accent = accent,
                    track = onCard.copy(alpha = 0.25f),
                    strokeWidth = strokeWidth.toPx(),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(strokeWidth, onCard.copy(alpha = 0.4f), CircleShape),
            )
        }
    }
}
