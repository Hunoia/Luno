package hunoia.luno.runtime.floatball.inbox

/**
 * 一次 reducer 调用的结果。除了新状态，还带出服务层做 UI / 计时决策需要的信息：
 * 新增、去重更新、显式移除、TTL 过期、容量丢弃、当前项是否变化。
 */
internal data class BallInboxChange(
    val state: BallInboxState,
    val added: BallItem? = null,
    val updated: BallItem? = null,
    val removed: List<BallItem> = emptyList(),
    val expired: List<BallItem> = emptyList(),
    val dropped: BallItem? = null,
    val duplicate: Boolean = false,
    val currentChanged: Boolean = false,
) {
    val hasNewItem: Boolean get() = added != null
    val isEmpty: Boolean get() = state.items.isEmpty()
    val changed: Boolean
        get() = added != null ||
            updated != null ||
            removed.isNotEmpty() ||
            expired.isNotEmpty() ||
            currentChanged
}
