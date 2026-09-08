package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerAcceleration
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerContinuousMode
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerContinuousModeTimeoutMs
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerCursorAlpha
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerCursorSizeDp
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerEdgeCancelThresholdDp
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerInitialYRatio
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerMovementDeadZoneDp
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerSensitivityX
import hunoia.luno.config.defaults.GestureSettingsDefaults.PointerSensitivityY
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class GestureSettings(
    val actionPanelVibrate: Boolean = true,
    val pointer: Pointer = Pointer()
) {
    @Serializable
    @Keep
    data class Pointer(
        val sensitivityX: Float = PointerSensitivityX,
        val sensitivityY: Float = PointerSensitivityY,
        val acceleration: Float = PointerAcceleration,
        val initialYRatio: Float = PointerInitialYRatio,
        val edgeCancelThresholdDp: Int = PointerEdgeCancelThresholdDp,
        val continuousMode: Boolean = PointerContinuousMode,
        val continuousModeTimeoutMs: Long = PointerContinuousModeTimeoutMs,
        val cursorSizeDp: Int = PointerCursorSizeDp,
        val cursorAlpha: Float = PointerCursorAlpha,
        val movementDeadZoneDp: Int = PointerMovementDeadZoneDp,
    )
}
