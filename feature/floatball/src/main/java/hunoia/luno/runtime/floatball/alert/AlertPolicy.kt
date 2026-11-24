package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.BandPath
import hunoia.luno.config.model.CapsuleShape
import hunoia.luno.config.model.CoverMask
import hunoia.luno.config.model.EdgeShape
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.RingStyle
import hunoia.luno.config.model.WashShape

/**
 * 渲染层的绘制器（**不落盘**）：v6 起与配置里的 [hunoia.luno.config.model.AlertFamily]
 * 一一对应；屏缘 / 胶囊的形态切换是族内参数，不改变这里的枚举。
 */
enum class AlertRenderStyle(val category: AlertRenderCategory) {
    Edge(AlertRenderCategory.Edge),
    Ring(AlertRenderCategory.Anchor),
    Capsule(AlertRenderCategory.Capsule),
    Wash(AlertRenderCategory.Screen),
    Cover(AlertRenderCategory.Screen),
    Particles(AlertRenderCategory.Particle),
    Reveal(AlertRenderCategory.Reveal),

    ;

    val isCapsuleHost: Boolean get() = category == AlertRenderCategory.Capsule
}

/** 渲染分派的大类。 */
enum class AlertRenderCategory { Edge, Particle, Capsule, Anchor, Screen, Reveal }

/**
 * 到达时机的环境快照：调用方在事件线程一次性采齐，不在 [shouldPlay] 里碰平台 API。
 */
data class AlertEnvironment(
    /** 屏幕是否亮着（`PowerManager.isInteractive`）。 */
    val screenOn: Boolean,
    /** 是否锁屏（`KeyguardManager.isKeyguardLocked`）。 */
    val keyguardLocked: Boolean,
    /** 勿扰是否开启（勿扰档位 ≠ 全部；未知档位按不开启处理）。 */
    val dndActive: Boolean,
)

/**
 * 各族的专属绘制参数：由播放计划构建（`AlertPlanFactory`）夹取归一化，
 * 随计划下发给渲染层。渲染层只读计划不读配置。
 */
sealed interface AlertEffectSpec

/**
 * 屏缘光带：`shape` 决定当前形态（呼吸 / 滚动 / 波纹 / 雷电），
 * 其余字段按形态取用；同一时刻只画一种。
 */
data class EdgeSpec(
    val shape: EdgeShape,
    val backend: hunoia.luno.config.model.AlertRenderBackend,
    val cornerRadiusDp: Int,
    val depthDp: Float,
    val hardness: Float,
    val breathAmount: Float,
    val path: BandPath,
    val travel: Float,
    val headWidthDp: Float,
    val tailLength: Float,
    val tailFalloff: Float,
    val tailAlpha: Float,
    val headBoost: Float,
    val noise: Float,
    val dual: Boolean,
    val reverse: Boolean,
    val crests: Int,
    val waveWidthDp: Float,
    val bolts: Int,
    val boltFlashes: Int,
    val boltWindow: Float,
    val boltWidthDp: Float,
    val boltReachPercent: Int,
) : AlertEffectSpec

/** 锚点环：描边层次 / 每循环环数 / 行程 / 描边宽度 / 光晕 / 锚点。 */
data class RingSpec(
    val style: RingStyle,
    val ringsPerCycle: Int,
    val travelPercent: Int,
    val strokeWidthDp: Float,
    val haloScale: Float,
    val centerAtAnchor: Boolean,
) : AlertEffectSpec

/** 胶囊：`shape` 决定扫光 / 呼吸 / 翻转；其余字段按形态取用。 */
data class CapsuleSpec(
    val shape: CapsuleShape,
    val strokeWidthDp: Float,
    val fill: Boolean,
    val heads: Int,
    val bandWidth: Float,
    val travel: Float,
    val trailScale: Float,
    val trailAlpha: Float,
    val reverse: Boolean,
    val glowLayers: Int,
    val glowScale: Float,
    val breatheScale: Float,
    val flipDegrees: Int,
) : AlertEffectSpec

/** 全屏着色：形状 / 强度 / 缩放 / 柔化 / 是否呼吸。 */
data class WashSpec(
    val shape: WashShape,
    val amplitude: Float,
    val zoomPercent: Int,
    val softness: Float,
    val breath: Boolean,
) : AlertEffectSpec

/** 全屏覆盖：遮罩 / 强度 / 条数 / 片数 / 亮缝 / 描边 / 羽化 / 曲线。 */
data class CoverSpec(
    val mask: CoverMask,
    val amplitude: Float,
    val bands: Int,
    val shards: Int,
    val slitDp: Float,
    val rimDp: Float,
    val softness: Float,
    val curve: Float,
) : AlertEffectSpec

