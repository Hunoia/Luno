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
)

/**
 * 请求来源。只用于日志、三态反馈与触发侧回执，**不得**参与 Executor 选择或执行分支。
 */
enum class DispatchOrigin {
    GESTURE,
    PANEL,
    QUICK_SEARCH,
    AUTOMATION,
    LINK,
    EXTERNAL_INTENT,
    UNKNOWN,
}
