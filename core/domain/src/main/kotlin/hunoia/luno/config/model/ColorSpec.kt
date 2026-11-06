package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 颜色来源：自定义 ARGB，或跟随 M3 主题角色（见 [ThemeColorKey]）。
 *
 * 枚举名会进配置文件 / 备份，重命名需做迁移。
 */
@Serializable
@Keep
enum class ColorSource { Custom, Theme }

/**
 * 可跟随主题的 M3 颜色角色。渲染端用当前的 `ColorScheme` 实时解析，
 * 主题（壁纸动态取色 / 明暗模式）变化时颜色随之更新。
 */
@Serializable
@Keep
enum class ThemeColorKey {
    Primary, PrimaryContainer, Secondary, SecondaryContainer,
    Tertiary, TertiaryContainer, Surface, SurfaceVariant,
    OnSurface, OnSurfaceVariant, Outline, OutlineVariant,
    SurfaceContainerLow, SurfaceContainer, SurfaceContainerHigh,
}

/**
 * 一次颜色选择的完整快照：来源 + 主题角色 + 自定义 ARGB。
 *
 * 只做数据载体，不依赖 Compose；渲染端用 `ColorSpec.toColor(ColorScheme)` 解析。
 * [customColor] 即使 [source] 为 [ColorSource.Theme] 也会存一份解析快照，
 * 供旧版本 app / 备份恢复时回退使用。
 */
@Serializable
@Keep
data class ColorSpec(
    val source: ColorSource = ColorSource.Custom,
    val themeKey: ThemeColorKey = ThemeColorKey.Primary,
    /** ARGB 整数。 */
    val customColor: Int = 0,
) {
    /**
     * 未设置：沿用「透明 → 跟随主题主色」的历史语义。
     * 仅触钮 / 子手势 / 悬浮球使用；习惯颜色恒有默认值，不做该回退。
     */
    val isUnset: Boolean
        get() = source == ColorSource.Custom && customColor == 0

    companion object {
        val Unset = ColorSpec()
    }
}
