package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 识屏的长按手势（全屏任意位置按住 → 幽灵卡 → 拖到动作上松手执行）。
 *
 * - [enabled]：是否在全屏任意位置监听长按。复用 ADR-0003 的内核触摸流
 *   （[hunoia.luno.gesture.touchsource.ShizukuTouchInputSource]），因此需要 Shizuku；
 *   默认关闭，用户显式打开才启动这条常驻读取（无事件不做功）。
 * - [longPressDelayMs] / [slopDp]：按住 ≥ 阈值即触发；长按前位移超 slop 视为滚动，本次作废。
 * - [dragStartTimeoutMs]：长按就绪后的“取消窗口”：窗口内没拖动超过阈值，本次按住
 *   不再触发幽灵卡。0 表示不启用（按住多久都能拖动触发）。
 * - [triggerCooldownMs]：两次长按触发之间的最小间隔，防误触发连着弹卡。
 * - [ocrEnabled]：框选后的动作菜单是否显示 OCR；长按取词不再触发 OCR。
 * - [ocrScripts]：框选 OCR 要尝试的文字体系；多选时并行识别后合并。中文模型已覆盖拉丁字母。
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
    val ocrEnabled: Boolean = true,
    // 中文模型同时覆盖拉丁字母与数字（中英混排），默认只跑它：多跑一份就多一份耗时，
    // 而日文映射到同一个 zh 模型（纯重复）、韩文模型对中文只会产出垃圾块再被去重丢掉。
    val ocrScripts: List<OcrScript> = listOf(OcrScript.Chinese),
)
