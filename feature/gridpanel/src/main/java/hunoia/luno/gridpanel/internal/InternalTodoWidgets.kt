package hunoia.luno.gridpanel.internal

import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoExtras
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.launch

/**
 * 代办部件：待办列表（未完成项可勾选 + 未完成数）。
 *
 * 数据源 [TodoCenter.todoConfig]（JSON DataStore，MultiProcess），经 domain 门面订阅。
 * 完成写回走 [TodoCenter.toggleTodo]——应用内状态变更，不走 Dispatcher（architecture §1.3）。
 * 编辑态 [enabled] = false：部件点按让给层根拖拽；非编辑态点条目拉起主应用对应编辑页。
 */

/** 双击确认倒计时时长：单点出现圆环，倒计时内再点才标记完成。 */
private const val TodoConfirmCountdownMs = 1200

/** 行最小高：小于该值不显示行（按可用高度自适应行数）。 */
private val TodoMinRowH = 30.dp

/** 行数上限：避免小部件里塞太多行。 */
private const val TodoMaxRows = 8

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

    val pending = config.todos.filter { !it.done }

    // 双击确认：单点起圆环倒计时，倒计时内再点标记完成；超时取消。同一时刻只一条倒计时。
    var pendingId by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pendingId) {
        if (pendingId != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(TodoConfirmCountdownMs))
            pendingId = null
        }
    }
    // 进入编辑态（enabled=false）时取消未完成的确认。
    LaunchedEffect(enabled) {
        if (!enabled) pendingId = null
    }

    val cellDesc = stringResource(R.string.grid_panel_todo_desc, pending.size)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .semantics { contentDescription = cellDesc },
    ) {
        val contentW = (maxWidth - 20.dp).coerceAtLeast(24.dp)
        val contentH = (maxHeight - 20.dp).coerceAtLeast(24.dp)
        val titleH = 20.dp
        val gap = 6.dp
        val listH = (contentH - titleH - gap).coerceAtLeast(24.dp)
        val rows = (listH / TodoMinRowH).toInt().coerceIn(1, TodoMaxRows)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // 标题行：主色点 + 「待办 · N」（N = 未完成数）。
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
                    style = MaterialTheme.typography.titleSmall,
                    color = onCard,
                    fontSize = (contentW.value * 0.11f).coerceIn(10f, 14f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(gap))
            if (pending.isEmpty()) {
                // 空态：无代办或全部完成。
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (config.todos.isEmpty()) R.string.grid_panel_todo_empty
                            else R.string.grid_panel_todo_all_done,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.5f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    pending.take(rows).forEach { todo ->
                        TodoRow(
                            todo = todo,
                            pending = pendingId == todo.id,
                            progress = progress.value,
                            accent = accent,
                            onCard = onCard,
                            enabled = enabled,
                            onCircleTap = {
                                if (pendingId == todo.id) {
                                    pendingId = null
                                    scope.launch { TodoCenter.toggleTodo(todo.id, true) }
                                } else {
                                    pendingId = todo.id
                                }
                            },
                            onOpen = {
                                context.startActivity(
                                    Intent().apply {
                                        component = ComponentName(context.packageName, TodoExtras.ACTIVITY_MAIN)
                                        putExtra(TodoExtras.EXTRA_EDIT_TODO_ID, todo.id)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                    if (pending.size > rows) {
                        val more = pending.size - rows
                        Text(
                            text = stringResource(R.string.grid_panel_todo_more, more),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = onCard.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 一行待办：左圆点（双击确认完成）+ 右文字（点条目跳编辑页），两目标分离。 */
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
    }
}

/** 圆点：未确认 = 空心描边；倒计时 = 主色进度环（主圆变倒计时，不另叠一层）。 */
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
            // 始终可点（enabled 时）：点击处理器区分「arm / confirm」，pending 只换样式。
            .then(
                if (enabled) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = completeDesc }
                } else {
                    Modifier
                },
            )
            .then(
                if (pending) {
                    Modifier
                } else {
                    Modifier.border(width = strokeWidth, color = onCard.copy(alpha = 0.4f), shape = CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            Canvas(Modifier.size(circleSize)) {
                val w = strokeWidth.toPx()
                val inset = w / 2f
                drawArc(
                    color = onCard.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
    }
}
