package hunoia.luno.shizuku

data class ShizukuStatus(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val uid: Int?
) {
    val isReady: Boolean
        get() = binderAlive && permissionGranted
}
