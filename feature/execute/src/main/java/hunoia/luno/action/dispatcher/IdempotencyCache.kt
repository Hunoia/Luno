package hunoia.luno.action.dispatcher

import hunoia.luno.core.domain.ActionResult
import hunoia.luno.execute.ExecuteSettlement
import kotlinx.coroutines.CompletableDeferred

/**
 * 幂等窗口（architecture §4.7）。
 *
 * 语义：
 * - 键由**产生意图的一方**生成，不透明；`Dispatcher` 只做窗口内的等值判定。
 * - 窗口内同键重放直接返回上一次落定结果，不重复执行副作用。
 * - 失败（`ActionResult.Failed`）不缓存：幂等不是重试。
 * - 并发同键：后到者等待先到者落定，不并行执行两次。
 * - 只存在于 `:service` 进程内存，不落盘、不进备份。
 */
class IdempotencyCache(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private data class Entry(val settledAt: Long, val settlement: ExecuteSettlement)

    /** 访问顺序 LRU：命中会把条目挪到队尾。 */
    private val settled = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    private val inFlight = HashMap<String, CompletableDeferred<ExecuteSettlement>>()

    suspend fun run(key: String?, block: suspend () -> ExecuteSettlement): ExecuteSettlement {
        if (key.isNullOrBlank()) return block()

        pruneExpired()
        settled[key]?.let { return it.settlement }
        inFlight[key]?.let { return it.await() }

        val deferred = CompletableDeferred<ExecuteSettlement>()
        inFlight[key] = deferred
        try {
            val settlement = block()
            if (settlement.result !is ActionResult.Failed) {
                settled[key] = Entry(clock(), settlement)
                pruneExpired()
            }
            deferred.complete(settlement)
            return settlement
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key)
        }
    }

    /** 仅用于测试与诊断。 */
    fun size(): Int = settled.size

    private fun pruneExpired() {
        if (settled.isEmpty()) return
        val now = clock()
        val iterator = settled.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.settledAt >= windowMs) iterator.remove() else break
        }
    }

    companion object {
        /** 双击 / IPC 重投 / 面板重复点击的防抖窗口。 */
        const val DEFAULT_WINDOW_MS = 2_000L
        const val DEFAULT_MAX_ENTRIES = 64
    }
}
