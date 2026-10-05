package hunoia.luno.action.dispatcher

import hunoia.luno.config.model.GestureTriggerType

/**
 * 分发完成事件（Dispatcher→EventBus）。
 *
 * 由 [NewActionDispatcher] 在每次分发落定后广播。自动化侧订阅此事件驱动
 * GESTURE 域评估，不再由手势入口同步调用 evaluate：
 * 手势动作立即执行，规则评估跟随分发落定。
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
