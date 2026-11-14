package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 悬浮窗尺寸范围（dp）；四角拖动与捏合共用。
 *
 * 高度下限由动作条决定：点开卡片后底部要放得下 2×2 个 48dp 命中区（96dp）加边距，
 * 低于这个高度 × 会被窗口边界裁掉，等于关不掉。
 */
val ClipFloatWidthRange = 140f..480f
val ClipFloatHeightRange = 120f..640f

/**
 * 停靠时留在屏内的可见宽度（dp）。
 *
 * 停靠 = 卡片整体移出屏幕，只留这一段；这段同时是唯一的命中区，所以取最小命中区
 * 48dp 作为下限：再窄就只够碰运气——系统自己的边缘返回手势还占着最里面约 24dp
 * （ui-guidelines §4.6）。
 */
val ClipFloatPeekWidth = 48f

/** [ClipFloatPlacement.peek] 的取值：正常可见。 */
const val ClipFloatPeekNone = 0

/** [ClipFloatPlacement.peek] 的取值：停靠在屏幕左缘，卡片主体移出左侧。 */
const val ClipFloatPeekLeft = -1

/** [ClipFloatPlacement.peek] 的取值：停靠在屏幕右缘，卡片主体移出右侧。 */
const val ClipFloatPeekRight = 1

/** 归一化坐标：0–1，按屏宽 / 屏高换算成窗口左上角。 */
@Serializable
@Keep
data class ClipFloatPlacement(
    val x: Float = 0.88f,
    val y: Float = 0.42f,
    /**
     * 停靠侧：0 正常可见 / -1 左缘 / 1 右缘。停靠时 [x] 记录的是「恢复后」的可见位置，
     * 窗口本身被推出屏幕，只留 [ClipFloatPeekWidth]。
     */
    val peek: Int = ClipFloatPeekNone,
) {
    fun coerced(): ClipFloatPlacement = copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        peek = peek.coerceIn(ClipFloatPeekLeft, ClipFloatPeekRight),
    )
}

/**
 * 剪贴板悬浮窗配置（随 `BackupDomain.CLIPBOARD` 走）。
 *
 * 窗口本身是临时 Surface：息屏 / 锁屏即关闭，解锁不自动恢复；这里只持久化下一次
 * 打开时的位置、尺寸与停靠侧。横竖屏各存一份位置，跨分辨率按比例恢复。
 *
 * 背景不透明度不做配置：卡片是不透明实色容器（跨应用没有同源底图，不叠 haze），
 * 只在拖出投递时由运行态临时压暗，那不是用户设置。
 */
@Serializable
@Keep
data class ClipFloatSettings(
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
        val safeWidth = widthDp.coerceIn(ClipFloatWidthRange.start, ClipFloatWidthRange.endInclusive)
        val safeHeight = heightDp.coerceIn(ClipFloatHeightRange.start, ClipFloatHeightRange.endInclusive)
        val safePortrait = portrait.coerced()
        val safeLandscape = landscape.coerced()
        return if (safeWidth == widthDp && safeHeight == heightDp &&
            safePortrait == portrait && safeLandscape == landscape
        ) {
            this
        } else {
            copy(
                widthDp = safeWidth,
                heightDp = safeHeight,
                portrait = safePortrait,
                landscape = safeLandscape,
            )
        }
    }
}
