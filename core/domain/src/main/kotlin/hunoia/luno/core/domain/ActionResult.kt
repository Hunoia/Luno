package hunoia.luno.core.domain

/** 动作所需能力；执行前由能力闸读取，不硬编码类型判断。 */
enum class Capability {
    None,
    Accessibility,
    Shizuku,
    /** Android 13+ POST_NOTIFICATIONS / 通知总开关。 */
    Notification,
}

/** 一次执行的落定结果：成功 / 失败带因 / 需授权带去向。 */
sealed interface ActionResult {
    /** outputs: runRule 等复合动作回传的变量（目前只有 "return"）。 */
    data class Success(
        val message: String? = null,
        val outputs: Map<String, String> = emptyMap(),
    ) : ActionResult
    data class Failed(val reason: ActionFailure, val message: String? = null) : ActionResult
    data class RequiresCapability(val capability: Capability) : ActionResult
}

enum class ActionFailure {
    InvalidParameter,
    ExecutionFailed,
    PermissionDenied,
    Unsupported,
}
