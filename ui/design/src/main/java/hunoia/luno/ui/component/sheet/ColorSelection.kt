package hunoia.luno.ui.component.sheet

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.ui.theme.resolveColor

/** 颜色选择器的输出：自定义颜色，或跟随某个主题色角色。 */
sealed class ColorSelection {
    data class Custom(val color: Color) : ColorSelection()
    data class Theme(val key: ThemeColorKey) : ColorSelection()
}

/**
 * 持久化快照 → 选择器初始态。
 *
 * [primaryFallback] = true 时，未设置（透明）的自定义色回落到「主要色」主题态，
 * 与触钮 / 子手势 / 悬浮球的历史渲染语义保持一致。
 */
fun ColorSpec.toInitialSelection(
    scheme: ColorScheme,
    primaryFallback: Boolean = false,
): ColorSelection = when {
    source == ColorSource.Theme -> ColorSelection.Theme(themeKey)
    primaryFallback && isUnset -> ColorSelection.Theme(ThemeColorKey.Primary)
    else -> ColorSelection.Custom(Color(customColor))
}

/**
 * 选择器结果 → 持久化快照。
 *
 * 主题色也会把当前解析值写进 [ColorSpec.customColor]：旧版本 app 或旧备份
 * 读不到新字段时，仍能按这个 ARGB 快照回退显示。
 */
fun ColorSelection.toColorSpec(scheme: ColorScheme): ColorSpec = when (this) {
    is ColorSelection.Custom -> ColorSpec(
        source = ColorSource.Custom,
        themeKey = ThemeColorKey.Primary,
        customColor = color.toArgb(),
    )
    is ColorSelection.Theme -> ColorSpec(
        source = ColorSource.Theme,
        themeKey = key,
        customColor = key.resolveColor(scheme).toArgb(),
    )
}
