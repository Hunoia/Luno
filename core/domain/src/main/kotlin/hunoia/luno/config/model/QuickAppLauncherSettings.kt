package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 快速启动内容布局（横竖屏各一份）：只保留内容相关项。
 * 面板外框宽高 / 位置由承载它的 `SinglePanel` 负责，这里不再维护第二套面板几何。
 */
@Serializable
@Keep
data class QuickLauncherGeometry(
    val candidateRows: Int = 2,
    val gridColumns: Int = 4,
    val keyHeightDp: Int = 45,
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
