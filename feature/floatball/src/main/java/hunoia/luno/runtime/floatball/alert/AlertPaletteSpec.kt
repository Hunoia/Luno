package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.rainbowRing
import kotlin.math.roundToInt

/**
 * 下发给渲染层的调色板：ARGB 色表（1–[AlertLimits.MaxPaletteColors] 色）或彩虹模式。
 *
 * 纯 JVM（不含 Compose），随 [AlertLayerPlan] 携带；渲染端用 [toColors] 转成
 * Compose 颜色并给一个单色回退。`colors` 为 ARGB 整数（[hunoia.luno.config.model.ColorSpec.customColor] 同构）。
 * 彩虹模式下 `colors` 已填参数化色相环采样（见 [AlertPalette.rainbowRing]），与设置页预览共用。
 */
data class AlertPaletteSpec(
    val colors: List<Int> = emptyList(),
    val rainbow: Boolean = false,
) {
    val isRainbow: Boolean get() = rainbow
    val isSingle: Boolean get() = !rainbow && colors.size == 1
}

internal fun AlertPalette.toSpec(): AlertPaletteSpec =
    if (rainbow) AlertPaletteSpec(colors = rainbowRing(), rainbow = true)
    else AlertPaletteSpec(colors = colors.take(AlertLimits.MaxPaletteColors), rainbow = false)

/**
 * 多色**环形**取色（纯 JVM，可单测）：把 [t] 归一化到 0..1 后定位相邻两色并线性插值，
 * 末色与首色相接。
 *
 * 「环形插值」而非「分桶」是取色口径本身：5 色分桶在屏缘上就是 5 段硬色带，与
 * ui-guidelines §2.4 的「沿边缘渐变」不符。单色恒返回唯一色；空表返回 0（透明），
 * 调用方（[AlertPaletteSpec.toColors]）已先把空表回退成单色。
 */
internal fun sampleArgb(colors: IntArray, t: Float): Int {
    if (colors.isEmpty()) return 0
    if (colors.size == 1) return colors[0]
    val n = colors.size
    val norm = ((t % 1f) + 1f) % 1f
    val x = norm * n
    val index = x.toInt().coerceIn(0, n - 1)
    return lerpArgb(colors[index], colors[(index + 1) % n], x - index)
}

/** 两色 ARGB 线性插值（四通道同权；用于周长 / 角度取色的相邻色过渡）。 */
internal fun lerpArgb(from: Int, to: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = (from ushr shift) and 0xFF
        val b = (to ushr shift) and 0xFF
        return (a + (b - a) * f).roundToInt().coerceIn(0, 0xFF)
    }
    return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
