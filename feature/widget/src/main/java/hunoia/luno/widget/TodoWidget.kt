package hunoia.luno.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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

/** 底部内边距比左右/顶部少 2dp，和顶部 14dp 外边距配合收紧底部。 */
private val TodoBottomPadding = WidgetInnerPadding - 2.dp

/**
 * 待办桌面小部件（Glance）：未完成列表（可滚动 LazyColumn，不截断不计数） +
 * 「待办 · N」标题 + 「+」新增。
 *
 * 交互：标题点击进 Luno 待办页；行圆点 → 普通确认弹窗完成（[WidgetConfirmDialogActivity]）；
 * 点行文字打开该条编辑弹窗；「+」打开新增弹窗（都直达弹窗 Activity，不进应用页面）。
 * 完成即从部件消失（管理页仍可撤销）。
 *
 * 配色：主色/文字走 `GlanceTheme.colors`（Material You 动态主题，与面板 `MaterialTheme.colorScheme` 一致）。
 * 数据源 [TodoCenter.todoConfig]（MultiProcess DataStore），经 domain 门面订阅；
 * 写回走 [TodoCenter.toggleTodo]（应用内状态变更，不走 Dispatcher，architecture §7.5）。
 */
class TodoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 数据与每实例状态必须在**组合内**读取：Glance 的 session 在 45s 内是复用的，
            // update() 只重跑 provideContent 的组合，不会再次执行 provideGlance。
            // 在外面读一次再当参数传进来，部件会一直显示 session 创建时的旧数据。
            val config by TodoCenter.todoConfig.collectAsState(initial = null)
            val loaded = config
            if (loaded == null) {
                // 首帧数据流未到：只画底，避免先闪一下「暂无待办」。
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(top = WidgetOuterTop),
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(ItemCorner)
                            .background(GlanceTheme.colors.surface),
                    ) {}
                }
            } else {
                val pending = loaded.todos.filter { !it.done }

                TodoContent(
                    context = context,
                    pending = pending,
                    title = context.getString(R.string.widget_todo_title, pending.size),
                    emptyText = context.getString(R.string.widget_todo_empty),
                )
            }
        }
    }
}

@Composable
private fun TodoContent(
    context: Context,
    pending: List<TodoItem>,
    title: String,
    emptyText: String,
) {
    val colors = GlanceTheme.colors
    val accent = colors.primary
    val onSurface = colors.onSurface
    val onSurfaceVariant = colors.onSurfaceVariant
    // 外层只负责顶部外边距（透明），卡片背景放在内层。
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(top = WidgetOuterTop),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(ItemCorner)
                .background(colors.surface)
                .padding(
                    start = WidgetInnerPadding,
                    top = WidgetInnerPadding,
                    end = WidgetInnerPadding,
                    bottom = TodoBottomPadding,
                ),
        ) {
            // 标题行：主色点 + 「待办 · N」（点击进 Luno 待办页） + 「+」新增弹窗。
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
                    modifier = GlanceModifier
                        .defaultWeight()
                        .semantics {
                            contentDescription = context.getString(R.string.widget_todo_open_list)
                        }
                        .clickable(onClick = actionStartActivity(TodoExtras.openListIntent(context))),
                    style = TextStyle(
                        color = onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(4.dp))
                Image(
                    provider = ImageProvider(R.drawable.widget_add),
                    contentDescription = context.getString(R.string.widget_todo_add),
                    modifier = GlanceModifier
                        .size(26.dp)
                        .cornerRadius(13.dp)
                        .background(accent)
                        .clickable(onClick = actionStartActivity(TodoExtras.editIntent(context))),
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
                            onSurface = onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRowContent(
    context: Context,
    todo: TodoItem,
    onSurface: ColorProvider,
) {
    val openDesc = context.getString(R.string.widget_todo_open, todo.text)
    val completeDesc = context.getString(R.string.widget_todo_complete, todo.text)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(TodoRowH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆点：空心描边，点击打开普通确认弹窗完成。
        Box(
            modifier = GlanceModifier
                .size(TodoDotSize)
                .semantics { contentDescription = completeDesc }
                .clickable(
                    onClick = actionStartActivity(
                        WidgetConfirmDialogActivity.intent(
                            context,
                            WidgetConfirmDialogActivity.ConfirmKind.TODO_COMPLETE,
                            todo.id,
                            todo.text,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ring),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(onSurface.alpha(context, 0.40f)),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        // 行文字：点击打开编辑弹窗（Glance 嵌套 clickable 不生效，编辑只挂在文字上，
        // 避免吞掉圆点完成；点行空白不触发编辑）。
        Text(
            text = todo.text,
            modifier = GlanceModifier
                .defaultWeight()
                .semantics { contentDescription = openDesc }
                .clickable(
                    onClick = actionStartActivity(TodoExtras.editIntent(context, todo.id)),
                ),
            style = TextStyle(
                color = onSurface,
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )
    }
}
