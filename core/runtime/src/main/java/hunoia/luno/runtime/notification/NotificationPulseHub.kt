package hunoia.luno.runtime.notification

import hunoia.luno.core.domain.NotificationPulse
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 通知脉冲 Hub：通知监听器与同进程消费者（通知胶囊 / 手势运行态）之间的静态直连。
 *
 * 只承载「通知到达 / 移除」两类事件。
 * 观察者在 binder 线程被回调，落地前自行切线程。
 */
object NotificationPulseHub {

    private val postedObservers = CopyOnWriteArrayList<(NotificationPulse) -> Unit>()
    private val removedObservers = CopyOnWriteArrayList<(NotificationPulse) -> Unit>()

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
}
