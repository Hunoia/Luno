package hunoia.luno.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import hunoia.luno.config.model.canCheck
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.ui.component.sheet.ConfirmDialogContent
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 桌面部件的一次性确认弹窗（习惯打卡 / 待办完成）。
 *
 * 复用 `:ui:design` 的 [ConfirmDialogContent]（底部卡片 + 取消/确认），在独立 Activity 里
 * 直接铺内容，不再套一层 Compose `Dialog`，避免双窗口/双遮罩；退场由 `AnchoredDialogContent`
 * 的两段式动画收尾，宿主不在动作里同步 `finish()`。
 *
 * 部件侧不再维护 pending 状态：点阵/圆点点击只是启动本 Activity；确认后才写回域门面，
 * 再由 [WidgetSync] 刷新部件。取消 / 返回 / 点外部都只关闭弹窗，不改数据。
 */
class WidgetConfirmDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = ConfirmKind.from(intent.getStringExtra(EXTRA_KIND)) ?: run {
            finish()
            return
        }
        val targetId = intent.getStringExtra(EXTRA_ID) ?: run {
            finish()
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val count = intent.getIntExtra(EXTRA_COUNT, 0)
        val target = intent.getIntExtra(EXTRA_TARGET, 1)
        // 与 TodoEditDialogActivity 同口径：弹层自己消费 navigationBars ∪ ime inset，
        // 不让 decor 再垫一层（否则卡片会比弹窗族其他成员高出一个导航栏）。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SideGestureTheme {
                ConfirmScreen(
                    kind = kind,
                    targetId = targetId,
                    label = label,
                    count = count,
                    target = target,
                    onFinished = { finish() },
                )
            }
        }
    }

    /** 切走即关：透明弹窗不该在后台残留。 */
    override fun onStop() {
        super.onStop()
        finish()
    }

    enum class ConfirmKind(val value: String) {
        HABIT_CHECK("habit_check"),
        TODO_COMPLETE("todo_complete"),
        ;

        companion object {
            fun from(value: String?): ConfirmKind? = entries.firstOrNull { it.value == value }
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_COUNT = "count"
        private const val EXTRA_TARGET = "target"

        /** 部件点击 → 确认弹窗；[label] 是习惯名 / 待办文本，用于弹窗正文。 */
        fun intent(
            context: Context,
            kind: ConfirmKind,
            targetId: String,
            label: String,
            count: Int = 0,
            target: Int = 1,
        ): Intent =
            Intent().apply {
                component = ComponentName(context, WidgetConfirmDialogActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_KIND, kind.value)
                putExtra(EXTRA_ID, targetId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_COUNT, count)
                putExtra(EXTRA_TARGET, target)
            }
    }
}

@Composable
private fun ConfirmScreen(
    kind: WidgetConfirmDialogActivity.ConfirmKind,
    targetId: String,
    label: String,
    count: Int,
    target: Int,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    val title = stringResource(
        when (kind) {
            WidgetConfirmDialogActivity.ConfirmKind.HABIT_CHECK -> R.string.widget_habit_check_title
            WidgetConfirmDialogActivity.ConfirmKind.TODO_COMPLETE -> R.string.widget_todo_complete_title
        },
    )
    val message = when (kind) {
        WidgetConfirmDialogActivity.ConfirmKind.HABIT_CHECK -> {
            if (target > 1) {
                stringResource(
                    R.string.widget_habit_check_message_progress,
                    (count + 1).coerceAtMost(target),
                    target,
                    label,
                )
            } else {
                stringResource(R.string.widget_habit_check_message, label)
            }
        }
        WidgetConfirmDialogActivity.ConfirmKind.TODO_COMPLETE ->
            stringResource(R.string.widget_todo_complete_message, label)
    }
    ConfirmDialogContent(
        title = title,
        text = message,
        onDismiss = { if (!working) onFinished() },
        // 写回是异步的：先干活，完成后才开始退场（`dismiss()` → 退场动画播完才 `onFinished()`），
        // 否则 Activity 会在动画一半时被 finish，确认路径直接瞬消。
        onConfirm = { dismiss ->
            if (!working) {
                working = true
                scope.launch {
                    when (kind) {
                        WidgetConfirmDialogActivity.ConfirmKind.HABIT_CHECK -> confirmHabitCheck(context, targetId)
                        WidgetConfirmDialogActivity.ConfirmKind.TODO_COMPLETE -> confirmTodoComplete(context, targetId)
                    }
                    working = false
                    dismiss()
                }
            }
        },
    )
}

private suspend fun confirmHabitCheck(context: Context, habitId: String) {
    val config = HabitCenter.getHabitConfig()
    val habit = config.habitById(habitId) ?: return
    val today = LocalDate.now().toEpochDay()
    val checks = config.checks[habit.id].orEmpty()
    if (!canCheck(checks, today, habit.interval, today)) return
    // 多次习惯只 +1，不直接填满今日目标。
    HabitCenter.incrementHabit(habit.id, today)
    WidgetSync.updateHabitWidgets(context)
}

private suspend fun confirmTodoComplete(context: Context, todoId: String) {
    val todo = TodoCenter.getTodoConfig().todoById(todoId) ?: return
    if (todo.done) return
    TodoCenter.toggleTodo(todo.id, true)
    WidgetSync.updateTodoWidgets(context)
}
