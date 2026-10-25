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
) {
    fun forOrientation(landscape: Boolean): WindowBounds =
        if (landscape) this.landscape else portrait

    fun withOrientation(landscape: Boolean, bounds: WindowBounds): MiniWindowSettings =
        if (landscape) copy(landscape = bounds)
        else copy(portrait = bounds)
}

/** 生效的小窗设置：未配置时用默认值。 */
fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = miniWindow ?: MiniWindowSettings()

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings =
    copy(miniWindow = settings)
