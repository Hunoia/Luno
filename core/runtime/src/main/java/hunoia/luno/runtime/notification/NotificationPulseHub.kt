package hunoia.luno.runtime.notification

import hunoia.luno.core.domain.NotificationPulse
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 通知脉冲 Hub：通知监听器与同进程消费者（通知胶囊 / 手势运行态）之间的静态直连。
 *
 * 只承载「通知到达 / 移除 / 通知栏整体变化」三类事件。
 * 观察者在 binder 线程被回调，落地前自行切线程。
 */
object NotificationPulseHub {

    private val postedObservers = CopyOnWriteArrayList<(NotificationPulse) -> Unit>()
    private val removedObservers = CopyOnWriteArrayList<(NotificationPulse) -> Unit>()
    private val stateObservers = CopyOnWriteArrayList<() -> Unit>()

    fun addPostedObserver(observer: (NotificationPulse) -> Unit) {
        postedObservers += observer
    }

    fun removePostedObserver(observer: (NotificationPulse) -> Unit) {
        postedObservers -= observer
    }

    fun firePosted(pulse: NotificationPulse) {
        postedObservers.forEach { observer -> runCatching { observer(pulse) } }
    }

    fun addRemovedObserver(observer: (NotificationPulse) -> Unit) {
        removedObservers += observer
    }

    fun removeRemovedObserver(observer: (NotificationPulse) -> Unit) {
        removedObservers -= observer
    }

    fun fireRemoved(pulse: NotificationPulse) {
        removedObservers.forEach { observer -> runCatching { observer(pulse) } }
    }

    fun addStateObserver(observer: () -> Unit) {
        stateObservers += observer
    }

    fun removeStateObserver(observer: () -> Unit) {
        stateObservers -= observer
    }

    /** 通知栏快照整体变化（监听器连接 / 断连）：不产生到达脉冲。 */
    fun fireStateChanged() {
        stateObservers.forEach { observer -> runCatching { observer() } }
    }
}
