package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.bridge.ui.DensityProvider
import kotlinx.serialization.Serializable

object ActionPanelStylesDefaults {

    val ArcStyleItemSize = DensityProvider.dp2px(48f)
    val ArcStyleItemGap = DensityProvider.dp2px(6f)
    const val ArcStyleSpreadSpacing = 1.12f
}

sealed interface ActionPanelStyle

/**
 * 弧形面板样式。全局一份（见 [hunoia.luno.config.model.ActionSettings.actionPanelStyle]），
 * 不再按触钮/方向分别存储。
 *
 * [itemSize] 与 [itemGapPx] 都是 **px**（与既有 itemSize 同单位，命名带 Px 以免和 dp 混淆）。
 */
@Serializable
@Keep
data class ArcStyle(
    val itemSize: Int = ActionPanelStylesDefaults.ArcStyleItemSize,
    /** 图标之间的净间距（相邻两个图标边缘之间的空隙）。0 = 完全贴合。 */
    val itemGapPx: Int = ActionPanelStylesDefaults.ArcStyleItemGap,
    /** 弧线离原点的远近 / 铺开程度。不影响净间距。 */
    val spreadSpacing: Float = ActionPanelStylesDefaults.ArcStyleSpreadSpacing,
) : ActionPanelStyle

/** 颜色来源：自定义色值或跟随主题色。 */
enum class ColorSource { Custom, Theme }

/** 可跟随的主题色键。 */
enum class ThemeColorKey {
    Primary, PrimaryContainer, Secondary, SecondaryContainer,
    Tertiary, TertiaryContainer, Surface, SurfaceVariant,
    OnSurface, OnSurfaceVariant, Outline, OutlineVariant,
    SurfaceContainerLow, SurfaceContainer, SurfaceContainerHigh,
}
