package hunoia.luno.runtime.floatball.alert

import androidx.compose.ui.graphics.Color
import hunoia.luno.config.model.AlertPalette

/**
 * 下发给渲染层的调色板：ARGB 色表（1–5 色）或彩虹模式。
 *
 * 纯 JVM（不含 Compose），随 [AlertLayerPlan] 携带；渲染端用 [toColors] 转成
 * Compose 颜色并给一个单色回退。`colors` 为 ARGB 整数（[hunoia.luno.config.model.ColorSpec.customColor] 同构）。
 */
data class AlertPaletteSpec(
    val colors: List<Int> = emptyList(),
    val rainbow: Boolean = false,
) {
    val isRainbow: Boolean get() = rainbow
    val isSingle: Boolean get() = !rainbow && colors.size == 1
}

internal fun AlertPalette.toSpec(): AlertPaletteSpec =
    if (rainbow) AlertPaletteSpec(colors = emptyList(), rainbow = true)
    else AlertPaletteSpec(colors = colors.take(5), rainbow = false)
