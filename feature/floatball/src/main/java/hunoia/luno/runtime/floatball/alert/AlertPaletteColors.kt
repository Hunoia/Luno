package hunoia.luno.runtime.floatball.alert

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/**
 * 解析好的调色板（Compose 侧）：1–[hunoia.luno.config.model.AlertLimits.MaxPaletteColors] 色或彩虹。
 *
 * 取色口径（需求确认）：边缘按周长、粒子按序号、胶囊 / 球环按角度；单色恒纯色。
 * 周长 / 角度采样走 [sampleArgb] 的**环形插值**（末色接回首色）—— 多色在屏缘上读作
 * 连续渐变，不会出现几段硬色带；粒子按序号取色本就是逐颗离散，仍走 [at]。
 */
internal class AlertPaletteColors(
    val colors: List<Color>,
    val rainbow: Boolean,
) {
    init {
        require(colors.isNotEmpty()) { "palette needs at least one color" }
    }

    /** 预转 ARGB：绘制期按周长采样是逐帧热路径，不在每帧做 Color → Int 往返。 */
    private val argb: IntArray = IntArray(colors.size) { colors[it].toArgb() }

    val isSingle: Boolean get() = colors.size == 1 && !rainbow

    /** 归一化位置（周长 / 相位）取色；单色返回唯一色，多色环形插值。 */
    fun sample(t: Float): Color = if (isSingle) colors[0] else Color(sampleArgb(argb, t))

    /** 按序号取色（粒子）；单色返回唯一色。 */
    fun at(index: Int): Color {
        if (isSingle) return colors[0]
        return colors[abs(index) % colors.size]
    }
}

/** [AlertPaletteSpec] → 渲染用颜色；[fallback] 是未选调色板时的单色（主题解析后）。 */
internal fun AlertPaletteSpec.toColors(fallback: Color): AlertPaletteColors {
    if (rainbow) {
        // 彩虹与设置页预览共用同一份色表（AlertPalette.rainbowRing，已在 toSpec 填好）；
        // 空表兜底为默认色相环，保证 AlertPaletteColors 非空约束。
        val ring = colors.ifEmpty { hunoia.luno.config.model.BuiltinAlertPalettes.rainbowColors }
        return AlertPaletteColors(
            colors = ring.map { Color(it) },
            rainbow = true,
        )
    }
    val list = colors.map { Color(it) }
    return AlertPaletteColors(
        colors = if (list.isEmpty()) listOf(fallback) else list,
        rainbow = false,
    )
}
