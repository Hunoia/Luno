package hunoia.luno.gesture.event

import hunoia.luno.config.model.GestureTriggerType

/**
 * 分发完成事件（Dispatcher→EventBus）。自动化侧订阅它驱动 GESTURE 域评估：
 * 手势动作立即执行，规则评估跟随分发落定，不在手势入口同步调用 evaluate。
 */
data class GestureDispatchEvent(
    /** 落定动作的 typeId（库引用已就地解析为条目动作）。 */
    val typeId: String,
    val buttonId: String?,
    /** 触发手势类型；面板点选等非直接触发为 null。 */
    val trigger: GestureTriggerType?,
    /** 是否源自手势链路（overlay 直调如搜索点选为 false，不进 GESTURE 评估）。 */
    val fromGesture: Boolean,
    val ok: Boolean,
)
