package hunoia.luno.runtime.recentspicker

/**
 * 最近应用滚动选择器浮窗生命周期：只做 show/end 编排，与 [hunoia.luno.runtime.volume.VolumeScrubRuntime] 同构。
 *
 * 滚动/点选状态在 [RecentsPickerState]（管线持有）；这里只管窗口的开关与关闭后的
 * 回调（协调器刷新触钮等）。窗口关闭时 [RecentsPickerOverlay] 会清掉传入的 state
 * （即管线持有的同一实例），管线据此停止路由。
 */
class RecentsPickerRuntime(
    private val onStateChanged: () -> Unit,
) {
    fun show(state: RecentsPickerState) {
        RecentsPickerOverlay.show(state, onClosed = onStateChanged)
    }

    fun end() {
        RecentsPickerOverlay.close()
    }

    fun onDestroy() {
        RecentsPickerOverlay.close()
    }
}
