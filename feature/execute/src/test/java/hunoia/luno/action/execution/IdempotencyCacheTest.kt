package hunoia.luno.action.execution

import hunoia.luno.action.dispatcher.IdempotencyCache
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ExecuteSettlement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 幂等窗口语义（architecture §4.7）：命中返回上次落定、失败不缓存、并发同键只执行一次。 */
class IdempotencyCacheTest {

    private var now = 1_000L
    private var executions = 0

    private fun cache(windowMs: Long = 2_000L, maxEntries: Int = 64) =
        IdempotencyCache(windowMs = windowMs, maxEntries = maxEntries, clock = { now })

    private fun success(): ExecuteSettlement = settlement(ActionResult.Success())

    private fun failed(): ExecuteSettlement =
        settlement(ActionResult.Failed(ActionFailure.ExecutionFailed))

    private fun settlement(result: ActionResult): ExecuteSettlement = ExecuteSettlement(
        request = ActionRequest(StoredAction.of("test.idempotency"), DispatchOrigin.PANEL),
        typeId = "test.idempotency",
        result = result,
    )

    private suspend fun run(
        cache: IdempotencyCache,
        key: String?,
        result: () -> ActionResult = { ActionResult.Success() },
    ): ExecuteSettlement = cache.run(key) {
        executions++
        settlement(result())
    }

    @Test
    fun `无键时每次都执行`() = runBlocking {
        val cache = cache()
        run(cache, null)
        run(cache, null)
        assertEquals(2, executions)
        assertEquals(0, cache.size())
    }

    @Test
    fun `窗口内同键只执行一次并返回同一结果`() = runBlocking {
        val cache = cache()
        val first = run(cache, "k1")
        now += 500
        val second = run(cache, "k1")

        assertEquals(1, executions)
        assertSame(first, second)
    }

    @Test
    fun `不同键各自执行`() = runBlocking {
        val cache = cache()
        run(cache, "k1")
        run(cache, "k2")
        assertEquals(2, executions)
    }

    @Test
    fun `超过窗口后同键重新执行`() = runBlocking {
        val cache = cache(windowMs = 1_000)
        run(cache, "k1")
        now += 1_000
        run(cache, "k1")
        assertEquals(2, executions)
    }

    @Test
    fun `失败不缓存`() = runBlocking {
        val cache = cache()
        run(cache, "k1") { ActionResult.Failed(ActionFailure.ExecutionFailed) }
        run(cache, "k1") { ActionResult.Failed(ActionFailure.ExecutionFailed) }
        assertEquals(2, executions)
        assertEquals(0, cache.size())
    }

    @Test
    fun `需授权结果会被缓存避免重复弹提示`() = runBlocking {
        val cache = cache()
        run(cache, "k1") { ActionResult.RequiresCapability(hunoia.luno.core.domain.Capability.Accessibility) }
        run(cache, "k1") { ActionResult.RequiresCapability(hunoia.luno.core.domain.Capability.Accessibility) }
        assertEquals(1, executions)
    }

    @Test
    fun `并发同键只执行一次`() = runBlocking {
        val cache = cache()
        val results = (1..8).map {
            async {
                cache.run("k1") {
                    executions++
                    delay(20)
                    settlement(ActionResult.Success())
                }
            }
        }.awaitAll()

        assertEquals(1, executions)
        assertEquals(1, results.distinct().size)
    }

    @Test
    fun `条目上限按 LRU 淘汰`() = runBlocking {
        val cache = cache(maxEntries = 2)
        run(cache, "k1")
        run(cache, "k2")
        run(cache, "k3")
        assertEquals(2, cache.size())

        // k1 已被淘汰 → 重新执行
        executions = 0
        run(cache, "k1")
        assertEquals(1, executions)
    }
}
