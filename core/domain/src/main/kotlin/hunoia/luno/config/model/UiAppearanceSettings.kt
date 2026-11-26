package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 用户可调的外观（ui-guidelines §2.5「可调外观白名单」）。
 *
 * 分组按**组件族**而不是形状角色：同一个 `LargeShape` 以前同时盖住 44dp 工具条、
 * 48dp 菜单项、56dp 行 / FAB、112dp 卡，一组半径会被最矮的容器卡死。拆细后每组
 * 只覆盖同一高度族的容器，默认值全部等于原来的固定 token（不动设置 = 视觉不变）。
 *
 * 几何约束见 [hunoia.luno.ui.theme.UiAppearance]：半径档位按组预算（该组最矮容器高）
 * 过滤，切换形态后越界会被明确改到该形态的最大合法档，不做静默 clamp。
 */
@Serializable
@Keep
data class UiAppearanceSettings(
    /** 列表与卡：行、组卡、分段组、输入框、空态、横幅（原 `LargeShape`）。 */
    val list: ShapeSetting = ShapeSetting(radiusDp = 16),
    /** 菜单项与 sheet 按钮（48dp 族，原 `LargeShape`）。 */
    val menu: ShapeSetting = ShapeSetting(radiusDp = 16),
    /** 紧凑工具条（标注页，抬到 48dp 后与菜单同族）。 */
    val toolbar: ShapeSetting = ShapeSetting(radiusDp = 16),
    /** 小元件：图标底、缩略图、图标格、badge（原 `SmallShape`，中档 = 小档 + 4）。 */
    val compact: ShapeSetting = ShapeSetting(radiusDp = 8),
    /** 弹层与大卡：sheet、dialog、anchored dialog、OCR 卡（原 `ExtraLargeShape`）。 */
    val sheet: ShapeSetting = ShapeSetting(radiusDp = 32),
    /** 胶囊：悬浮底栏、指示 pill、chip、按钮胶囊、玻璃胶囊（原 `CapsuleShape`）。 */
    val capsule: ShapeModeSetting = ShapeModeSetting(),
    /** 顶栏玻璃：模糊与收起后的底色不透明度。 */
    val topBarGlass: GlassSetting = GlassSetting(blurDp = 40, alpha = 0.70f),
    /** 悬浮玻璃：底栏 / 搜索 / 玻璃胶囊的模糊、不透明度与阴影。 */
    val floatingGlass: GlassSetting = GlassSetting(
        blurDp = 25,
        alpha = 0.65f,
        shadow = ShadowStrength.Subtle,
    ),
    /** 页面边距与列表间距。 */
    val spacing: SpacingSetting = SpacingSetting(),
    /** 主题色：跟随系统动态取色，或选一套色板。 */
    val theme: ThemePaletteSetting = ThemePaletteSetting(),
)

/** 主题色模式：系统动态 / 选定色板。 */
@Serializable
@Keep
enum class ThemeMode {
    /** 跟随系统 Material You 动态取色（默认）。 */
    System,

    /** 使用 [ThemePaletteSetting.paletteId] 指向的内置或自定义色板。 */
    Palette,
}

/**
 * 主题色板选择。
 *
 * [paletteId] 指向 `BuiltinAlertPalettes` 或到达提示素材库里的用户自定义色板；
 * 找不到时回退 [ThemeMode.System]，不空主题、不崩。
 */
@Serializable
@Keep
data class ThemePaletteSetting(
    val mode: ThemeMode = ThemeMode.System,
    val paletteId: String? = null,
)

/** 圆角形态：连续曲率（squircle）/ 标准圆弧（round rect）。 */
@Serializable
@Keep
enum class ShapeMode {
    /** 连续曲率：容器 0.48、胶囊 0.2（默认，形状语言与改造前一致）。 */
    Continuous,

    /** 标准圆弧：平滑度 0，端部是圆弧，几何上限放宽到 r ≤ h/2。 */
    Standard,
}

/** 一组容器的圆角设置（形态 + 档位）。 */
@Serializable
@Keep
data class ShapeSetting(
    val mode: ShapeMode = ShapeMode.Continuous,
    val radiusDp: Int,
)

/** 胶囊只有形态（半径是百分比，随高度自适应）。 */
@Serializable
@Keep
data class ShapeModeSetting(
    val mode: ShapeMode = ShapeMode.Continuous,
)

/** 玻璃面：模糊半径、底色不透明度、阴影强度。 */
@Serializable
@Keep
data class GlassSetting(
    val blurDp: Int,
    val alpha: Float,
    val shadow: ShadowStrength = ShadowStrength.None,
)

/** 玻璃阴影三档。 */
@Serializable
@Keep
enum class ShadowStrength {
    None,
    Subtle,
    Strong,
}

/** 页面级间距。 */
@Serializable
@Keep
data class SpacingSetting(
    val pageGutterDp: Int = 16,
    val listSpacingDp: Int = 16,
)

/** 半径档位的可选值（设置页与几何门禁共用一份）。 */
object UiAppearancePresets {
    val ContinuousRadius = listOf(8, 12, 16, 18, 20, 24, 28, 32, 36)
    val StandardRadius = listOf(8, 12, 16, 20, 24, 28, 32, 36, 40)
    val PageGutter = listOf(12, 16, 20, 24)
    val ListSpacing = listOf(8, 12, 16, 20)
    val BlurDp = 0..40
}
