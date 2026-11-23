package hunoia.luno.domain.gridpanel

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import kotlinx.coroutines.flow.Flow

/**
 * 代办域门面（gridpanel 部件与管理页的唯一入口）。
 *
 * 切换 / 增删改是应用内状态变更（配置写入），走 domain 门面写回；
 * 不走 Dispatcher / SurfaceDispatch（architecture §1.3 配置写入不是 Action）。
 */
object TodoCenter {

    val todoConfig: Flow<TodoConfig> = ConfigProvider.todoConfig

    /** 返回导航页那一帧的配置兜底：必须是真实值，不能用 `TodoConfig()` 空默认。 */
    fun latestTodoConfig(): TodoConfig = ConfigProvider.latestTodoConfig()

    suspend fun getTodoConfig(): TodoConfig = ConfigProvider.getTodoConfig()

    /** 部件 / 管理页切换完成态（done=true 记完成时刻，写后按保留天数惰性裁剪）。 */
    suspend fun toggleTodo(todoId: String, done: Boolean) =
        ConfigProvider.toggleTodo(todoId, done)

    suspend fun addTodo(todo: TodoItem) = ConfigProvider.addTodo(todo)

    suspend fun updateTodo(todo: TodoItem) = ConfigProvider.updateTodo(todo)

    suspend fun deleteTodo(todoId: String) = ConfigProvider.deleteTodo(todoId)

    /** 待办域全局推迟时长（分钟）：只影响通知「推迟」后的重弹间隔，不改原提醒时间。 */
    suspend fun setReminderSnoozeMinutes(minutes: Int) =
        ConfigProvider.updateTodoConfig { it.withReminderSnoozeMinutes(minutes) }

    /** 已完成保留天数：降低档位时立即裁剪旧数据。 */
    suspend fun setDoneRetentionDays(days: Int) =
        ConfigProvider.setDoneRetentionDays(days)

    /** 清空全部已完成代办（保留未完成）。 */
    suspend fun clearDoneTodos() =
        ConfigProvider.clearDoneTodos()
}
