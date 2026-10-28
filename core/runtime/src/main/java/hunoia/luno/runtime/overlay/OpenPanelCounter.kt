package hunoia.luno.runtime.overlay

import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

/**
 * 在屏面板计数 + 「等面板退场」的共享实现，供 transient Surface（屏幕取点 / 识屏取链）使用。
 *
 * 面板（搜索、网格、动作面板）还在淡出时，底下应用的无障碍窗口内容取不到、截图也会拍到面板；
 * 两处都要等 `PanelDismissed` 或超时兜底，这里收口成一处。
 */
class OpenPanelCounter(
    private val maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
    private val pollMs: Long = DEFAULT_POLL_MS,
) : AutoCloseable {

    private val count = AtomicInteger(0)

    private val onShown: (PanelShownEvent) -> Unit = { count.incrementAndGet() }
    private val onDismissed: (PanelDismissedEvent) -> Unit = {
        count.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
    }

    init {
        Events.subscribe(PanelShownEvent::class, onShown)
        Events.subscribe(PanelDismissedEvent::class, onDismissed)
    }

    val openCount: Int get() = count.get()

    val hasOpenPanels: Boolean get() = count.get() > 0

    /** 等到没有在屏面板或超时；返回是否等到全部退场。 */
    suspend fun awaitDismissed(): Boolean {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (count.get() > 0 && System.currentTimeMillis() < deadline) {
            delay(pollMs)
        }
        return count.get() == 0
    }

    override fun close() {
        Events.unsubscribe(PanelShownEvent::class, onShown)
        Events.unsubscribe(PanelDismissedEvent::class, onDismissed)
    }

    companion object {
        const val DEFAULT_MAX_WAIT_MS = 800L
        const val DEFAULT_POLL_MS = 50L
    }
}
