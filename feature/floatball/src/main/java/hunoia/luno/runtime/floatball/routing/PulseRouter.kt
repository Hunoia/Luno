package hunoia.luno.runtime.floatball.routing

import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.floatball.inbox.SuggestionItem

/**
 * 一条通知的去向。
 *
 * 建议项优先：规则命中的通知全部进建议胶囊，且**不再**进通知胶囊
 * （沿用旧验证码不双弹语义）。
 */
internal sealed interface PulseRoute {

    /** 规则命中：全部进建议胶囊。 */
    data class Suggestion(val items: List<SuggestionItem>) : PulseRoute

    /** 普通通知：进通知胶囊。 */
    data class Notification(val item: NotificationItem) : PulseRoute

    /** 通知胶囊也关了：什么都不弹。 */
    data object Drop : PulseRoute
}

/**
 * 通知 → 胶囊的去路判定。
 *
 * 纯函数，不碰 Service / 窗口 / 线程，可 JVM 单测。建议项的生产（含后台切线程）由调用方做完
 * 再传入 —— 建议胶囊关闭时传空列表；通知胶囊的开关同样由调用方读设置后传入。
 *
 * 抑制表（滑掉后不重弹）仍在调用方：它会在过期时清理条目，是副作用，不属判定。
 */
internal object PulseRouter {

    fun route(
        pulse: NotificationPulse,
        suggestionItems: List<SuggestionItem>,
        notificationCapsuleEnabled: Boolean,
        nowMs: Long,
    ): PulseRoute = when {
        suggestionItems.isNotEmpty() -> PulseRoute.Suggestion(suggestionItems)
        !notificationCapsuleEnabled -> PulseRoute.Drop
        else -> PulseRoute.Notification(
            NotificationItem(
                notificationKey = notificationKey(pulse),
                packageName = pulse.packageName,
                title = pulse.title,
                text = pulse.text,
                receivedAtElapsedMs = nowMs,
            )
        )
    }

    /** 系统没给 key（自家通知等）时按包名 + 标题派生一个，保证队列去重键稳定。 */
    internal fun notificationKey(pulse: NotificationPulse): String =
        pulse.key ?: "${pulse.packageName}:${pulse.title}"
}