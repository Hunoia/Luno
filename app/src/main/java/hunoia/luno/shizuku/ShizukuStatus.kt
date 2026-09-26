package hunoia.luno.shizuku

data class ShizukuStatus(
    val installed: Boolean,
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val uid: Int?
) {
    val executorLabel: String
        get() = when (uid) {
            0 -> "uid=0"
            2000 -> "uid=2000"
            null -> "-"
            else -> "uid=$uid"
        }

    val isReady: Boolean
        get() = binderAlive && permissionGranted
}
