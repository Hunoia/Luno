package hunoia.luno.runtime.floatball.suppression

/**
 * 「滑掉后同源抑制」的抑制表：一条 `key → 滑掉时刻`，窗口期内同一 key 不再重弹。
 *
 * 纯逻辑：时钟、窗口长度、开关都由调用方注入，不碰 Android，可 JVM 单测。
 * 表格有界（[maxEntries] 条），满了淘汰最旧的一条（`LinkedHashMap` 按插入序）。
 *
 * 两种来源的时钟语义**故意保留差异**，由调用方各注入各的，不要在这里统一：
 * - 通知用 `uptimeMillis`（不含深睡）；
 * - 结果胶囊用 `elapsedRealtime`（含深睡）。
 */
internal class DismissSuppression(
    private val nowMs: () -> Long,
    private val windowMs: () -> Long,
    private val enabled: () -> Boolean = { true },
    private val maxEntries: Int = MaxEntries,
) {

    private val dismissedAt = LinkedHashMap<String, Long>()

    /**
     * 该 [key] 是否还在抑制窗内。
     *
     * 已过期（或功能关闭）的条目顺手清掉，避免抑制表长期挂着失效记录。
     */
    fun isSuppressed(key: String?): Boolean {
        if (key == null) return false
        if (!enabled()) return false
        val at = dismissedAt[key] ?: return false
        if (nowMs() - at < windowMs()) return true
        dismissedAt.remove(key)
        return false
    }

    /**
     * 记录一次滑掉。
     *
     * 功能关闭时清掉该 key 的历史 —— 否则关掉抑制再打开，会被滑掉很久以前的记录立刻压住。
     */
    fun markDismissed(key: String) {
        if (!enabled()) {
            dismissedAt.remove(key)
            return
        }
        dismissedAt.remove(key)
        dismissedAt[key] = nowMs()
        while (dismissedAt.size > maxEntries) {
            val oldest = dismissedAt.keys.firstOrNull() ?: break
            dismissedAt.remove(oldest)
        }
    }

    companion object {
        /** 抑制表上限：超出按插入序淘汰最旧。 */
        const val MaxEntries = 8
    }
}