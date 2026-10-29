package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 按压取词配置（feature:extract）。
 *
 * - [enabled]：是否在**全屏任意位置**监听长按取词。复用 ADR-0003 的内核触摸流
 *   （[hunoia.luno.gesture.touchsource.ShizukuTouchInputSource]），因此需要 Shizuku。
 *   关闭后取词只能走触钮长按动作（internal.extract），不再全屏触发。
 * - [longPressDelayMs] / [slopDp]：长按判定阈值，对齐一墨——按住 ≥ 阈值且位移 < slop 才触发，
 *   否则视为拖动 / 点击，不取词。
 * - [preferOcr]：是否优先走 OCR 后端（当前 OCR 未接入，恒走 a11y）。
 *
 * 持久化挂在 [hunoia.luno.config.model.InitialSettings.extract]（与手势总开关同组），
 * 由首页设置页开关控制；运行时经 [hunoia.luno.runtime.GestureCoordinator] 读取最新快照。
 */
@Serializable
@Keep
data class ExtractConfig(
    val enabled: Boolean = true,
    val longPressDelayMs: Int = 400,
    val slopDp: Float = 10f,
    val preferOcr: Boolean = false,
)
