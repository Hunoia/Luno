package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.bridge.ui.DensityProvider
import kotlinx.serialization.Serializable

object ActionPanelStylesDefaults {

    val ArcStyleItemSize = DensityProvider.dp2px(48f)
    val ArcStyleItemGap = DensityProvider.dp2px(6f)
    const val ArcStyleSpreadSpacing = 1.12f
    const val ArcStyleAppIconScale = 1f
    const val ArcStyleMaxAngleDegrees = 180f
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
    /** 无圆底应用图标的绘制比例；应用 Drawable 常带透明留白，因此允许单独调节。 */
    val appIconScale: Float = ActionPanelStylesDefaults.ArcStyleAppIconScale,
    /** 动作面板允许展开的最大角度，实际布局仍会受屏幕可用空间限制。 */
    val maxAngleDegrees: Float = ActionPanelStylesDefaults.ArcStyleMaxAngleDegrees,
    /** 图标之间的净间距（相邻两个图标边缘之间的空隙）。0 = 完全贴合。 */
    val itemGapPx: Int = ActionPanelStylesDefaults.ArcStyleItemGap,
    /** 弧线离原点的远近 / 铺开程度。不影响净间距。 */
    val spreadSpacing: Float = ActionPanelStylesDefaults.ArcStyleSpreadSpacing,
    /** 扇菜单离屏幕左右/上下边缘的最小边距（与网格面板/快捷启动同一口径）。 */
    val placement: SurfacePlacement = SurfacePlacement(),
) : ActionPanelStyle

