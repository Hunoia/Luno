package hunoia.luno.gesture.settings

import hunoia.luno.config.model.GestureTriggerType

/**
 * 「去给这个手势位挑一个动作」——手势模块只描述目标位，不构造选择器的路由对象。
 *
 * 这样 `:feature:gesture` 不必依赖 `:feature:picker`（picker → gridpanel → gesture 会成环）；
 * 由 `:app` 把它翻译成选择器请求。
 */
data class GestureActionTarget(
    val gestureButtonId: String = "",
    val subGestureId: String = "",
    val slot: Int = 0,
    val directionLabel: String = "",
    val triggerType: GestureTriggerType = GestureTriggerType.Slide,
)
