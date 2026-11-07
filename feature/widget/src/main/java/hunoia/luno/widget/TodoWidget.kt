package hunoia.luno.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.TodoExtras

/** 行高：镜像内置 TodoListCell 的 `TodoRowH`。 */
private val TodoRowH = 32.dp

/** 待办行圆点直径：镜像内置 TodoCheckCircle 的 `circleSize`。 */
private val TodoDotSize = 22.dp

/** 待办勾选回调的 todoId 参数键。 */
private object TodoParams {
    val TODO_ID = ActionParameters.Key<String>("todo_id")
}

/**
 * 待办桌面小部件（Glance）：未完成列表（可滚动 LazyColumn，不截断不计数） +
 * 「待办 · N」标题 + 「+」新增。
 *
 * 交互：行圆点双击确认完成（镜像面板 TodoCheckCircle，圆环倒计时）；点行文字打开
 * 该条编辑弹窗；「+」打开新增弹窗（都直达弹窗 Activity，不进应用页面）。
 * 完成即从部件消失（管理页仍可撤销）。
 *
 * 配色：主色/文字走 `GlanceTheme.colors`（Material You 动态主题，与面板 `MaterialTheme.colorScheme` 一致）。
 * 数据源 [TodoCenter.todoConfig]（MultiProcess DataStore），经 domain 门面订阅；
 * 写回走 [TodoCenter.toggleTodo]（应用内状态变更，不走 Dispatcher，architecture §7.5）。
 */
class TodoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs: Preferences = getAppWidgetState(context, id)
        val config = TodoCenter.getTodoConfig()
        val pending = config.todos.filter { !it.done }
        val now = System.currentTimeMillis()
        val pendingTodoId = prefs[WidgetStateKeys.TODO_PENDING_ID].orEmpty()
        val pendingAt = prefs[WidgetStateKeys.TODO_PENDING_AT] ?: 0L
        val pendingActive = pendingTodoId.isNotEmpty() &&
            now - pendingAt < ConfirmWindowMillis &&
            pending.any { it.id == pendingTodoId }

        val title = context.getString(R.string.widget_todo_title, pending.size)
        val emptyText = context.getString(R.string.widget_todo_empty)

        provideContent {
            TodoContent(
                context = context,
                pending = pending,
                title = title,
                emptyText = emptyText,
                pendingTodoId = if (pendingActive) pendingTodoId else "",
            )
        }
    }
}

@Composable
private fun TodoContent(
    context: Context,
    pending: List<TodoItem>,
    title: String,
    emptyText: String,
    pendingTodoId: String,
) {
    val colors = GlanceTheme.colors
    val accent = colors.primary
    val onSurface = colors.onSurface
    val onSurfaceVariant = colors.onSurfaceVariant
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(ItemCorner)
            .background(colors.surface)
            .padding(WidgetInnerPadding),
    ) {
        // 标题行：主色点 + 「待办 · N」 + 「+」新增弹窗。
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.size(9.dp).cornerRadius(4.5.dp).background(accent),
            ) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Image(
                provider = ImageProvider(R.drawable.widget_add),
                contentDescription = context.getString(R.string.widget_todo_add),
                modifier = GlanceModifier
                    .size(26.dp)
                    .cornerRadius(13.dp)
                    .background(accent)
                    .clickable(onClick = actionStartActivity(addTodoIntent(context))),
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (pending.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyText,
                    style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp),
                )
            }
        } else {
            // 可滚动列表：超出可视高度的行由宿主 ListView 滚动可达，不截断不计数。
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(pending, itemId = { it.id.hashCode().toLong() }) { todo ->
                    TodoRowContent(
                        context = context,
                        todo = todo,
                        accent = accent,
                        onSurface = onSurface,
                        pending = todo.id == pendingTodoId,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoRowContent(
    context: Context,
    todo: TodoItem,
    accent: ColorProvider,
    onSurface: ColorProvider,
    pending: Boolean,
) {
    val openDesc = context.getString(R.string.widget_todo_open, todo.text)
    val completeDesc = context.getString(R.string.widget_todo_complete, todo.text)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(TodoRowH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆点：pending = 实心主色 + 白勾（一眼可见的确认反馈，不用细环）；
        // 平时 = 文字色 40% 圆环描边（对齐内置 TodoCheckCircle）。
        Box(
            modifier = GlanceModifier
                .size(TodoDotSize)
                .semantics { contentDescription = completeDesc }
                .clickable(
                    onClick = actionRunCallback<TodoToggleCallback>(
                        actionParametersOf(TodoParams.TODO_ID to todo.id),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (pending) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(TodoDotSize / 2)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_check),
                        contentDescription = null,
                        modifier = GlanceModifier.size(TodoDotSize * 0.6f),
                        colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                    )
                }
            } else {
                Image(
                    provider = ImageProvider(R.drawable.widget_ring),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(onSurface.alpha(context, 0.40f)),
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        // 行文字：点击打开编辑弹窗（Glance 嵌套 clickable 不生效，编辑只挂在文字上，
        // 避免吞掉圆点 toggle；点行空白不触发编辑）。
        Text(
            text = todo.text,
            modifier = GlanceModifier
                .defaultWeight()
                .semantics { contentDescription = openDesc }
                .clickable(
                    onClick = actionStartActivity(editTodoIntent(context, todo.id)),
                ),
            style = TextStyle(
                color = onSurface,
                fontSize = 11.sp,
                fontWeight = if (pending) FontWeight.Medium else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

/** 打开待办编辑弹窗 Activity（新增：无 EXTRA_TODO_ID）。 */
private fun addTodoIntent(context: Context): Intent = Intent().apply {
    component = ComponentName(context.packageName, TodoExtras.ACTIVITY_TODO_EDIT)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/** 打开待办编辑弹窗 Activity（编辑 [todoId]）。 */
private fun editTodoIntent(context: Context, todoId: String): Intent = addTodoIntent(context).apply {
    putExtra(TodoExtras.EXTRA_TODO_ID, todoId)
}

/**
 * 待办行圆点双击确认回调：首点置该条待确认态并启动多帧重绘（圆环倒计时），窗口内再点才
 * 标记完成；超时/换行首点都从新起点开始。完成即消失（todoConfig 写回触发重绘）。
 */
class TodoToggleCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val todoId = parameters[TodoParams.TODO_ID] ?: return
        val widget = TodoWidget()
        val prefs: Preferences = widget.getAppWidgetState(context, glanceId)
        val now = System.currentTimeMillis()
        val pendingId = prefs[WidgetStateKeys.TODO_PENDING_ID].orEmpty()
        val pendingAt = prefs[WidgetStateKeys.TODO_PENDING_AT] ?: 0L
        if (pendingId == todoId && now - pendingAt < ConfirmWindowMillis) {
            TodoCenter.toggleTodo(todoId, true)
            updateAppWidgetState(context, glanceId) {
                it[WidgetStateKeys.TODO_PENDING_ID] = ""
                it.remove(WidgetStateKeys.TODO_PENDING_AT)
            }
            widget.update(context, glanceId)
        } else {
            updateAppWidgetState(context, glanceId) {
                it[WidgetStateKeys.TODO_PENDING_ID] = todoId
                it[WidgetStateKeys.TODO_PENDING_AT] = now
            }
            widget.update(context, glanceId)
        }
    }
}
