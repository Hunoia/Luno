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
    /** 面板贴屏边距：定位跟随触发锚点后的离屏间隙，与网格面板同款（0–48dp）。 */
    val screenMarginDp: Float = GridPanelDefaults.ScreenMarginDp,
)

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
