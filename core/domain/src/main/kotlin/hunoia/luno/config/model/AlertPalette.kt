package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * 到达提示·调色板：1–[AlertLimits.MaxPaletteColors] 色（ARGB，[ColorSpec.customColor] 同构）
 * 或彩虹模式。
 *
 * [rainbow] 打开时按参数化色相环取色（见 [rainbowRing]），[colors] 只当「彩虹之外的偏移色」
 * 可空；单色（[colors].size == 1 且非 [rainbow]）= 纯色，其余按周长 / 序号采样。
 *
 * 彩虹参数（[rainbowHueOffset]…[rainbowReverse]）默认值 = 原固定色相环（全饱和 / 全亮度 /
 * 无偏移），旧数据零迁移无缝兼容。
 *
 * [id]：内置 `builtin_*`（[BuiltinAlertPalettes]，不落盘），自定义 `custom_*`（存
 * [AlertLibrary.palettes]）。改 id 视为换调色板，不做迁移。
 */
@Serializable
@Keep
data class AlertPalette(
    val id: String,
    val name: String,
    val colors: List<Int> = emptyList(),
    val rainbow: Boolean = false,
    /** 彩虹色相偏移（0–359°）。 */
    val rainbowHueOffset: Int = 0,
    /** 彩虹饱和度范围（0–100）。 */
    val rainbowSatMin: Int = 100,
    val rainbowSatMax: Int = 100,
    /** 彩虹亮度范围（0–100）。 */
    val rainbowValMin: Int = 100,
    val rainbowValMax: Int = 100,
    /** 彩虹取色方向（默认顺时针）。 */
    val rainbowReverse: Boolean = false,
)

/**
 * 彩虹参数 → 色相环采样（ARGB，数量 = [AlertLimits.MaxPaletteColors]）。纯 JVM，可单测。
 *
 * 渲染端（[AlertPaletteSpec.toColors]）与设置页预览共用同一份色表：预览条与实际播放必须
 * 同色，否则「预览里是紫红、播出来是蓝」这类差异只能靠肉眼发现。多色取色在相邻两色之间
 * 线性插值（首尾相接），所以采样在屏缘上读作连续色相环，而不是几段硬色带。
 * 饱和度 / 亮度在环上从 min 到 max 线性渐变，给环加深度；[rainbowReverse] 反转方向。
 */
fun AlertPalette.rainbowRing(): List<Int> {
    val n = AlertLimits.MaxPaletteColors
    val satMin = rainbowSatMin.coerceIn(0, 100) / 100f
    val satMax = rainbowSatMax.coerceIn(0, 100) / 100f
    val valMin = rainbowValMin.coerceIn(0, 100) / 100f
    val valMax = rainbowValMax.coerceIn(0, 100) / 100f
    val ring = List(n) { i ->
        val t = i.toFloat() / n
        val hue = (((rainbowHueOffset % 360) + 360) % 360 + t * 360f) % 360f
        val sat = satMin + (satMax - satMin) * t
        val value = valMin + (valMax - valMin) * t
        hsvToArgb(hue, sat, value)
    }
    return if (rainbowReverse) ring.reversed() else ring
}

