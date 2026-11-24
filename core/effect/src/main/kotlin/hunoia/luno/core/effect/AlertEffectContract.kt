package hunoia.luno.core.effect

import kotlinx.serialization.Serializable

/**
 * 到达提示 / 通用效果播放的对外契约（纯 JVM，可跨进程序列化）。
 *
 * 一次请求携带最多 [AlertEffectMaxLayers] 层，每层引用一个全局预设并声明**宿主**；
 * 宿主决定绘制面（全屏舞台 / 屏缘几何 / 球锚点 / 胶囊轮廓 / 全屏着色），
 * 与设置页的「槽位」无关——槽位只是配置分组。
 *
 * 请求经 Intent + JSON 送到 `:service`；服务未运行时由调用进程托管效果窗。
 * 渲染实现不读配置：配置 → 计划 → 请求都由调用侧完成。
 */
const val AlertEffectMaxLayers: Int = 5

/** 效果宿主：决定绘制面与几何来源。 */
@Serializable
enum class AlertEffectHost {
    /** 全屏舞台窗（粒子 / 路径 / 揭示 / 全屏着色共用）。 */
    Stage,

    /** 舞台窗 + 屏缘周长几何。 */
    Edge,

    /** 舞台窗 + 球矩形锚点（[AlertEffectRequest.anchor] 必填）。 */
    Ball,

    /** 胶囊窗 Compose 层（通知路径）或外部 rect 的圆角近似。 */
    Capsule,

    /** 舞台窗 + 全屏着色 shader（闪光 / 暗角 / 缩放）。 */
    Screen,
}

/**
 * 效果类型：决定参数子集与渲染管线。
 *
 * 粒子类共用发射器 + 六族方程；其余保留各自专用渲染，但共用计划、宿主、时序与调色板。
 */
@Serializable
enum class AlertEffectKind {
    Particle,
    Edge,
    Ball,
    Capsule,
    Screen,
    Reveal,
}

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

/** 屏幕绝对坐标矩形（px）。[AlertEffectHost.Ball] / [AlertEffectHost.Capsule] 必填。 */
@Serializable
data class AlertRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * 颜色覆盖：调色板 id 优先；[colors] 是逐色 ARGB（1–5 色）。
 * 两者都为空 = 跟随预设自身颜色。
 */
@Serializable
data class AlertColorOverride(
    val paletteId: String? = null,
    val colors: List<Int> = emptyList(),
)

/**
 * 槽位 / 请求级覆盖（少量、非全量参数）：
 * 颜色、整体强度（0..1）、速度倍率（1 = 预设基准）。
 */
@Serializable
data class AlertOverrides(
    val color: AlertColorOverride? = null,
    val intensity: Float? = null,
    val speed: Float? = null,
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

/** 一层：预设 + 宿主 + 覆盖。同一预设可在不同宿主下各播一层。 */
@Serializable
data class AlertEffectLayer(
    val presetId: String,
    val host: AlertEffectHost,
    val overrides: AlertOverrides = AlertOverrides(),
)

/**
 * 一次播放请求。
 *
 * [id] 由调用方生成（UUID），用于日志、取消与完成回调匹配；
 * [speed] 是全局倍率（叠加各层覆盖的速度）；[seed] 为 null 时由播放器取随机种子。
 */
@Serializable
data class AlertEffectRequest(
    val id: String,
    val layers: List<AlertEffectLayer>,
    val concurrency: AlertConcurrency = AlertConcurrency.Reject,
    val anchor: AlertRect? = null,
    val content: AlertContent? = null,
    val speed: Float = 1f,
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