/**
 * 粒子参数：统一引擎直接消费层的发射器 / 运动方程 / 外观。
 * [params] 的时间字段（lifeShare / launchShare）在渲染层按单循环时长换算。
 */
data class ParticlesSpec(
    val params: ParticleParams,
) : AlertEffectSpec

/** 揭示：文字 / 图标、错峰、缩放与揭示顺序（方向）；内容来自播放请求。 */
data class RevealSpec(
    val pattern: hunoia.luno.config.model.RevealPattern,
    val title: String,
    val body: String,
    val iconPackage: String?,
    val staggerPercent: Int,
    val scaleFrom: Float,
    val direction: RevealDirection,
) : AlertEffectSpec

/**
 * 一层的播放计划：绘制器、循环数、单循环时长（已含档位与速度倍率），
 * 加透明度峰值与该族的专属绘制参数（[spec]）。
 */
data class AlertLayerPlan(
    val style: AlertRenderStyle,
    /** 循环次数（呼吸圈数 / 绕缘圈数 / 粒子波次 / 光环圈数 / 脉冲次数）。 */
    val cycles: Int,
    /** 单循环时长（ms）：预设基准 × 应用档位 ÷ 速度倍率。 */
    val cycleMillis: Int,
    val brightness: Float,
    val spec: AlertEffectSpec,
    /** 该层的调色板（null = 跟随动效回退颜色）。 */
    val palette: AlertPaletteSpec? = null,
    /** 触发到达的通知来源包（App 图标形状用；真到达有值，试播无）。 */
    val appPackage: String? = null,
    /** 预设 id：粒子种子与日志用。 */
    val presetId: String? = null,
    /** 预设内稳定的层 id；编辑器「试播这一层」按它过滤。 */
    val layerId: String? = null,
) {
    val totalMillis: Int get() = cycleMillis * cycles
}

/**
 * 一次播放的完整计划：舞台层与胶囊层分开（渲染面不同）。
 *
 * [durationMillis] = 各层 [AlertLayerPlan.totalMillis] 的最大值——组合动效同时起手，
 * 最长的那层播完就整体收场；不设总时长上限。
 */
data class AlertPlayPlan(
    val layers: List<AlertLayerPlan>,
    val seed: Int = 0,
    /** 预设自带的冲突策略：播放端统一执行，调用方不判「在播就不播」。 */
    val playbackPolicy: AlertPlaybackPolicy = AlertPlaybackPolicy.Skip,
    /**
     * 关闭屏幕时刻：播到「全黑可锁」为止，窗口保持黑屏不摘，等真正息屏再拆。
     * 只影响舞台收尾与黑幕类形态的尾部淡出，不影响普通到达 / 动作动效。
     */
    val closing: Boolean = false,
) {

    val durationMillis: Int get() = layers.maxOfOrNull { it.totalMillis } ?: 0

    /** 舞台层：画在全屏穿透窗上（边缘 / 粒子 / 球心 / 全屏着色）。 */
    val stageLayers: List<AlertLayerPlan>
        get() = layers.filter { !it.style.isCapsuleHost }

    /** 胶囊层：画在胶囊窗自己的 Compose 层。 */
    val capsuleLayers: List<AlertLayerPlan>
        get() = layers.filter { it.style.isCapsuleHost }

    val isEmpty: Boolean get() = layers.isEmpty()

    /** 只保留指定层（编辑器「试播这一层」用）；[layerId] 为 null 时原样返回。 */
    fun onlyLayer(layerId: String?): AlertPlayPlan =
        if (layerId == null) this else copy(layers = layers.filter { it.layerId == layerId })
}

/**
 * 是否播放：黑名单 + 场景抑制，全部通过才播（总开关关闭 / 未选预设时计划为空）。
 * [packageName] 是本次到达通知的来源包（试播等无来源场景传 null，不参与黑名单判定）。
 */
fun shouldPlay(
    alert: NotificationAlertSettings,
    env: AlertEnvironment,
    packageName: String? = null,
): Boolean {
    if (packageName != null && packageName in alert.appBlocklist) return false
    if (!env.screenOn) return false
    if (env.keyguardLocked) return false
    if (env.dndActive) return false
    return true
}

/** 这份计划是否依赖胶囊的存在性：胶囊层要画在胶囊窗上；球锚定的环需要胶囊矩形。 */
fun needsCapsule(plan: AlertPlayPlan): Boolean = plan.layers.any { layer ->
    layer.style.isCapsuleHost || layer.style.category == AlertRenderCategory.Anchor
}
