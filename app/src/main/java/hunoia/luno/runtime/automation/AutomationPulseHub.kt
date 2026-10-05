package hunoia.luno.runtime.automation

import hunoia.luno.runtime.condition.NotificationPulse

/**
 * 通知脉冲 Hub：NotificationListenerService 与 GestureCoordinator 同在 `:service` 进程，
 * 静态直连，不走跨进程。权限被撤回时服务收不到回调，条件恒 false（不崩）。
 */
object AutomationPulseHub {
    @Volatile
    var onNotification: ((NotificationPulse) -> Unit)? = null
}
