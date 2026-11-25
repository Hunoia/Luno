package hunoia.luno.floatball.settings.alert

import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.argbToHsv
import hunoia.luno.config.model.hsvToArgb

/** 和谐色生成算法（设置页「生成」区）。 */
enum class PaletteGenerateAlgo {
    /** 色环上基色两侧等距偏移（±30°/±60°…），温和过渡。 */
    Analogous,

    /** 基色与补色交替，高对比。 */
    Complementary,

    /** 色环 120° 均布（红 / 黄 / 蓝构架）。 */
    Triadic,

    /** 同色相不同明度梯度（浅 → 深）。 */
    Shades,
}

/**
 * 从 [baseArgb] 按 [algo] 生成 [count] 个 ARGB 色（[AlertLimits.MaxPaletteColors] 内）。
 * 纯 JVM，无 Compose / android 依赖，可单测。
 *
 * 各算法都保留基色的饱和度；明度按算法在环上做梯度，保证「同源但不单调」。
 */
internal fun generatePalette(
    baseArgb: Int,
    algo: PaletteGenerateAlgo,
    count: Int,
): List<Int> {
    val (hue, sat, value) = argbToHsv(baseArgb)
    val n = count.coerceIn(1, AlertLimits.MaxPaletteColors)
    return List(n) { i ->
        when (algo) {
            PaletteGenerateAlgo.Analogous -> {
                val spread = 60f // 环上最大偏移（两侧各 ±30° 附近）
                val offset = (i - (n - 1) / 2f) * (spread / maxOf(1, n - 1)) * 2f
                hsvToArgb(hue + offset, sat, value)
            }
            PaletteGenerateAlgo.Complementary -> {
                // 基色 / 补色交替；补色明度略降，避免 6 色平板。
                val onBase = i % 2 == 0
                val v = if (onBase) value else value * 0.78f
                hsvToArgb(hue + if (onBase) 0f else 180f, sat, v)
            }
            PaletteGenerateAlgo.Triadic -> {
                val step = (i % 3) * 120f
                val v = value * (1f - 0.06f * (i % 3))
                hsvToArgb(hue + step, sat, v)
            }
            PaletteGenerateAlgo.Shades -> {
                // 浅 → 深：明度从 1.0 线性到 0.18。
                val v = if (n == 1) value else 1f - (1f - 0.18f) * (i.toFloat() / (n - 1))
                hsvToArgb(hue, sat, v)
            }
        }
    }
}
