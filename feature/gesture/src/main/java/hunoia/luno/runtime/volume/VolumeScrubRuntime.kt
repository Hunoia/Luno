package hunoia.luno.runtime.volume

import android.content.Context
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.runtime.volume.VolumeScrubOverlay
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
        val o = VolumeScrubOverlay(context, config.stepThresholdDp)
            .also { overlay = it }
        o.show(onDismiss = { end() })
        Events.post(PanelShownEvent())
        return true
    }

    private fun begin(): Boolean {
        if (isActive) return false
        isActive = true
        onStateChanged()
        return true
    }

    fun end() {
        val wasActive = isActive || overlay != null
        if (!wasActive) return
        isActive = false
        overlay?.dismiss()
        onStateChanged()
        if (wasActive) Events.post(PanelDismissedEvent())
    }

    fun onDestroy() {
        // 不能只 dismiss：必须同时清掉 isActive 与 overlay，否则对象停留在
        // 「active 但已关闭」的自相矛盾状态，show() 的 begin() 会一直返回 false。
        // （当前 GestureCoordinator.onDestroy 后整个实例都会被重建，所以这不影响现网行为，
        //   但状态本身不该留着。）
        val wasActive = isActive || overlay != null
        overlay?.dismiss()
        overlay = null
        isActive = false
        // 正常关闭走 end() 的配对 Dismissed；销毁路径没人发，而 panelCount 活在
        // 跨重绑存活的 FloatBallService 里 —— 少发一次球就一直隐藏到下一次面板开合。
        if (wasActive) Events.post(PanelDismissedEvent())
    }
}
