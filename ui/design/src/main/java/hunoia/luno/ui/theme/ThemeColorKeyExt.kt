package hunoia.luno.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import hunoia.luno.design.R
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.ThemeColorKey

@get:StringRes
val ThemeColorKey.displayNameRes: Int
    get() = when (this) {
        ThemeColorKey.Primary -> R.string.theme_color_primary
        ThemeColorKey.PrimaryContainer -> R.string.theme_color_primary_container
        ThemeColorKey.Secondary -> R.string.theme_color_secondary
        ThemeColorKey.SecondaryContainer -> R.string.theme_color_secondary_container
        ThemeColorKey.Tertiary -> R.string.theme_color_tertiary
        ThemeColorKey.TertiaryContainer -> R.string.theme_color_tertiary_container
        ThemeColorKey.Surface -> R.string.theme_color_surface
        ThemeColorKey.SurfaceVariant -> R.string.theme_color_surface_variant
        ThemeColorKey.OnSurface -> R.string.theme_color_on_surface
        ThemeColorKey.OnSurfaceVariant -> R.string.theme_color_on_surface_variant
        ThemeColorKey.Outline -> R.string.theme_color_outline
        ThemeColorKey.OutlineVariant -> R.string.theme_color_outline_variant
        ThemeColorKey.SurfaceContainerLow -> R.string.theme_color_surface_container_low
        ThemeColorKey.SurfaceContainer -> R.string.theme_color_surface_container
        ThemeColorKey.SurfaceContainerHigh -> R.string.theme_color_surface_container_high
    }

@androidx.compose.runtime.Composable
fun ThemeColorKey.resolveColor(): Color = when (this) {
    ThemeColorKey.Primary -> MaterialTheme.colorScheme.primary
    ThemeColorKey.PrimaryContainer -> MaterialTheme.colorScheme.primaryContainer
    ThemeColorKey.Secondary -> MaterialTheme.colorScheme.secondary
    ThemeColorKey.SecondaryContainer -> MaterialTheme.colorScheme.secondaryContainer
    ThemeColorKey.Tertiary -> MaterialTheme.colorScheme.tertiary
    ThemeColorKey.TertiaryContainer -> MaterialTheme.colorScheme.tertiaryContainer
    ThemeColorKey.Surface -> MaterialTheme.colorScheme.surface
    ThemeColorKey.SurfaceVariant -> MaterialTheme.colorScheme.surfaceVariant
    ThemeColorKey.OnSurface -> MaterialTheme.colorScheme.onSurface
    ThemeColorKey.OnSurfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
    ThemeColorKey.Outline -> MaterialTheme.colorScheme.outline
    ThemeColorKey.OutlineVariant -> MaterialTheme.colorScheme.outlineVariant
    ThemeColorKey.SurfaceContainerLow -> MaterialTheme.colorScheme.surfaceContainerLow
    ThemeColorKey.SurfaceContainer -> MaterialTheme.colorScheme.surfaceContainer
    ThemeColorKey.SurfaceContainerHigh -> MaterialTheme.colorScheme.surfaceContainerHigh
}

fun ThemeColorKey.resolveColor(scheme: ColorScheme): Color = when (this) {
    ThemeColorKey.Primary -> scheme.primary
    ThemeColorKey.PrimaryContainer -> scheme.primaryContainer
    ThemeColorKey.Secondary -> scheme.secondary
    ThemeColorKey.SecondaryContainer -> scheme.secondaryContainer
    ThemeColorKey.Tertiary -> scheme.tertiary
    ThemeColorKey.TertiaryContainer -> scheme.tertiaryContainer
    ThemeColorKey.Surface -> scheme.surface
    ThemeColorKey.SurfaceVariant -> scheme.surfaceVariant
    ThemeColorKey.OnSurface -> scheme.onSurface
    ThemeColorKey.OnSurfaceVariant -> scheme.onSurfaceVariant
    ThemeColorKey.Outline -> scheme.outline
    ThemeColorKey.OutlineVariant -> scheme.outlineVariant
    ThemeColorKey.SurfaceContainerLow -> scheme.surfaceContainerLow
    ThemeColorKey.SurfaceContainer -> scheme.surfaceContainer
    ThemeColorKey.SurfaceContainerHigh -> scheme.surfaceContainerHigh
}

@Composable
fun resolveColor(source: ColorSource, themeKey: ThemeColorKey, customColor: Int): Color = when (source) {
    ColorSource.Custom -> Color(customColor)
    ColorSource.Theme -> themeKey.resolveColor()
}

/** 按颜色来源解析为具体颜色；主题色用传入的 [scheme] 实时解析。 */
fun ColorSpec.toColor(scheme: ColorScheme): Color = when (source) {
    ColorSource.Custom -> Color(customColor)
    ColorSource.Theme -> themeKey.resolveColor(scheme)
}

@Composable
fun ColorSpec.toColor(): Color = toColor(MaterialTheme.colorScheme)

/**
 * 触钮 / 子手势 / 悬浮球的标记色：未设置（透明）时跟随主题主色，
 * 与历史 `GestureButton.color == 0` 的语义一致。
 */
fun ColorSpec.toMarkColor(scheme: ColorScheme, alpha: Float = 1f): Color =
    if (isUnset) scheme.primary.copy(alpha = alpha) else toColor(scheme)

@Composable
fun ColorSpec.toMarkColor(alpha: Float = 1f): Color =
    toMarkColor(MaterialTheme.colorScheme, alpha)
