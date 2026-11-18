package hunoia.luno.runtime.floatball.routing

import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.floatball.inbox.ResultItem

/**
 * 一条通知的去向。
 *
 * 规则优先：命中分发规则的通知走规则自己那条路（结果胶囊 / 直执行），**不再**进通知胶囊
 * （沿用旧「验证码不双弹」语义）；未命中的普通通知才进通知胶囊。
 */
internal sealed interface PulseRoute {

    /** 规则命中：结果胶囊档的项进结果胶囊（直执行档已就地执行，[items] 可能为空）。 */
    data class Result(val items: List<ResultItem>) : PulseRoute

    /** 普通通知：进通知胶囊。 */
    data class Notification(val item: NotificationItem) : PulseRoute

    /** 通知胶囊也关了：什么都不弹。 */
    data object Drop : PulseRoute
}

/**
 * 通知 → 胶囊的去路判定。
 *
 * 纯函数，不碰 Service / 窗口 / 线程，可 JVM 单测。结果项的生产（含直执行、后台切线程）
 * 由调用方做完再传入 —— 通知来源的读取开关关掉时传空列表；通知胶囊的开关同样由调用方
 * 读设置后传入（它只管「未命中的普通通知显不显示」，不影响规则）。
 *
 * 抑制表（滑掉后不重弹）仍在调用方：它会在过期时清理条目，是副作用，不属判定。
 */
internal object PulseRouter {

    fun route(
        pulse: NotificationPulse,
        resultItems: List<ResultItem>,
        matchedRules: Boolean,
        notificationCapsuleEnabled: Boolean,
        nowMs: Long,
    ): PulseRoute = when {
        // 命中过规则就不再进通知胶囊——直执行档虽然不产出条目，也已经「处理过」这条通知。
        matchedRules -> PulseRoute.Result(resultItems)
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
