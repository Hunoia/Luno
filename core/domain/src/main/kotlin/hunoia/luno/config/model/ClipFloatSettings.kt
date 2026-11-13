package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 悬浮窗背景透明度范围（只影响背景，正文保持不透明）。 */
val ClipFloatAlphaRange = 0.35f..1f

/** 悬浮窗尺寸范围（dp）；四角拖动与捏合共用。 */
val ClipFloatWidthRange = 140f..480f
val ClipFloatHeightRange = 100f..640f

/** 居中归一化坐标：0–1，按屏宽 / 屏高换算成窗口左上角。 */
@Serializable
@Keep
data class ClipFloatPlacement(
    val x: Float = 0.88f,
    val y: Float = 0.42f,
)

/**
 * 剪贴板悬浮窗配置（随 `BackupDomain.CLIPBOARD` 走）。
 *
 * 窗口本身是临时 Surface：息屏 / 锁屏即关闭，解锁不自动恢复；这里只持久化下一次
 * 打开时的位置、尺寸与背景透明度。横竖屏各存一份位置，跨分辨率按比例恢复。
 */
@Serializable
@Keep
data class ClipFloatSettings(
    /** 背景透明度：只作用于容器底色，正文 / 图标始终不透明。 */
    val alpha: Float = 1f,
    val widthDp: Float = 220f,
    val heightDp: Float = 180f,
    val portrait: ClipFloatPlacement = ClipFloatPlacement(),
    val landscape: ClipFloatPlacement = ClipFloatPlacement(),
) {
    fun placement(landscape: Boolean): ClipFloatPlacement =
        if (landscape) this.landscape else this.portrait

    fun withPlacement(landscape: Boolean, placement: ClipFloatPlacement): ClipFloatSettings =
        if (landscape) copy(landscape = placement) else copy(portrait = placement)

    fun coerced(): ClipFloatSettings {
        val safeAlpha = alpha.coerceIn(ClipFloatAlphaRange.start, ClipFloatAlphaRange.endInclusive)
        val safeWidth = widthDp.coerceIn(ClipFloatWidthRange.start, ClipFloatWidthRange.endInclusive)
        val safeHeight = heightDp.coerceIn(ClipFloatHeightRange.start, ClipFloatHeightRange.endInclusive)
        val safePortrait = portrait.coercedPlacement()
        val safeLandscape = landscape.coercedPlacement()
        return if (safeAlpha == alpha && safeWidth == widthDp && safeHeight == heightDp &&
            safePortrait == portrait && safeLandscape == landscape
        ) {
            this
        } else {
            copy(
                alpha = safeAlpha,
                widthDp = safeWidth,
                heightDp = safeHeight,
                portrait = safePortrait,
                landscape = safeLandscape,
            )
        }
    }

    private fun ClipFloatPlacement.coercedPlacement(): ClipFloatPlacement =
        copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
}
