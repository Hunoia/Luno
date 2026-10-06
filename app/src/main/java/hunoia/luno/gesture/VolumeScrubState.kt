package hunoia.luno.gesture

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hunoia.luno.bridge.system.volumeDown
import hunoia.luno.bridge.system.volumeUp
import hunoia.luno.config.model.VolumeScrubConfig

class VolumeScrubState(private val context: Context) {
    var isActive by mutableStateOf(false)
        private set
    var accumulator by mutableStateOf(0f)
        private set
    var accumulatorX by mutableStateOf(0f)
        private set

    private var config: VolumeScrubConfig = VolumeScrubConfig()
    private val stepThreshold: Float
        get() = context.resources.displayMetrics.density * config.stepThresholdDp

    fun activate(config: VolumeScrubConfig) {
        this.config = config
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
        if (config.horizontalEnabled) {
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
        isActive = false
        accumulator = 0f
        accumulatorX = 0f
        return true
    }

    fun onDragCancel(): Boolean {
        if (!isActive) return false
        isActive = false
        accumulator = 0f
        accumulatorX = 0f
        return true
    }
}
