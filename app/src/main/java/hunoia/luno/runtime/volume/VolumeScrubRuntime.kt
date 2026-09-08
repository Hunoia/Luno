package hunoia.luno.runtime.volume

import android.content.Context
import hunoia.luno.action.api.VolumeScrubOverlay
import hunoia.luno.config.model.ActionSettings

class VolumeScrubRuntime(
    private val context: Context,
    private val actionSettingsProvider: () -> ActionSettings?,
    private val onStateChanged: () -> Unit,
) {
    private var overlay: VolumeScrubOverlay? = null
    var isActive: Boolean = false
        private set

    fun show(): Boolean {
        if (!begin()) return false
        val scrubSettings = actionSettingsProvider()?.volumeScrub ?: ActionSettings.VolumeScrub()
        val o = VolumeScrubOverlay(context, scrubSettings.horizontalEnabled, scrubSettings.stepThresholdDp).also { overlay = it }
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

    fun onDestroy() { overlay?.dismiss() }
}
