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
 * - [dragStartTimeoutMs]：长按达到后等待拖动的时间窗口；窗口内拖动超过 [slopDp] 才触发，
 *   没拖动则本次长按作废；0 = 长按即触发。
 * - [triggerCooldownMs]：两次长按触发之间的最小间隔，防误触发连着弹卡。
 * - [keepAfterRelease]：抬手后不收起幽灵卡；再按住可拖动，长按其他位置另选。
 * - [ocrEnabled]：a11y 节点取不到文字时是否用 OCR 兜底（图片内文字也在此列）。
 * - [ocrScripts]：要尝试的文字体系；多选时并行识别后合并。中文模型已覆盖拉丁字母。
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
    val dragStartTimeoutMs: Int = 3_000,
    val triggerCooldownMs: Int = 800,
    val keepAfterRelease: Boolean = false,
    val ocrEnabled: Boolean = true,
    val ocrScripts: List<OcrScript> = listOf(
        OcrScript.Chinese,
        OcrScript.Japanese,
        OcrScript.Korean,
    ),
)
