package hunoia.luno.shizuku

data class BatchFrozenResult(
    val requestedCount: Int,
    val attemptedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val fallbackTriggered: Boolean,
    val fallbackAttemptedCount: Int = 0,
    val fallbackSuccessCount: Int = 0,
    val fallbackFailedCount: Int = 0,
    val errorSummary: String? = null
)
