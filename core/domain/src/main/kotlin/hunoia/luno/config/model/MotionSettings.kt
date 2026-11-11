package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 应用动效档位（ui-guidelines §5.1 / §5.2）。
 *
 * 倍率**叠加在系统动画缩放之上**：最终时长 = 家族基础值 × 档位 × 系统缩放。
 * 生产代码只乘自己的档位，不读系统值（Compose / 原生动画 API 已自动跟随系统）。
 */
@Serializable
@Keep
enum class MotionSpeed(val factor: Float) {
    Faster(0.8f),
    Standard(1.0f),
    Slower(1.3f),
    Slowest(1.7f),
}

/** 三类可独立调节的动效档位：面板 / 浮层 / 浮窗。 */
@Serializable
@Keep
data class MotionTuningSettings(
    val panel: MotionSpeed = MotionSpeed.Standard,
    val overlay: MotionSpeed = MotionSpeed.Standard,
    val floating: MotionSpeed = MotionSpeed.Standard,
)
