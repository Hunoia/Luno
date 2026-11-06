package hunoia.luno.service

/**
 * 分发 IPC 的连接状态机（纯 Kotlin，便于单测）。
 *
 * 关键语义：
 * - 只有 `bindService` 真的返回 true 才记 `bindRequested`；失败要允许下一轮重试，
 *   否则整个进程生命周期都不会再 bind。
 * - `onServiceDisconnected` 不清 `bindRequested`：系统会对同一个连接自动重连。
 * - `onBindingDied` / `onNullBinding` 必须清 `bindRequested`：宿主进程已死或绑定无效，
 *   必须 unbind 后重新 bind，否则永远不会恢复。
 */
internal class DispatchConnection(
    private val retryIntervalMs: Long = 2_000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    var bindRequested: Boolean = false
        private set

    private var lastBindRequestMs = -retryIntervalMs

    /** 记录一次 bindService 结果；返回是否已发起绑定。 */
    fun onBindAttempt(bound: Boolean): Boolean {
        if (bound) {
            bindRequested = true
            lastBindRequestMs = clock()
        }
        return bound
    }

    /** 未绑定时按限频决定是否重试；已绑定则不重试。 */
    fun shouldRetryBind(): Boolean {
        if (bindRequested) return false
        val now = clock()
        if (now - lastBindRequestMs < retryIntervalMs) return false
        lastBindRequestMs = now
        return true
    }

    fun onServiceDisconnected() {
        // 系统会为同一连接自动重连，保留 bindRequested。
    }

    fun onBindingDied() {
        bindRequested = false
    }

    fun onNullBinding() {
        bindRequested = false
    }
}
