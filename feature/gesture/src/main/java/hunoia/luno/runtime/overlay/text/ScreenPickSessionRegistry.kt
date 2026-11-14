package hunoia.luno.runtime.overlay.text

/**
 * 全屏手势按下瞬间的预捕获快照，按手势会话 id 暂存。
 *
 * 为什么需要这张表：识屏动作必须走 `ActionRequest → Dispatcher`，但预捕获快照
 * （按下时的 a11y 文本 + 截屏位图）是**同进程、短生命周期**的运行时对象，不能
 * 塞进要跨 IPC 的 `ActionRequest`。触发侧在点火时把快照交到这里，执行侧在
 * `showScreenPickPortal` 里按 `sessionId` 取走；两边都在 `:service` 进程。
 *
 * 表只保留最近若干条，防止分发失败时泄漏位图；取走即删。
 */
object ScreenPickSessionRegistry {
    private const val MaxEntries = 4

    private val entries = object : LinkedHashMap<String, ScreenPreCapture>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ScreenPreCapture>?): Boolean =
            size > MaxEntries
    }

    @Synchronized
    fun offer(sessionId: String, preCapture: ScreenPreCapture?) {
        if (preCapture == null) return
        entries.remove(sessionId)?.let { recycle(it) }
        entries[sessionId] = preCapture
    }

    @Synchronized
    fun consume(sessionId: String?): ScreenPreCapture? {
        if (sessionId == null) return null
        return entries.remove(sessionId)
    }

    /** 会话作废时丢弃未取走的快照；已经 handedOff 的位图由识屏层接管，不在这里回收。 */
    @Synchronized
    fun cancel(sessionId: String?) {
        if (sessionId == null) return
        entries.remove(sessionId)?.let { recycle(it) }
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { recycle(it) }
        entries.clear()
    }

    private fun recycle(preCapture: ScreenPreCapture) {
        preCapture.discard()
    }
}
