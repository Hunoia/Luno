package hunoia.luno.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import hunoia.luno.core.LunoLog
import hunoia.luno.gridpanel.TodoExtras
import hunoia.luno.gridpanel.todo.TodoEditDialog
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.launch

/**
 * 待办编辑弹窗（桌面部件「+」/点行直达，不进应用页面；面板部件同一入口）。
 *
 * 经 [TodoExtras.EXTRA_TODO_ID] 区分：空 = 新增，非空 = 编辑（预填文字 + 提醒 + 删除）。
 * 弹窗本体与提醒视图收口在 [TodoEditDialog]（与列表页共用），这里只负责窗口样式与收尾。
 * 写回 [hunoia.luno.domain.gridpanel.TodoCenter] 后由 WidgetSync 的配置订阅刷新全部待办部件；
 * 这里额外主动刷一次，保证弹窗关闭瞬间部件已更新。
 */
class TodoEditDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val todoId = intent.getStringExtra(TodoExtras.EXTRA_TODO_ID)
        // Dialog 主题浮窗：与应用内 Dialog 同样关 decorFitsSystemWindows + ADJUST_NOTHING，
        // 让 IME inset 派发下来（imePadding 生效），内容顶到键盘上方。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // 套 Luno 主题：与页面内 TodoEditDialog（SideGestureTheme 下）视觉一致。
            SideGestureTheme {
                TodoEditDialog(
                    todoId = todoId,
                    onDismiss = { finish() },
                    onChanged = {
                        lifecycleScope.launch {
                            runCatching { WidgetSync.updateTodoWidgets(applicationContext) }
                                .onFailure { LunoLog.w(TAG, "update after change failed: ${it.message}") }
                        }
                    },
                )
            }
        }
    }

    /** 切走即关：透明弹窗不该在后台残留（未保存的输入随关闭丢弃）。 */
    override fun onStop() {
        super.onStop()
        finish()
    }

    private companion object {
        const val TAG = "TodoEditDialog"
    }
}
