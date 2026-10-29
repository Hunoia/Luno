package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 识屏的长按手势（全屏任意位置按住 → 幽灵卡 → 拖到动作上松手执行）。
 *
 * - [enabled]：是否在全屏任意位置监听长按。复用 ADR-0003 的内核触摸流
 *   （[hunoia.luno.gesture.touchsource.ShizukuTouchInputSource]），因此需要 Shizuku；
 *   默认关闭，用户显式打开才启动这条常驻读取（无事件不做功）。
 * - [longPressDelayMs] / [slopDp]：按住 ≥ 阈值且位移 < slop 才算“长按”，
 *   位移超 slop 视为拖动（交给触钮手势 / 底层应用），不算识屏。
 *
 * 与「轻点取词、拖动框选」共存：这两条走识屏层自己的窗口手势，不需要 Shizuku；
 * 只有“在别人的应用上按住不放”才必须借内核触摸流。
 */
@Serializable
@Keep
data class ScreenPickGesture(
    val enabled: Boolean = false,
    val longPressDelayMs: Int = 400,
    val slopDp: Float = 10f,
)
