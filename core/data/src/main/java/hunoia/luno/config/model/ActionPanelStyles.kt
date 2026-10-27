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
 * 弧形面板样式，全局一份（[ActionSettings.actionPanelStyle]，不按触钮/方向分别存储）。
 * [itemSize] 与 [itemGapPx] 单位都是 px（命名带 Px 以区分 dp）。
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

enum class ColorSource { Custom, Theme }
enum class ThemeColorKey {
    Primary, PrimaryContainer, Secondary, SecondaryContainer,
    Tertiary, TertiaryContainer, Surface, SurfaceVariant,
    OnSurface, OnSurfaceVariant, Outline, OutlineVariant,
    SurfaceContainerLow, SurfaceContainer, SurfaceContainerHigh,
}
