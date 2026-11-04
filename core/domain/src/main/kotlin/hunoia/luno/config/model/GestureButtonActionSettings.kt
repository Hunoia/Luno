package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults
import kotlinx.serialization.Serializable

/**
 * 小窗在某个屏幕方向下的位置与大小，以屏幕比例表示（0..1 屏宽/屏高，与触钮 bounds 同坐标系）。
 * x/y 为左上角偏移；width/height 为相对屏幕的尺寸比例。
 */
@Serializable
@Keep
data class WindowBounds(
    val x: Float = AdvancedSettingsDefaults.MiniWindowX,
    val y: Float = AdvancedSettingsDefaults.MiniWindowY,
    val width: Float = AdvancedSettingsDefaults.MiniWindowWidth,
    val height: Float = AdvancedSettingsDefaults.MiniWindowHeight,
)

/** 横屏那套默认。竖屏用 [WindowBounds] 的默认即可。 */
val DefaultLandscapeBounds: WindowBounds = WindowBounds(
    x = AdvancedSettingsDefaults.MiniWindowLandscapeX,
    y = AdvancedSettingsDefaults.MiniWindowLandscapeY,
    width = AdvancedSettingsDefaults.MiniWindowLandscapeWidth,
    height = AdvancedSettingsDefaults.MiniWindowLandscapeHeight,
)

/** 小窗位置与大小，竖屏横屏各存一套：转屏后比例几乎必然不合理，所以按方向分开。 */
@Serializable
@Keep
data class MiniWindowSettings(
    val portrait: WindowBounds = WindowBounds(),
    val landscape: WindowBounds = DefaultLandscapeBounds,
    /** 从手势/触钮等带触点的入口打开时，以触发点为中心，而不是用固定 x/y。 */
    val followTrigger: Boolean = false,
    /** 跟随触发位置时，窗口与屏幕边缘保留的最小间距。 */
    val followTriggerGapDp: Int = AdvancedSettingsDefaults.MiniWindowFollowGapDp,
) {
    fun forOrientation(landscape: Boolean): WindowBounds =
        if (landscape) this.landscape else portrait

    fun withOrientation(landscape: Boolean, bounds: WindowBounds): MiniWindowSettings =
        if (landscape) copy(landscape = bounds)
        else copy(portrait = bounds)
}

/**
 * 把窗口以触发点为中心放置，并夹取到屏幕内；尺寸不变。
 *
 * 传入比例坐标（0..1），[gapX]/[gapY] 是屏幕边缘间距的比例值。
 * 窗口比屏幕还大时优先保证左上角在屏内，右侧/底部允许超出。
 */
fun WindowBounds.centeredOnTrigger(
    anchorXRatio: Float,
    anchorYRatio: Float,
    gapXRatio: Float = 0f,
    gapYRatio: Float = 0f,
): WindowBounds {
    fun place(center: Float, size: Float, gap: Float): Float {
        val free = (1f - size).coerceAtLeast(0f)
        val min = gap.coerceIn(0f, free)
        val max = (free - gap).coerceAtLeast(0f).coerceAtLeast(min)
        return (center - size / 2f).coerceIn(min, max)
    }
    val w = width.coerceIn(0.2f, 1.5f)
    val h = height.coerceIn(0.2f, 1.5f)
    return copy(
        x = place(anchorXRatio, w, gapXRatio),
        y = place(anchorYRatio, h, gapYRatio),
    )
}

/** 生效的小窗设置：未配置时用默认值。 */
fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = miniWindow ?: MiniWindowSettings()

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings =
    copy(miniWindow = settings)
