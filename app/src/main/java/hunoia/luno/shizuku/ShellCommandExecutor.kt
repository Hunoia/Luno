package hunoia.luno.shizuku

data class ShellCommandResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val elapsedMs: Long,
    val error: String? = null,
)