/** HSV → ARGB（纯 JVM；h∈[0,360)，s、v∈[0,1]），与 Compose / android.graphics 无关，可单测。 */
fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val hp = hue / 60f
    val x = c * (1 - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = value - c
    fun ch(vv: Float): Int = ((vv + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

/** ARGB → HSV（h∈[0,360)，s、v∈[0,1]；纯 JVM，与 [hsvToArgb] 互逆，可单测）。 */
fun argbToHsv(argb: Int): Triple<Float, Float, Float> {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    val hue = ((h % 360f) + 360f) % 360f
    val s = if (max == 0f) 0f else delta / max
    return Triple(hue, s, max)
}

/**
 * 内置调色板：16 套主题 + 彩虹（常量，不落盘；素材库 `AlertLibrary.palettes` 另存自定义）。
 *
 * 色值取自各主题的招牌 5 色（可辨识度优先），顺序即取色顺序。
 * 名字用规范名（Catppuccin / Dracula / Nord / Tokyo Night / Gruvbox / Rosé Pine / Everforest /
 * Solarized / One Dark / Kanagawa / Monokai / Ayu / Material 都是专名，不做本地化）。
 */
object BuiltinAlertPalettes {

    val CatppuccinMocha = AlertPalette(
        "builtin_catppuccin_mocha", "Catppuccin Mocha",
        listOf(0xFF94E2D5.toInt(), 0xFFF9E2AF.toInt(), 0xFFFAB387.toInt(), 0xFFCBA6F7.toInt(), 0xFF89B4FA.toInt()),
    )

    val CatppuccinMacchiato = AlertPalette(
        "builtin_catppuccin_macchiato", "Catppuccin Macchiato",
        listOf(0xFF8BD5CA.toInt(), 0xFFEED49F.toInt(), 0xFFF5A97F.toInt(), 0xFFC6A0F6.toInt(), 0xFF8AADF4.toInt()),
    )

    val CatppuccinFrappe = AlertPalette(
        "builtin_catppuccin_frappe", "Catppuccin Frappe",
        listOf(0xFF81C8BE.toInt(), 0xFFE5C890.toInt(), 0xFFEF9F76.toInt(), 0xFFCA9EE6.toInt(), 0xFF8CAAEE.toInt()),
    )

    val CatppuccinLatte = AlertPalette(
        "builtin_catppuccin_latte", "Catppuccin Latte",
        listOf(0xFF179299.toInt(), 0xFFDF8E1D.toInt(), 0xFFFE640B.toInt(), 0xFF8839EF.toInt(), 0xFF1E66F5.toInt()),
    )

    val Dracula = AlertPalette(
        "builtin_dracula", "Dracula",
        listOf(0xFF8BE9FD.toInt(), 0xFFF1FA8C.toInt(), 0xFFFFB86C.toInt(), 0xFFBD93F9.toInt(), 0xFFFF79C6.toInt()),
    )

    val Nord = AlertPalette(
        "builtin_nord", "Nord",
        listOf(0xFF88C0D0.toInt(), 0xFFEBCB8B.toInt(), 0xFFD08770.toInt(), 0xFFB48EAD.toInt(), 0xFF81A1C1.toInt()),
    )

    val TokyoNight = AlertPalette(
        "builtin_tokyo_night", "Tokyo Night",
        listOf(0xFF7DCFFF.toInt(), 0xFFE0AF68.toInt(), 0xFFFF9E64.toInt(), 0xFFBB9AF7.toInt(), 0xFF7AA2F7.toInt()),
    )

    val Gruvbox = AlertPalette(
        "builtin_gruvbox", "Gruvbox",
        listOf(0xFF83A598.toInt(), 0xFFD8A657.toInt(), 0xFFFE8019.toInt(), 0xFFB16286.toInt(), 0xFF458588.toInt()),
    )

    val RosePine = AlertPalette(
        "builtin_rose_pine", "Rosé Pine",
        listOf(0xFFEBBCBA.toInt(), 0xFF9CCFD8.toInt(), 0xFFF6C177.toInt(), 0xFFC4A7E7.toInt(), 0xFF31748F.toInt()),
    )

    val EverforestDark = AlertPalette(
        "builtin_everforest_dark", "Everforest Dark",
        listOf(0xFFE67E80.toInt(), 0xFFA7C080.toInt(), 0xFFDBBC7F.toInt(), 0xFF83C092.toInt(), 0xFFD699B6.toInt()),
    )

    val SolarizedDark = AlertPalette(
        "builtin_solarized_dark", "Solarized Dark",
        listOf(0xFF268BD2.toInt(), 0xFF2AA198.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(), 0xFFCB4B16.toInt()),
    )

    val OneDark = AlertPalette(
        "builtin_one_dark", "One Dark",
        listOf(0xFFE06C75.toInt(), 0xFFE5C07B.toInt(), 0xFF98C379.toInt(), 0xFF61AFEF.toInt(), 0xFFC678DD.toInt()),
    )

    val KanagawaWave = AlertPalette(
        "builtin_kanagawa_wave", "Kanagawa Wave",
        listOf(0xFF7AA2F7.toInt(), 0xFF7DCFFF.toInt(), 0xFF9ECE6A.toInt(), 0xFFE0AF68.toInt(), 0xFFF7768E.toInt()),
    )

    val Monokai = AlertPalette(
        "builtin_monokai", "Monokai",
        listOf(0xFFE6DB74.toInt(), 0xFFFD971F.toInt(), 0xFFF92672.toInt(), 0xFFA6E22E.toInt(), 0xFF66D9EF.toInt()),
    )

    val AyuDark = AlertPalette(
        "builtin_ayu_dark", "Ayu Dark",
        listOf(0xFF5CCFE6.toInt(), 0xFF59C2FF.toInt(), 0xFFFFE27B.toInt(), 0xFFFF8F40.toInt(), 0xFFAAD94C.toInt()),
    )

    val MaterialDeep = AlertPalette(
        "builtin_material_deep", "Material Deep",
        listOf(0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF9800.toInt(), 0xFFF44336.toInt()),
    )

    /** 彩虹：参数化色相环（默认全饱和 / 全亮度 / 无偏移），按 [AlertPalette.rainbowRing] 取色。 */
    val Rainbow = AlertPalette(
        "builtin_rainbow", "彩虹",
        colors = emptyList(),
        rainbow = true,
    )

    /**
     * 默认色相环（参数全默认）：设置页预览与渲染端共用同一份色表（见 [AlertPalette.rainbowRing]）。
     * 多色取色在相邻两色之间线性插值（首尾相接），所以采样在屏缘上读作连续色相环。
     */
    val rainbowColors: List<Int> get() = Rainbow.rainbowRing()

    /** 内置全集（顺序即设置页展示顺序）。 */
    val all: List<AlertPalette> = listOf(
        CatppuccinMocha,
        CatppuccinMacchiato,
        CatppuccinFrappe,
        CatppuccinLatte,
        Dracula,
        Nord,
        TokyoNight,
        Gruvbox,
        RosePine,
        EverforestDark,
        SolarizedDark,
        OneDark,
        KanagawaWave,
        Monokai,
        AyuDark,
        MaterialDeep,
        Rainbow,
    )

    /** id → 内置调色板（选择 / 回退用）。 */
    fun builtin(id: String): AlertPalette? = all.firstOrNull { it.id == id }
}

/** 全部可选调色板：内置（顺序即展示顺序）+ 素材库自定义。 */
fun AlertLibrary.allPalettes(): List<AlertPalette> = BuiltinAlertPalettes.all + palettes

/** id → 调色板（内置优先，再查素材库）；null = 未找到。 */
fun AlertLibrary.paletteById(id: String?): AlertPalette? {
    if (id == null) return null
    return BuiltinAlertPalettes.builtin(id) ?: palettes.firstOrNull { it.id == id }
}

/** 删除素材库调色板；引用它的槽位由设置页 / 播放计划回落为跟随预设颜色。 */
fun AlertLibrary.removePalette(id: String): AlertLibrary =
    copy(palettes = palettes.filterNot { it.id == id })

/** 新增或替换一个素材库调色板（按 id）。 */
fun AlertLibrary.upsertPalette(palette: AlertPalette): AlertLibrary {
    val next = palettes.filterNot { it.id == palette.id } + palette
    return copy(palettes = next)
}
