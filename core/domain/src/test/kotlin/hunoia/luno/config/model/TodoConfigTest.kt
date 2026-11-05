package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoConfigTest {

    private val day = 86_400_000L
    private val now = 1_000_000_000L

    // ---------- add / update / delete ----------

    @Test
    fun `add appends todo`() {
        val cfg = TodoConfig().addTodo(TodoItem(id = "t1", text = "买牛奶"))
        assertEquals(1, cfg.todos.size)
        assertEquals("买牛奶", cfg.todos.first().text)
    }

    @Test
    fun `update replaces in place`() {
        val cfg = TodoConfig(todos = listOf(TodoItem(id = "t1", text = "旧")))
        val renamed = cfg.updateTodo(TodoItem(id = "t1", text = "新"))
        assertEquals(1, renamed.todos.size)
        assertEquals("新", renamed.todos.first().text)
    }

    @Test
    fun `delete removes todo`() {
        val cfg = TodoConfig(todos = listOf(TodoItem(id = "t1"), TodoItem(id = "t2")))
        val after = cfg.deleteTodo("t1")
        assertEquals(1, after.todos.size)
        assertTrue(after.todoById("t2") != null)
        assertTrue(after.todoById("t1") == null)
    }

    // ---------- toggleTodo ----------

    @Test
    fun `complete records completedAt and clears on uncomplete`() {
        val cfg = TodoConfig(todos = listOf(TodoItem(id = "t1")))
        val done = cfg.toggleTodo("t1", true, completedAt = now)
        assertTrue(done.todoById("t1")!!.done)
        assertEquals(now, done.todoById("t1")!!.completedAt)

        val undone = done.toggleTodo("t1", false)
        assertFalse(undone.todoById("t1")!!.done)
        assertNull(undone.todoById("t1")!!.completedAt)
    }

    @Test
    fun `toggle is idempotent`() {
        val cfg = TodoConfig(todos = listOf(TodoItem(id = "t1")))
        val done = cfg.toggleTodo("t1", true, completedAt = now)
        assertTrue(done.toggleTodo("t1", true) === done) // 已完成再完成原样返回

        val undone = done.toggleTodo("t1", false)
        assertTrue(undone.toggleTodo("t1", false) === undone) // 未完成再取消原样返回
    }

    @Test
    fun `pending count ignores done`() {
        val cfg = TodoConfig(
            todos = listOf(
                TodoItem(id = "t1"),
                TodoItem(id = "t2"),
                TodoItem(id = "t3", done = true, completedAt = now),
            ),
        )
        assertEquals(2, cfg.pendingCount)
    }

    // ---------- pruneDone ----------

    @Test
    fun `prune keeps recent done and drops old within retention`() {
        val cfg = TodoConfig(
            todos = listOf(
                TodoItem(id = "t1", done = true, completedAt = now),                 // 刚完成
                TodoItem(id = "t2", done = true, completedAt = now - 5 * day),       // 5 天前（保留）
                TodoItem(id = "t3", done = true, completedAt = now - 8 * day),       // 8 天前（裁掉，> 7）
                TodoItem(id = "t4"),                                                 // 未完成（保留）
            ),
        )
        val pruned = cfg.pruneDone(now, 7)
        assertTrue(pruned.todoById("t1") != null)
        assertTrue(pruned.todoById("t2") != null)
        assertTrue(pruned.todoById("t3") == null)
        assertTrue(pruned.todoById("t4") != null)
    }

    @Test
    fun `prune keeps boundary done exactly at cutoff`() {
        // 边界：完成于 now - retention，恰好保留
        val cfg = TodoConfig(todos = listOf(TodoItem(id = "t1", done = true, completedAt = now - 7 * day)))
        val pruned = cfg.pruneDone(now, 7)
        assertTrue(pruned.todoById("t1") != null)
    }

    @Test
    fun `prune is no-op when nothing older than retention`() {
        val cfg = TodoConfig(
            todos = listOf(
                TodoItem(id = "t1", done = true, completedAt = now),
                TodoItem(id = "t2"),
            ),
        )
        val pruned = cfg.pruneDone(now, 7)
        assertTrue(pruned === cfg) // 无变化原样返回
    }
}
