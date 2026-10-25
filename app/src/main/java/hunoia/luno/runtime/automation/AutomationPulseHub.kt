package hunoia.luno.runtime.automation

import hunoia.luno.runtime.condition.NotificationPulse
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 通知脉冲 Hub：NotificationListenerService 与 GestureCoordinator / FloatBallService
 * 同在 `:service` 进程，静态直连，不走跨进程。权限被撤回时服务收不到回调，
 * 条件恒 false（不崩）。
 */
object AutomationPulseHub {

    private val notificationObservers = CopyOnWriteArrayList<(NotificationPulse) -> Unit>()

    /** 订阅通知到达。监听器在 binder 线程回调，观察者自行切线程。 */
    fun addNotificationObserver(observer: (NotificationPulse) -> Unit) {
        notificationObservers += observer
    }

    fun removeNotificationObserver(observer: (NotificationPulse) -> Unit) {
        notificationObservers -= observer
    }

    /** 由监听器触发：逐个隔离异常，一个观察者崩不影响其余。 */
    fun fireNotification(pulse: NotificationPulse) {
        notificationObservers.forEach { observer ->
            runCatching { observer(pulse) }
        }
    }

    /** 勿扰是否开启；连接时播种，未授权通知访问时恒 false。 */
    @Volatile
    var dndActive: Boolean = false
        private set

    private val interruptionObservers = CopyOnWriteArrayList<(Boolean) -> Unit>()

    /** 订阅勿扰变化；订阅即回放当前状态，观察者不用自己补基线。 */
    fun addInterruptionObserver(observer: (Boolean) -> Unit) {
        interruptionObservers += observer
        runCatching { observer(dndActive) }
    }

    fun removeInterruptionObserver(observer: (Boolean) -> Unit) {
        interruptionObservers -= observer
    }

    /** 由监听器推入：勿扰模式变化（true=开启）。 */
    fun fireInterruption(active: Boolean) {
        dndActive = active
        interruptionObservers.forEach { observer ->
            runCatching { observer(active) }
        }
    }
}
