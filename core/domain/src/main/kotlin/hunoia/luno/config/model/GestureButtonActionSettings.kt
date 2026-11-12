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

/** 迷你小窗贴在屏幕哪一侧。 */
@Serializable
@Keep
enum class MiniFreeformPosition {
    /** 按触发点位置决定：触点落在左半屏贴左，右半屏贴右。 */
    AUTO,
    LEFT,
    RIGHT,
}

/**
 * 迷你小窗（MIUI/HyperOS 原生迷你浮窗）的独立设置。
 *
 * 尺寸由系统控制，这里只决定位置与是否与屏幕上已有的小窗互换。
 */
@Serializable
@Keep
data class MiniFreeformSettings(
    val position: MiniFreeformPosition = MiniFreeformPosition.AUTO,
    /** 屏幕已有小窗时，把旧小窗恢复全屏，把当前应用挂成迷你小窗。 */
    val autoSwap: Boolean = true,
)

/** 小窗位置与大小，竖屏横屏各存一套：转屏后比例几乎必然不合理，所以按方向分开。 */
@Serializable
@Keep
data class MiniWindowSettings(
    val portrait: WindowBounds = WindowBounds(),
    val landscape: WindowBounds = DefaultLandscapeBounds,
    /** 从手势/触钮等带触点的入口打开时，以触发点为中心，而不是用固定 x/y。 */
    val followTrigger: Boolean = false,
    /** 跟随触发位置时，横 / 竖屏各自的三个边距。 */
    val portraitPlacement: SurfacePlacement = SurfacePlacement(),
    val landscapePlacement: SurfacePlacement = SurfacePlacement(),
    /**
     * 打开新小窗时，把屏幕上已有的其他小窗恢复为全屏。
     *
     * 与 [MiniFreeformSettings.autoSwap] 相互独立：那个管迷你小窗动作，这个管普通小窗。
     */
    val swapExisting: Boolean = true,
    /** 迷你小窗动作的设置，与小窗本身互不影响。 */
    val miniFreeform: MiniFreeformSettings = MiniFreeformSettings(),
) {
    fun forOrientation(landscape: Boolean): WindowBounds =
        if (landscape) this.landscape else portrait

    fun placement(landscape: Boolean): SurfacePlacement =
        if (landscape) landscapePlacement else portraitPlacement

    fun withOrientation(landscape: Boolean, bounds: WindowBounds): MiniWindowSettings =
        if (landscape) copy(landscape = bounds)
        else copy(portrait = bounds)

    fun withPlacement(landscape: Boolean, placement: SurfacePlacement): MiniWindowSettings =
        if (landscape) copy(landscapePlacement = placement)
        else copy(portraitPlacement = placement)
}

/** 生效的小窗设置：未配置时用默认值。 */
fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = miniWindow ?: MiniWindowSettings()

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings =
    copy(miniWindow = settings)
