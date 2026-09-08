package hunoia.luno.gesture

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hunoia.luno.bridge.volumeDown
import hunoia.luno.bridge.volumeUp
import hunoia.luno.config.model.ActionSettings

class VolumeScrubState(
    private val context: Context,
    private val onModeChanged: (Boolean) -> Unit,
) {
    var isActive by mutableStateOf(false)
        private set
    var accumulator by mutableStateOf(0f)
        private set
    var accumulatorX by mutableStateOf(0f)
        private set

    private var actionSettings: ActionSettings = ActionSettings()
    private val stepThreshold: Float
        get() = context.resources.displayMetrics.density * actionSettings.volumeScrub.stepThresholdDp

    fun activate(settings: ActionSettings) {
        actionSettings = settings
        isActive = true
        accumulator = 0f
        accumulatorX = 0f
    }

    fun onDrag(dragAmount: androidx.compose.ui.geometry.Offset): Boolean {
        if (!isActive) return false
        accumulator += dragAmount.y
        while (accumulator >= stepThreshold) {
            context.volumeDown()
            accumulator -= stepThreshold
        }
        while (accumulator <= -stepThreshold) {
            context.volumeUp()
            accumulator += stepThreshold
        }
        if (actionSettings.volumeScrub.horizontalEnabled) {
            accumulatorX += dragAmount.x
            while (accumulatorX >= stepThreshold) {
                context.volumeUp()
                accumulatorX -= stepThreshold
            }
            while (accumulatorX <= -stepThreshold) {
                context.volumeDown()
                accumulatorX += stepThreshold
            }
        }
        return true
    }

    fun onDragEnd(): Boolean {
        if (!isActive) return false
        onModeChanged(false)
        isActive = false
        accumulator = 0f
        accumulatorX = 0f
        return true
    }

    fun onDragCancel(): Boolean {
        if (!isActive) return false
        onModeChanged(false)
        isActive = false
        accumulator = 0f
        accumulatorX = 0f
        return true
    }
}
