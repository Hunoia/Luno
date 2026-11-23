package hunoia.luno.runtime.floatball.alert

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 解析好的调色板（Compose 侧）：1–5 色或彩虹，附单色回退。
 *
 * 取色口径（需求确认）：边缘按周长、粒子按序号、胶囊/球环按周长；单色恒纯色。
 * [second] 是单色 + 双色（边缘流光）时的第二色，多色时不用（第二带按自己的周长采样）。
 */
internal class AlertPaletteColors(
    val colors: List<Color>,
    val rainbow: Boolean,
    val second: Color? = null,
) {
    init {
        require(colors.isNotEmpty()) { "palette needs at least one color" }
    }

    val isSingle: Boolean get() = colors.size == 1 && !rainbow

    /** 归一化位置（周长 / 相位）取色；单色返回唯一色，多色按位置分桶，彩虹按色相环。 */
    fun sample(t: Float): Color {
        if (isSingle) return colors[0]
        val n = colors.size
        val x = (((t - t.toInt()) % 1f) + 1f) % 1f
        val idx = (x * n).toInt().coerceIn(0, n - 1)
        return colors[idx]
    }

    /** 按序号取色（粒子）；单色返回唯一色。 */
    fun at(index: Int): Color {
        if (isSingle) return colors[0]
        return colors[abs(index) % colors.size]
    }
}

/** [AlertPaletteSpec] → 渲染用颜色；[fallback] 是未选调色板时的单色（主题解析后）。 */
internal fun AlertPaletteSpec.toColors(fallback: Color): AlertPaletteColors {
    if (rainbow) {
        return AlertPaletteColors(colors = RainbowColors, rainbow = true)
    }
    val list = colors.map { Color(it) }
    return AlertPaletteColors(
        colors = if (list.isEmpty()) listOf(fallback) else list,
        rainbow = false,
    )
}

/** 彩虹的固定色相环（HSV 均匀 5 档）。 */
private val RainbowColors: List<Color> =
    listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f).map { hue -> Color.hsv(hue * 360f, 0.85f, 0.95f) }
