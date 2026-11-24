package hunoia.luno.core.effect

import kotlinx.serialization.Serializable

/**
 * 到达提示 / 通用效果播放的对外契约（纯 JVM，可跨进程序列化）。
 *
 * 一次请求只引用**一个预设**：预设内部可以组合 1–5 层（层类别 / 宿主 / 参数都在
 * 预设里，见 `AlertEffectModel`），调用方不再自己声明宿主或拼层。要自定义组合，
 * 先在预设库里保存成预设，再按 id 播放。
 *
 * 请求经 Intent + JSON 送到 `:service`；服务未运行时由调用进程托管效果窗。
 * 渲染实现不读配置：配置 → 计划 → 请求都由调用侧完成。
 */

/** 并发/打断语义：由请求显式指定，播放器不得自行改写。 */
@Serializable
enum class AlertConcurrency {
    /** 有动画在播时拒绝（默认，与通知到达语义一致）。 */
    Reject,

    /** 新请求立即打断旧动画。 */
    Replace,

    /** 排队；实现上限由播放器决定（约定最多 1 个待播）。 */
    Queue,
}

/** 屏幕绝对坐标矩形（px）；舞台锚点缺省时居中。 */
@Serializable
data class AlertRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * 揭示类内容。正文由调用方负责截断与合规；通知路径只传标题 + 应用图标，
 * 保持「动效层不落日志、不扩散正文」的口径。
 */
@Serializable
data class AlertContent(
    val title: String? = null,
    val body: String? = null,
    /** 应用图标来源包名；null = 不渲染图标。 */
    val iconPackage: String? = null,
)

/**
 * 一次播放请求。
 *
 * [id] 由调用方生成（UUID），用于日志、取消与完成回调匹配；
 * [presetId] 是内置或素材库里的预设 id；[speed] 是全局倍率（1 = 预设基准），
 * [intensity] 是全局强度（0..2，null = 预设原样）；[seed] 为 null 时由播放器取随机种子。
 */
@Serializable
data class AlertEffectRequest(
    val id: String,
    val presetId: String,
    val concurrency: AlertConcurrency = AlertConcurrency.Reject,
    val anchor: AlertRect? = null,
    val content: AlertContent? = null,
    val speed: Float = 1f,
    val intensity: Float? = null,
    val seed: Int? = null,
)

/** 取消令牌：跨进程实现只保证 [cancel] 有效；完成/等待由调用侧的可选回调承担。 */
interface AlertEffectHandle {
    val requestId: String

    fun cancel()
}

/** 播放器：`play` 返回 null = 被并发策略拒绝或不可用（原因由实现记日志）。 */
interface AlertEffectPlayer {
    fun play(request: AlertEffectRequest): AlertEffectHandle?

    fun cancel(requestId: String)
}