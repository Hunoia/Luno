package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 到达提示·调色板：1–5 色（ARGB，[ColorSpec.customColor] 同构）或彩虹模式。
 *
 * [rainbow] 打开时按固定色相环取色，[colors] 只当「彩虹之外的偏移色」可空；
 * 单色（[colors].size == 1 且非 [rainbow]）= 纯色，其余按周长 / 序号采样。
 *
 * [id]：内置 `builtin_*`（[BuiltinAlertPalettes]，不落盘），自定义 `custom_*`（存
 * [NotificationAlertSettings.customPalettes]）。改 id 视为换调色板，不做迁移。
 */
@Serializable
@Keep
data class AlertPalette(
    val id: String,
    val name: String,
    val colors: List<Int> = emptyList(),
    val rainbow: Boolean = false,
)

/**
 * 内置调色板：8 套主题 + 彩虹（常量，不落盘；`BuiltinAlertPalettes.all` 追加自定义）。
 *
 * 色值取自各主题的招牌 5 色（可辨识度优先），顺序即取色顺序。
 * 名字用规范名（Catppuccin / Dracula / Nord / Tokyo Night / Gruvbox 都是专名，不做本地化）。
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

    /** 彩虹：固定色相环，按周长 / 序号循环取色。 */
    val Rainbow = AlertPalette(
        "builtin_rainbow", "彩虹",
        colors = emptyList(),
        rainbow = true,
    )

    /**
     * 彩虹色相环的 5 个采样（ARGB，色相 0° / 72° / 144° / 216° / 288°）。
     *
     * 渲染端（AGSL / Compose）与设置页预览共用同一份色表：预览条与实际播放必须同色，
     * 否则「预览里是紫红、播出来是蓝」这类差异只能靠肉眼发现。多色取色在相邻两色之间
     * 线性插值（首尾相接），所以 5 个采样在屏缘上读作连续色相环，而不是 5 段硬色带。
     */
    val rainbowColors: List<Int> = listOf(
        0xFFF22424.toInt(),
        0xFFC9F224.toInt(),
        0xFF24F2A0.toInt(),
        0xFF2477F2.toInt(),
        0xFF4E24F2.toInt(),
    )

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
        Rainbow,
    )

    /** id → 内置调色板（选择 / 回退用）。 */
    fun builtin(id: String): AlertPalette? = all.firstOrNull { it.id == id }
}

/**
 * 该类目当前命中的调色板：按 [NotificationAlertSettings] 里各类目的 `paletteId`
 * 在内置 + 自定义里解析；null = 跟随 [NotificationAlertSettings.color] 纯色。
 */
fun NotificationAlertSettings.paletteFor(category: AlertCategory): AlertPalette? {
    val id = when (category) {
        AlertCategory.Edge -> edge.paletteId
        AlertCategory.Particle -> particle.paletteId
        AlertCategory.Capsule -> capsule.paletteId
        AlertCategory.Anchor -> anchor.paletteId
    } ?: return null
    return BuiltinAlertPalettes.builtin(id) ?: customPalettes.firstOrNull { it.id == id }
}

/** 全部可选调色板：内置（顺序即展示顺序）+ 自定义。 */
fun NotificationAlertSettings.allPalettes(): List<AlertPalette> =
    BuiltinAlertPalettes.all + customPalettes

/** 该类目当前选中的调色板 id（null = 跟随 [NotificationAlertSettings.color]）。 */
fun NotificationAlertSettings.paletteIdOf(category: AlertCategory): String? = when (category) {
    AlertCategory.Edge -> edge.paletteId
    AlertCategory.Particle -> particle.paletteId
    AlertCategory.Capsule -> capsule.paletteId
    AlertCategory.Anchor -> anchor.paletteId
}

/** 设置该类目的调色板 id（null = 跟随 [NotificationAlertSettings.color]）。 */
fun NotificationAlertSettings.withPaletteId(
    category: AlertCategory,
    id: String?,
): NotificationAlertSettings = when (category) {
    AlertCategory.Edge -> copy(edge = edge.copy(paletteId = id))
    AlertCategory.Particle -> copy(particle = particle.copy(paletteId = id))
    AlertCategory.Capsule -> copy(capsule = capsule.copy(paletteId = id))
    AlertCategory.Anchor -> copy(anchor = anchor.copy(paletteId = id))
}

/** 删除自定义调色板并清空所有引用它的分类（引用回落为纯色）。 */
fun NotificationAlertSettings.removePalette(id: String): NotificationAlertSettings = copy(
    customPalettes = customPalettes.filterNot { it.id == id },
    edge = edge.copy(paletteId = edge.paletteId?.takeIf { it != id }),
    particle = particle.copy(paletteId = particle.paletteId?.takeIf { it != id }),
    capsule = capsule.copy(paletteId = capsule.paletteId?.takeIf { it != id }),
    anchor = anchor.copy(paletteId = anchor.paletteId?.takeIf { it != id }),
)
