package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 悬浮窗尺寸范围（dp）；四角拖动与捏合共用。
 *
 * 两行内容卡：行1「应用名 · Activity 短名」、行2 完整组件名小字，
 * 高度下限按两行文本 + 内距收窄（没有动作条，不需要剪贴板卡的 96dp 预算）。
 */
val ActivityFloatWidthRange = 140f..480f
val ActivityFloatHeightRange = 96f..480f

/**
 * 停靠时留在屏内的可见宽度（dp）。
 *
 * 停靠 = 卡片整体移出屏幕，只留这一段；这段同时是唯一的命中区，所以取最小命中区
 * 48dp 作为下限：再窄就只够碰运气——系统自己的边缘返回手势还占着最里面约 24dp
 * （ui-guidelines §4.6）。
 */
val ActivityFloatPeekWidth = 48f

/** [ActivityFloatPlacement.peek] 的取值：正常可见。 */
const val ActivityFloatPeekNone = 0

/** [ActivityFloatPlacement.peek] 的取值：停靠在屏幕左缘，卡片主体移出左侧。 */
const val ActivityFloatPeekLeft = -1

/** [ActivityFloatPlacement.peek] 的取值：停靠在屏幕右缘，卡片主体移出右侧。 */
const val ActivityFloatPeekRight = 1

/** 归一化坐标：0–1，按屏宽 / 屏高换算成窗口左上角。 */
@Serializable
@Keep
data class ActivityFloatPlacement(
    val x: Float = 0.88f,
    val y: Float = 0.18f,
    /**
     * 停靠侧：0 正常可见 / -1 左缘 / 1 右缘。停靠时 [x] 记录的是「恢复后」的可见位置，
     * 窗口本身被推出屏幕，只留 [ActivityFloatPeekWidth]。
     */
    val peek: Int = ActivityFloatPeekNone,
) {
    fun coerced(): ActivityFloatPlacement = copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        peek = peek.coerceIn(ActivityFloatPeekLeft, ActivityFloatPeekRight),
    )
}

/**
 * 当前界面悬浮窗配置（随 `BackupDomain.ACTION` 的 [FloatBallSettings] 走）。
 *
 * 窗口本身是临时 Surface：息屏 / 锁屏即关闭，解锁不自动恢复；这里只持久化下一次
 * 打开时的位置、尺寸与停靠侧。横竖屏各存一份位置，跨分辨率按比例恢复。
 *
 * 内容不进配置：卡片实时读 `ForegroundActivityHub`，复制 / 展示都取当下值。
 * 背景不透明度不做配置：卡片是不透明实色容器（跨应用没有同源底图，不叠 haze）。
 */
@Serializable
@Keep
data class ActivityFloatSettings(
    val widthDp: Float = 220f,
    val heightDp: Float = 120f,
    val portrait: ActivityFloatPlacement = ActivityFloatPlacement(),
    val landscape: ActivityFloatPlacement = ActivityFloatPlacement(),
) {
    fun placement(landscape: Boolean): ActivityFloatPlacement =
        if (landscape) this.landscape else this.portrait

    fun withPlacement(landscape: Boolean, placement: ActivityFloatPlacement): ActivityFloatSettings =
        if (landscape) copy(landscape = placement) else copy(portrait = placement)

    fun coerced(): ActivityFloatSettings {
        val safeWidth = widthDp.coerceIn(ActivityFloatWidthRange.start, ActivityFloatWidthRange.endInclusive)
        val safeHeight = heightDp.coerceIn(ActivityFloatHeightRange.start, ActivityFloatHeightRange.endInclusive)
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
