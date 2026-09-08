package hunoia.luno.config.model

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.bridge.vibration.VibrationEffects
import kotlinx.serialization.Serializable
import kotlin.random.Random

private fun randomColor(): Int = AndroidColor.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))

@Serializable
@Keep
data class GestureButtonBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0.06f,
    val height: Float = 1f,
)

object GestureButtonDefaults {
    const val ID_DEFAULT = "1"
    const val Enabled = true
    val Bounds = GestureButtonBounds()
    val SlideActions = DirectionActions(
        mapOf(GestureDirection.Right to Action.toList(ActionFacade.BACK))
    )
    val LongSlideActions = DirectionActions()
    val SlideHoldActions = DirectionActions()
    val LongSlideHoldActions = DirectionActions()
    val LongSlideActionPanelStyles = LongSlideActionPanelStyles()
    val TapActions = listOf(GestureActionsDefaults.ActionNone)
    val DoubleTapActions = listOf(GestureActionsDefaults.ActionNone)
    val LongPressActions = listOf(GestureActionsDefaults.ActionNone)
    const val Color = android.graphics.Color.TRANSPARENT
    const val MirrorHorizontal = true
    val SlideTriggerDistance = DensityProvider.dp2px(30f)
    val LongSlideTriggerDistance = DensityProvider.dp2px(100f)
    const val LongPressTriggerDelayMs = 250L
    const val DoubleTapTriggerDelayMs = 300L
    const val SlideHoldTriggerDelayMs = 120L
    const val LongSlideHoldTriggerDelayMs = 120L
    const val SlideVibrate = true
    const val LongSlideVibrate = true
    const val TapVibrate = true
    const val LongPressVibrate = true
    const val VibrateImmediately = false
    const val CustomVibrationMs = 50L
    const val FitSoftKeyboard = true
    const val IsPreciseSlideType = true
    const val HideLandscape = false
    const val HideScreenLock = false
    const val HideHomeScreen = false
    val Defaults = listOf(GestureButton(id = ID_DEFAULT))
}

@Serializable
@Keep
data class GestureButton(
    val id: String,
    val name: String = "",
    val bounds: GestureButtonBounds = GestureButtonDefaults.Bounds,
    val enabled: Boolean = GestureButtonDefaults.Enabled,
    val slideActions: DirectionActions = GestureButtonDefaults.SlideActions,
    val slideHoldActions: DirectionActions = GestureButtonDefaults.SlideHoldActions,
    val longSlideActions: DirectionActions = GestureButtonDefaults.LongSlideActions,
    val longSlideHoldActions: DirectionActions = GestureButtonDefaults.LongSlideHoldActions,
    val longSlideActionPanelStyles: LongSlideActionPanelStyles = GestureButtonDefaults.LongSlideActionPanelStyles,
    val angle: GestureButtonAngle = GestureButtonAngle(),
    val actionSettingsOverride: GestureButtonActionSettingsOverride = GestureButtonActionSettingsOverride(),
    val tapActions: List<Action> = GestureButtonDefaults.TapActions,
    val doubleTapActions: List<Action> = GestureButtonDefaults.DoubleTapActions,
    val longPressActions: List<Action> = GestureButtonDefaults.LongPressActions,
    val color: Int = GestureButtonDefaults.Color,
    val mirrorHorizontal: Boolean = GestureButtonDefaults.MirrorHorizontal,
    val slideVibrate: Boolean = GestureButtonDefaults.SlideVibrate,
    val longSlideVibrate: Boolean = GestureButtonDefaults.LongSlideVibrate,
    val tapVibrate: Boolean = GestureButtonDefaults.TapVibrate,
    val longPressVibrate: Boolean = GestureButtonDefaults.LongPressVibrate,
    val vibrateImmediately: Boolean = GestureButtonDefaults.VibrateImmediately,
    val vibrationEffect: VibrationEffects = VibrationEffects.Click,
    val customVibrationMs: Long = GestureButtonDefaults.CustomVibrationMs,
    val slideTriggerDistance: Int = GestureButtonDefaults.SlideTriggerDistance,
    val longSlideTriggerDistance: Int = GestureButtonDefaults.LongSlideTriggerDistance,
    val longPressTriggerDelayMs: Long = GestureButtonDefaults.LongPressTriggerDelayMs,
    val doubleTapTriggerDelayMs: Long = GestureButtonDefaults.DoubleTapTriggerDelayMs,
    val slideHoldTriggerDelayMs: Long = GestureButtonDefaults.SlideHoldTriggerDelayMs,
    val longSlideHoldTriggerDelayMs: Long = GestureButtonDefaults.LongSlideHoldTriggerDelayMs,
    val fitSoftKeyboard: Boolean = GestureButtonDefaults.FitSoftKeyboard,
    val isPreciseSlideType: Boolean = GestureButtonDefaults.IsPreciseSlideType,
    val hideLandscape: Boolean = GestureButtonDefaults.HideLandscape,
    val hideScreenLock: Boolean = GestureButtonDefaults.HideScreenLock,
    val hideHomeScreen: Boolean = GestureButtonDefaults.HideHomeScreen,
) : Comparable<GestureButton> {

    companion object {
        const val ID_DEFAULT = GestureButtonDefaults.ID_DEFAULT
        val Defaults: List<GestureButton> get() = GestureButtonDefaults.Defaults

        fun create(name: String = ""): GestureButton {
            val colorInt = randomColor()
            return GestureButton(
                id = SystemClock.uptimeMillis().toString(),
                name = name,
                color = Color(colorInt).toArgb(),
            )
        }

    }

    val isDefault: Boolean = id == ID_DEFAULT

    override fun compareTo(other: GestureButton): Int = id.compareTo(other.id)
}
