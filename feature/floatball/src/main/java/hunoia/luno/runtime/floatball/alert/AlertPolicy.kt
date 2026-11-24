package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.AlertRenderBackend
import hunoia.luno.config.model.CapsuleFlip
import hunoia.luno.config.model.CapsulePulse
import hunoia.luno.config.model.CapsuleShape
import hunoia.luno.config.model.CapsuleSweep
import hunoia.luno.config.model.CoverBands
import hunoia.luno.config.model.CoverCircle
import hunoia.luno.config.model.CoverIris
import hunoia.luno.config.model.CoverMask
import hunoia.luno.config.model.CoverShards
import hunoia.luno.config.model.CoverSlit
import hunoia.luno.config.model.EdgeBreathing
import hunoia.luno.config.model.EdgeLightning
import hunoia.luno.config.model.EdgeShape
import hunoia.luno.config.model.EdgeSweep
import hunoia.luno.config.model.EdgeWave
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.RingHalo
import hunoia.luno.config.model.RingNested
import hunoia.luno.config.model.RingStyle
import hunoia.luno.config.model.WashRadial
import hunoia.luno.config.model.WashRing
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
 * 屏缘：`shape` 决定当前形态，四个形态参数块各自独立；
 * 族级只有后端与圆角。渲染层按 `shape` 只读对应那一块。
 */
data class EdgeSpec(
    val shape: EdgeShape,
    val backend: AlertRenderBackend,
    val cornerRadiusDp: Int,
    val breathing: EdgeBreathing,
    val sweep: EdgeSweep,
    val wave: EdgeWave,
    val lightning: EdgeLightning,
) : AlertEffectSpec

/** 锚点环：三种层次共用环数 / 行程 / 描边，额外参数按层次各取一份。 */
data class RingSpec(
    val style: RingStyle,
    val centerAtAnchor: Boolean,
    val strokeWidthDp: Float,
    val ringsPerCycle: Int,
    val travelPercent: Int,
    val nested: RingNested,
    val halo: RingHalo,
) : AlertEffectSpec

/** 胶囊：`shape` 决定扫光 / 呼吸 / 翻转，参数块各自独立。 */
data class CapsuleSpec(
    val shape: CapsuleShape,
    val sweep: CapsuleSweep,
    val pulse: CapsulePulse,
    val flip: CapsuleFlip,
) : AlertEffectSpec

/** 全屏着色：造型 + 强度 / 呼吸 + 造型自己的几何参数。 */
data class WashSpec(
    val shape: WashShape,
    val amplitude: Float,
    val breath: Boolean,
    val radial: WashRadial,
    val ring: WashRing,
) : AlertEffectSpec

/** 全屏覆盖：遮罩 + 强度 / 曲线 + 遮罩自己的几何参数。 */
data class CoverSpec(
    val mask: CoverMask,
    val amplitude: Float,
    val curve: Float,
    val circle: CoverCircle,
    val iris: CoverIris,
    val slit: CoverSlit,
    val bands: CoverBands,
    val shards: CoverShards,
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
    /** 相对播放起点的延迟（ms）：0 = 与其它层同时起手。 */
    val startAt: Int = 0,
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

    /** 该层在整场里的收尾时刻（含延迟）。 */
    val endMillis: Int get() = startAt + totalMillis
}

/**
 * 一次播放的完整计划：舞台层与胶囊层分开（渲染面不同）。
 *
 * [durationMillis] = 各层 [AlertLayerPlan.endMillis] 的最大值——层可以有自己的
 * 延迟，最晚收尾的那层决定整场时长；不设总时长上限。
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

    val durationMillis: Int get() = layers.maxOfOrNull { it.endMillis } ?: 0

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
