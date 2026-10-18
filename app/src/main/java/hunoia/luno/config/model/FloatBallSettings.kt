package hunoia.luno.config.model

import android.graphics.Color as AndroidColor
import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable

/**
 * 悬浮球配置。
 *
 * 挂在 [ActionSettings.floatBall] 下不是随手选的：球跑在 `FloatBallService`
 * （`:service` 进程），只能顺着 `ConfigProvider.actionSettings → SettingsStore →
 * SettingsState` 读到。挂到别的域会让运行时静默失效。
 *
 * 坐标是归一化的（0..1），旋转屏幕 / 换分辨率都不用迁移数据。
 *
 * 交互与一墨对齐：**按住即拖**（无长按门槛），松手落位；
 * 点按 = 抬手未超 touchSlop 时执行 [tapAction]。
 */
@Serializable
@Keep
data class FloatBallSettings(
    val enabled: Boolean = FloatBallDefaults.Enabled,
    /** 归一化横坐标（球窗顶边 / 屏宽）。 */
    val x: Float = FloatBallDefaults.X,
    /** 归一化纵坐标（球窗顶边 / 屏高）。 */
    val y: Float = FloatBallDefaults.Y,
    val sizeDp: Float = FloatBallDefaults.SizeDp,
    val color: Int = FloatBallDefaults.Color,
    /** 单击球执行的动作。为 null 表示单击无响应。 */
    val tapAction: StoredAction? = null,
    /**
     * 静置多久后变暗缩小。0 = 关闭。
     */
    val idleDimDelayMs: Long = FloatBallDefaults.IdleDimDelayMs,
)

/**
 * 手感常量取值来自一墨（`ball_idle_alpha` 回落 0.4、按压 scale 0.9、
 * 静置缩放约 0.85、静置延时默认 3s）。
 */
object FloatBallDefaults {
    const val Enabled = false
    const val X = 0.92f
    const val Y = 0.42f
    const val SizeDp = 56f
    const val MinSizeDp = 36f
    const val MaxSizeDp = 96f
    val Color: Int = AndroidColor.TRANSPARENT

    /** 静置延时（ms），0 = 关。 */
    const val IdleDimDelayMs = 3_000L

    /** 静置 alpha。 */
    const val IdleDimAlpha = 0.4f

    /**
     * 静置出屏比例：球径的这部分移到屏幕外（一墨 `ballShrinkRatio` 同物，
     * 它的真实语义是横向偏移而非等比缩小）。一半出屏。
     */
    const val IdleOutRatio = 0.5f

    /** 按压缩放。 */
    const val PressScale = 0.9f

    /** touchSlop（dp），点按与拖动分界。 */
    const val TouchSlopDp = 12f

    /** 键盘弹出时球与键盘顶边的间距（dp）。 */
    const val ImeMarginDp = 8f
}
