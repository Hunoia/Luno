package hunoia.luno.core.domain

/** 动作所需能力；执行前由能力闸读取，不硬编码类型判断。 */
enum class Capability {
    None,
    Accessibility,
    Shizuku,
}

/** 一次执行的落定结果：成功 / 失败带因 / 需授权带去向。 */
sealed interface ActionResult {
    /** outputs: runRule 等复合动作回传的变量（目前只有 "return"）。 */
    data class Success(
        val message: String? = null,
        val outputs: Map<String, String> = emptyMap(),
    ) : ActionResult
    data class Failed(
        val reason: ActionFailure,
        /** 诊断/结构化错误信息（如异常消息、内部 key）；不直接展示给用户。 */
        val message: String? = null,
        /** 用户可见的失败文案（本地化）；null 时由 reason 映射到通用文案。 */
        val userMessage: String? = null,
    ) : ActionResult
    data class RequiresCapability(val capability: Capability) : ActionResult
}

enum class ActionFailure {
    InvalidParameter,
    ExecutionFailed,
    PermissionDenied,
    Unsupported,
}
