package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/** 已完成代办保留天数：完成超 `now − retention` 天写时惰性裁剪，避免无限增长。 */
const val TodoDoneRetentionDays = 7

private const val DayMillis = 86_400_000L

/**
 * 一个代办。一次性任务：只有文字 + 完成标记（无 streak / interval / 历史点阵）。
 *
 * 字段：
 * - [text]：代办文字（唯一用户可见内容）。
 * - [done]：是否已完成。
 * - [createdAt]：创建时刻（epoch millis），仅展示用。
 * - [completedAt]：完成时刻（epoch millis），供「完成 N 天后自动清理」；未完成为 null。
 */
@Serializable
@Keep
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
)

/**
 * 代办配置域：代办列表（一次性任务）。
 *
 * 数据流：网格面板部件与管理页都经 `:core:data` 的 `ConfigProvider` 门面读写，
 * 不直接进 `ActionRequest`（应用内状态变更，非可执行 Action）。
 */
@Serializable
@Keep
data class TodoConfig(
    val todos: List<TodoItem> = emptyList(),
) {

    val pendingCount: Int get() = todos.count { !it.done }

    fun todoById(id: String): TodoItem? = todos.firstOrNull { it.id == id }

    fun addTodo(todo: TodoItem): TodoConfig = copy(todos = todos + todo)

    fun updateTodo(todo: TodoItem): TodoConfig =
        copy(todos = todos.map { if (it.id == todo.id) todo else it })

    fun deleteTodo(todoId: String): TodoConfig =
        copy(todos = todos.filterNot { it.id == todoId })

    /**
     * 切换完成态：done=true 记录完成时刻（供自动清理），取消完成清空 completedAt。
     * 幂等：状态无变化原样返回（避免无意义写盘）。
     */
    fun toggleTodo(
        todoId: String,
        done: Boolean,
        completedAt: Long = System.currentTimeMillis(),
    ): TodoConfig {
        val index = todos.indexOfFirst { it.id == todoId }
        if (index < 0) return this
        val current = todos[index]
        if (current.done == done) return this
        val next = todos.toMutableList()
        next[index] = current.copy(done = done, completedAt = if (done) completedAt else null)
        return copy(todos = next)
    }

    /**
     * 惰性裁剪：移除完成超 `now − retentionDays` 天的代办。无变化原样返回（避免无意义写盘）。
     * 写时调用，存储有界（见 [TodoDoneRetentionDays]）。
     */
    fun pruneDone(nowMillis: Long, retentionDays: Int): TodoConfig {
        val cutoff = nowMillis - retentionDays.coerceAtLeast(0) * DayMillis
        val next = todos.filterNot { todo ->
            todo.done && todo.completedAt?.let { it < cutoff } == true
        }
        return if (next == todos) this else copy(todos = next)
    }
}
