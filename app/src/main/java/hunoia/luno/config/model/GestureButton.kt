package hunoia.luno.config.model

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hunoia.luno.R
import hunoia.luno.action.dispatcher.MigratingStoredActionSerializer
import hunoia.luno.action.model.StoredAction
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.AppContext
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
    val Bounds = GestureButtonBounds(y = 0.50529f, height = 0.30311f)
    val SlideActions = DirectionActions(
        mapOf(
            GestureDirection.Right to listOf(StoredAction.of("accessibility.back")),
            GestureDirection.Up to listOf(StoredAction.of("accessibility.recents")),
            GestureDirection.Down to listOf(StoredAction.of("accessibility.notificationPanel")),
        )
    )
    val LongSlideActions = DirectionActions()
    val SlideHoldActions = DirectionActions(
        mapOf(GestureDirection.Right to listOf(StoredAction.of("internal.quickAppLauncher")))
    )
    val LongSlideHoldActions = DirectionActions(
        mapOf(GestureDirection.Right to listOf(StoredAction.of("internal.quickSearch")))
    )
    val LongSlideActionPanelStyles = LongSlideActionPanelStyles()
    val TapActions = listOf(StoredAction.of("internal.hideGestureButton"))
    val DoubleTapActions = emptyList<StoredAction>()
    val LongPressActions = listOf(StoredAction.of("intent.assist"))
    const val Color = android.graphics.Color.TRANSPARENT
    const val MirrorHorizontal = true
    val SlideTriggerDistance = DensityProvider.dp2px(30f)
    val LongSlideTriggerDistance = DensityProvider.dp2px(100f)
    const val LongPressTriggerDelayMs = 250L
    const val DoubleTapTriggerDelayMs = 300L
    const val SlideHoldTriggerDelayMs = 120L
    const val LongSlideHoldTriggerDelayMs = 120L
    const val SlideVibrate = false
    const val LongSlideVibrate = false
    const val TapVibrate = false
    const val LongPressVibrate = false
    const val SlideHoldVibrate = false
    const val LongSlideHoldVibrate = false
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
    val tapActions: List<@Serializable(MigratingStoredActionSerializer::class) StoredAction> =
        GestureButtonDefaults.TapActions,
    val doubleTapActions: List<@Serializable(MigratingStoredActionSerializer::class) StoredAction> =
        GestureButtonDefaults.DoubleTapActions,
    val longPressActions: List<@Serializable(MigratingStoredActionSerializer::class) StoredAction> =
        GestureButtonDefaults.LongPressActions,
    val color: Int = GestureButtonDefaults.Color,
    val mirrorHorizontal: Boolean = GestureButtonDefaults.MirrorHorizontal,
    val slideVibrate: Boolean = GestureButtonDefaults.SlideVibrate,
    val longSlideVibrate: Boolean = GestureButtonDefaults.LongSlideVibrate,
    val tapVibrate: Boolean = GestureButtonDefaults.TapVibrate,
    val longPressVibrate: Boolean = GestureButtonDefaults.LongPressVibrate,
    val slideHoldVibrate: Boolean = GestureButtonDefaults.SlideHoldVibrate,
    val longSlideHoldVibrate: Boolean = GestureButtonDefaults.LongSlideHoldVibrate,
    val slideTriggerDistance: Int = GestureButtonDefaults.SlideTriggerDistance,
    val longSlideTriggerDistance: Int = GestureButtonDefaults.LongSlideTriggerDistance,
    val longPressTriggerDelayMs: Long = GestureButtonDefaults.LongPressTriggerDelayMs,
    val doubleTapTriggerDelayMs: Long = GestureButtonDefaults.DoubleTapTriggerDelayMs,
    val slideHoldTriggerDelayMs: Long = GestureButtonDefaults.SlideHoldTriggerDelayMs,
    val longSlideHoldTriggerDelayMs: Long = GestureButtonDefaults.LongSlideHoldTriggerDelayMs,
) : Comparable<GestureButton> {

    /**
     * 远档阈值不得小于近档：否则短滑会落进 LongSlide 分支，而 SlideHold 因
     * `canTriggerSlide && !canTriggerLong` 恒不成立而永久失联（数据层兜底，
     * 与 SubGesture.effectiveLongSlideTriggerDistance 一致；UI 侧另有 gap 校验）。
     */
    val effectiveLongSlideTriggerDistance: Int
        get() = longSlideTriggerDistance.coerceAtLeast(slideTriggerDistance)

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

/** 按钮显示名：有名字用名字，否则取主题字符串（与 UI 侧 buttonTextCompose 同约定）。 */
internal fun GestureButton.resolveDisplayName(): String {
    if (name.isNotEmpty()) return name
    return AppContext.get().getString(R.string.gesture_button)
}
