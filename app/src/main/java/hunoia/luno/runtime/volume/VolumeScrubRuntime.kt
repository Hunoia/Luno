package hunoia.luno.runtime.volume

import android.content.Context
import hunoia.luno.action.api.VolumeScrubOverlay
import hunoia.luno.config.model.VolumeScrubConfig

class VolumeScrubRuntime(
    private val context: Context,
    private val onStateChanged: () -> Unit,
) {
    private var overlay: VolumeScrubOverlay? = null
    var isActive: Boolean = false
        private set

    fun show(config: VolumeScrubConfig = VolumeScrubConfig()): Boolean {
        if (!begin()) return false
        val o = VolumeScrubOverlay(context, config.horizontalEnabled, config.stepThresholdDp)
            .also { overlay = it }
        o.show(onDismiss = { end() })
        return true
    }

    private fun begin(): Boolean {
        if (isActive) return false
        isActive = true
        onStateChanged()
        return true
    }

    fun end() {
        if (!isActive && overlay == null) return
        isActive = false
        overlay?.dismiss()
        onStateChanged()
    }

    fun onDestroy() {
        // 不能只 dismiss：必须同时清掉 isActive 与 overlay，否则对象停留在
        // 「active 但已关闭」的自相矛盾状态，show() 的 begin() 会一直返回 false。
        // （当前 GestureCoordinator.onDestroy 后整个实例都会被重建，所以这不影响现网行为，
        //   但状态本身不该留着。）
        overlay?.dismiss()
        overlay = null
        isActive = false
    }
}
