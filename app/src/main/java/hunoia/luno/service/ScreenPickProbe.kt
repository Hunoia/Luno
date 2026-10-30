package hunoia.luno.service

/**
 * 识屏快照探测钩子：debug 变体注册（见 app/src/debug 的 [ScreenPickProbeReceiver]），
 * release 恒为 null、零开销。用于在真机上直接验证 [A11yTextSnapshotReader] 对当前
 * 界面抓到了什么（排查“界面明明有字却识别不到”）。
 */
object ScreenPickProbe {
    @Volatile
    var hook: ((x: Int, y: Int) -> Unit)? = null
}
