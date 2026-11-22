package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 搜索小窗卡片（WebView）尺寸范围（dp）；右下角手柄拖动改写。
 * 范围给得宽（平板/大屏也够用），实际显示时还会按可用窗口尺寸再收一道。
 */
val WebCardWidthRange = 240f..900f
val WebCardHeightRange = 240f..1100f

/**
 * 小窗卡片尺寸（随 `QuickSearchSettings` 走）：横竖屏各存一份，跨分辨率按
 * 「dp 想要多少、超屏就 clamp」恢复。位置不持久化——卡片永远居中，即用即关。
 */
@Serializable
@Keep
data class WebCardSettings(
    val portraitWidthDp: Float = 360f,
    val portraitHeightDp: Float = 640f,
    val landscapeWidthDp: Float = 640f,
    val landscapeHeightDp: Float = 360f,
) {
    fun widthDp(landscape: Boolean): Float = if (landscape) landscapeWidthDp else portraitWidthDp

    fun heightDp(landscape: Boolean): Float = if (landscape) landscapeHeightDp else portraitHeightDp

    fun withSize(landscape: Boolean, widthDp: Float, heightDp: Float): WebCardSettings =
        if (landscape) {
            copy(landscapeWidthDp = widthDp, landscapeHeightDp = heightDp)
        } else {
            copy(portraitWidthDp = widthDp, portraitHeightDp = heightDp)
        }

    fun coerced(): WebCardSettings = copy(
        portraitWidthDp = portraitWidthDp.coerceIn(WebCardWidthRange.start, WebCardWidthRange.endInclusive),
        portraitHeightDp = portraitHeightDp.coerceIn(WebCardHeightRange.start, WebCardHeightRange.endInclusive),
        landscapeWidthDp = landscapeWidthDp.coerceIn(WebCardWidthRange.start, WebCardWidthRange.endInclusive),
        landscapeHeightDp = landscapeHeightDp.coerceIn(WebCardHeightRange.start, WebCardHeightRange.endInclusive),
    )
}
