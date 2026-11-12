package hunoia.luno.core.domain

/**
 * 一次动作执行请求：统一进 Dispatcher 的唯一输入。
 *
 * 只承载「执行什么」与「从哪来」，不携带触发源专属状态；触发源需要补的上下文
 * （手势按钮、面板锚点、坐标）由各自的 Trigger 侧在提交前处理完毕。
 */
data class ActionRequest(
    val stored: StoredAction,
    val origin: DispatchOrigin,
    /** 幂等键：同一次用户操作重复投递时由执行口去重；为空表示不做幂等。 */
    val idempotencyKey: String? = null,
    /**
     * 触发上下文：只允许**同进程**的 Trigger 侧补齐，IPC 路径必须保持为空。
     *
     * 当前只承载全屏手势的锚点与会话 id（识屏幽灵卡需要按下点）；动作本身仍然
     * 无参，执行口只把它当作“从哪里触发”的提示，不参与 Executor 选择。
     */
    val triggerContext: TriggerContext? = null,
)

/** 触发源在提交前补好的同进程上下文；不得跨进程传输。 */
data class TriggerContext(
    /** 按下点（屏幕像素）；无坐标的动作保持 null。 */
    val position: Pair<Int, Int>? = null,
    /** 一次手势会话的 id：执行侧据此取回按下瞬间的预捕获快照。 */
    val sessionId: String? = null,
)

/**
 * 请求来源。只用于日志、三态反馈与触发侧回执，**不得**参与 Executor 选择或执行分支。
 */
enum class DispatchOrigin {
    GESTURE,
    PANEL,
    QUICK_SEARCH,
    LINK,
    EXTERNAL_INTENT,
    UNKNOWN,
}
