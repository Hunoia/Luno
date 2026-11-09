package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 快速启动器布局几何（横竖屏各一份）：横屏下调整的就是横屏这份。
 * 行为开关与启动统计不分朝向。
 */
@Serializable
@Keep
data class QuickLauncherGeometry(
    val contentHeightFraction: Float = 0.40301f,
    val candidateRows: Int = 2,
    val panelWidthFraction: Float = 0.89973f,
    val gridColumns: Int = 4,
    val keyHeightDp: Int = 45,
    /** 跟随触发点的三个边距：横向贴触发侧，竖向跟随触发点（上下保留最小间距）。 */
    val placement: SurfacePlacement = SurfacePlacement(),
) {
    /** 定位与渲染必须用同一份生效值，否则面板实际高度小于定位假设会出现底部留空。 */
    fun effectiveContentHeightFraction(): Float = contentHeightFraction.coerceIn(
        MinContentHeightFraction,
        MaxContentHeightFraction,
    )

    fun effectivePanelWidthFraction(): Float = panelWidthFraction.coerceIn(
        MinPanelWidthFraction,
        MaxPanelWidthFraction,
    )

    companion object {
        const val MinContentHeightFraction = 0.25f
        const val MaxContentHeightFraction = 0.95f
        const val MinPanelWidthFraction = 0.65f
        const val MaxPanelWidthFraction = 1f
    }
}

@Serializable
@Keep
data class QuickAppLauncherSettings(
    val portrait: QuickLauncherGeometry = QuickLauncherGeometry(),
    val landscape: QuickLauncherGeometry = QuickLauncherGeometry(),
    val tapOpensMiniWindow: Boolean = false,
) {
    fun layout(landscape: Boolean): QuickLauncherGeometry =
        if (landscape) this.landscape else this.portrait

    fun withLayout(
        landscape: Boolean,
        transform: (QuickLauncherGeometry) -> QuickLauncherGeometry,
    ): QuickAppLauncherSettings =
        if (landscape) copy(landscape = transform(this.landscape)) else copy(portrait = transform(this.portrait))
}
