package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.JsonSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Keep
data class ActionPanelStyles(
    val type: Int = ActionPanelStylesDefaults.Type,
    val json: String = ""
) {
    companion object {
        const val TYPE_ARC = ActionPanelStylesDefaults.TYPE_ARC

        fun arc(): ActionPanelStyles = ActionPanelStyles(TYPE_ARC)
        fun arc(style: ArcStyle): ActionPanelStyles = ActionPanelStyles(TYPE_ARC, JsonSerializer.encodeToString(style))
    }

    @Transient
    val value: ActionPanelStyle = run {
        val json = json
        if (json.isEmpty()) return@run ArcStyle()
        when (type) {
            TYPE_ARC -> JsonSerializer.decodeFromString<ArcStyle>(json)
            else -> error("Unknown ActionPanelStyle type: $type")
        }
    }
}

object ActionPanelStylesDefaults {

    const val TYPE_ARC = 1

    const val Type = TYPE_ARC
    val ArcStyleItemSize = DensityProvider.dp2px(48f)
    const val ArcStyleArcLength = 170
    const val ArcStyleSpreadSpacing = 1.12f
}

sealed interface ActionPanelStyle

@Serializable
@Keep
data class ArcStyle(
    val itemSize: Int = ActionPanelStylesDefaults.ArcStyleItemSize,
    val arcLength: Int = ActionPanelStylesDefaults.ArcStyleArcLength,
    val spreadSpacing: Float = ActionPanelStylesDefaults.ArcStyleSpreadSpacing
) : ActionPanelStyle

@Serializable
@Keep
data class LongSlideActionPanelStyles(
    val styles: Map<GestureDirection, ActionPanelStyles> = emptyMap()
) {
    fun styleBy(direction: GestureDirection): ActionPanelStyles = styles[direction] ?: ActionPanelStyles()

    fun withStyle(direction: GestureDirection, style: ActionPanelStyles): LongSlideActionPanelStyles {
        return copy(styles = styles + (direction to style))
    }
}

/** 颜色来源：自定义色值或跟随主题色。 */
enum class ColorSource { Custom, Theme }

/** 可跟随的主题色键。 */
enum class ThemeColorKey {
    Primary, PrimaryContainer, Secondary, SecondaryContainer,
    Tertiary, TertiaryContainer, Surface, SurfaceVariant,
    OnSurface, OnSurfaceVariant, Outline, OutlineVariant,
    SurfaceContainerLow, SurfaceContainer, SurfaceContainerHigh,
}
