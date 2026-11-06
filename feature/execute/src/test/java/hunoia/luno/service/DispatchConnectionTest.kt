package hunoia.luno.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨进程分发连接的契约（architecture §12.1 / §12.2）：
 * 绑定失败可重试、限频、服务断开保留连接、宿主死亡后必须重新绑定。
 */
class DispatchConnectionTest {

    private var now = 10_000L

    private fun connection() = DispatchConnection(retryIntervalMs = 2_000L, clock = { now })

    @Test
    fun `绑定失败不占位，允许下一轮重试`() {
        val state = connection()
        assertFalse(state.onBindAttempt(bound = false))
        assertFalse(state.bindRequested)
        assertTrue(state.shouldRetryBind())
    }

    @Test
    fun `绑定成功后才停止重试`() {
        val state = connection()
        assertTrue(state.onBindAttempt(bound = true))
        assertTrue(state.bindRequested)
        assertFalse(state.shouldRetryBind())
    }

    @Test
    fun `未绑定时的重试受时间窗限制`() {
        val state = connection()
        state.onBindAttempt(bound = false)

        assertTrue(state.shouldRetryBind())
        assertFalse(state.shouldRetryBind())

        now += 2_000
        assertTrue(state.shouldRetryBind())
    }

    @Test
    fun `首次重试不被零值时钟卡住`() {
        now = 500
        val state = connection()
        assertTrue(state.shouldRetryBind())
    }

    @Test
    fun `服务断开保留绑定等待系统重连`() {
        val state = connection()
        state.onBindAttempt(bound = true)
        state.onServiceDisconnected()

        assertTrue(state.bindRequested)
        assertFalse(state.shouldRetryBind())
    }

    @Test
    fun `宿主进程死亡后允许重新绑定`() {
        val state = connection()
        state.onBindAttempt(bound = true)
        state.onBindingDied()

        assertFalse(state.bindRequested)
        now += 2_000
        assertTrue(state.shouldRetryBind())
    }

    @Test
    fun `空绑定后允许重新绑定`() {
        val state = connection()
        state.onBindAttempt(bound = true)
        state.onNullBinding()

        assertFalse(state.bindRequested)
        now += 2_000
        assertTrue(state.shouldRetryBind())
    }
}
