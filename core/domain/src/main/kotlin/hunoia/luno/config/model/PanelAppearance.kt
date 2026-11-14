package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 面板外观：卡透明度、背景模糊半径与跟随触发点的三个边距。
 *
 * 两份语义：
 * - [ActionSettings.panelAppearance] 是全局默认，始终有值；
 * - 网格面板 / 单功能面板的 `appearance` 为 `null` 时跟随全局，非空时整块覆盖。
 *
 * 边距与面板几何一样横竖屏各存一份；全局与单面板覆盖都按同一口径读写，
 * 转屏后各取对应朝向。
 */
@Serializable
@Keep
data class PanelAppearance(
    /** 卡片本体透明度：0–1，半透明透出原背景；0 = 纯模糊 / 完全透明底。 */
    val cardAlpha: Float = GridPanelDefaults.CardAlpha,
    /** 面板与内置部件背景的模糊半径（dp，0 = 关闭）。 */
    val blurRadiusDp: Float = GridPanelDefaults.BlurRadiusDp,
    val portraitPlacement: SurfacePlacement = SurfacePlacement(),
    val landscapePlacement: SurfacePlacement = SurfacePlacement(),
) {
    fun placement(landscape: Boolean): SurfacePlacement =
        if (landscape) landscapePlacement else portraitPlacement

    fun withPlacement(landscape: Boolean, placement: SurfacePlacement): PanelAppearance =
        if (landscape) copy(landscapePlacement = placement) else copy(portraitPlacement = placement)
}

/** 面板覆盖为空 = 跟随全局。 */
fun PanelAppearance?.orGlobal(global: PanelAppearance): PanelAppearance = this ?: global
