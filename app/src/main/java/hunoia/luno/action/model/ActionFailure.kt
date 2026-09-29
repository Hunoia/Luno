package hunoia.luno.action.model

enum class ActionFailure {
    AppNotFound,
    ActivityNotFound,
    InvalidParameter,
    ExecutionFailed,
    PermissionDenied,
    Timeout,
    Unsupported
}
