package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertSlot
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleParams

/**
 * 渲染层的样式族（**不落盘**）：只用于渲染分派与种子。
 *
 * 配置（v3）已改为「全局预设 + 宿主变体」，预设参数直接写进各类 `*Spec`；
 * 这里的枚举只是渲染端的绘制器选择器，和设置页 / 备份无关。
 */
sealed interface AlertRenderStyle {
    val category: AlertRenderCategory
}

/** 渲染分派的大类。 */
enum class AlertRenderCategory { Edge, Particle, Capsule, Anchor, Screen, Reveal }

/** 屏缘绘制器。 */
enum class EdgeAlertStyle : AlertRenderStyle {
    EdgeGlow,
    EdgeFlow,
    EdgeRipple,
    EdgeFireworks,
    EdgeBladeRise,
    Ribbon,
    Lightning,
    ;

    override val category: AlertRenderCategory get() = AlertRenderCategory.Edge
}

/** 粒子绘制器（v3 统一引擎，参数来自 [ParticlesSpec]；枚举只决定种子与日志）。 */
enum class ParticleAlertStyle : AlertRenderStyle {
    TopRain,
    BottomFountain,
    Meteor,
    Confetti,
    Firefly,
    Path,

    ;

    override val category: AlertRenderCategory get() = AlertRenderCategory.Particle
}

/** 胶囊窗自己的 Compose 层。 */
enum class CapsuleAlertStyle : AlertRenderStyle {
    Pulse,
    Shimmer,
    BreathOutline,
    InnerSweep,
    Flip3D,
    DualChase,
    ;

    override val category: AlertRenderCategory get() = AlertRenderCategory.Capsule
}

/** 球锚点 / 全屏涟漪。 */
enum class AnchorAlertStyle : AlertRenderStyle {
    Ring,
    Ripple,
    BallBurst,
    BallHalo,
    Spiral,
    Orbit3D,
    ;

    override val category: AlertRenderCategory get() = AlertRenderCategory.Anchor
}

/** 舞台窗的全屏着色。 */
enum class ScreenAlertStyle : AlertRenderStyle {
    Flash,
    Vignette,
    ZoomPulse,
    ;

    override val category: AlertRenderCategory get() = AlertRenderCategory.Screen
}

/** 揭示类：文字逐字 / 应用图标逐元素。 */
enum class RevealAlertStyle : AlertRenderStyle {
    Text,
    Icon,
    ;

    override val category: AlertRenderCategory get() = AlertRenderCategory.Reveal
}

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
 * 各样式的专属绘制参数：由播放计划构建（`AlertSlotPlanFactory`）夹取归一化，
 * 随计划下发给渲染层。渲染层只读计划不读配置。
 */
sealed interface AlertEffectSpec

/** 呼吸光：屏缘圆角（dp）与衰减硬度（指数，1 = 指数衰减）。 */
data class EdgeGlowSpec(
    val cornerRadiusDp: Int,
    val hardness: Float,
) : AlertEffectSpec

/** 边缘流光：带数与拖尾宽度、刃感三件套（主带宽 / 深度硬度 / 拖尾浓度）、屏缘圆角。 */
data class EdgeFlowSpec(
    val dualBand: Boolean,
    val trailScale: Float,
    val dualColor: Boolean,
    val coreBand: Float,
    val hardness: Float,
    val trailAlpha: Float,
    val cornerRadiusDp: Int,
) : AlertEffectSpec

/**
 * 粒子参数：v3 统一引擎直接消费预设的发射器 / 运动方程 / 外观。
 * [params] 的时间字段（lifeShare）在渲染层按单循环时长换算。
 */
data class ParticlesSpec(
    val params: ParticleParams,
) : AlertEffectSpec

/** 球光环：核描边宽度（dp）、每循环环数、光晕缩放（0 = 无光晕）。 */
data class RingSpec(
    val strokeWidthDp: Float,
    val ringsPerCycle: Int,
    val haloScale: Float,
) : AlertEffectSpec

/** 全屏涟漪：核描边宽度（dp）、光晕强度、锚点是否通知球心。 */
data class RippleSpec(
    val strokeWidthDp: Float,
    val haloScale: Float,
    val centerAtBall: Boolean,
) : AlertEffectSpec

/** 胶囊脉冲：辉光宽度（dp）、光晕缩放。 */
data class PulseSpec(
    val glowWidthDp: Float,
    val haloScale: Float,
) : AlertEffectSpec

/** 胶囊边框流光：描边宽度（dp）、拖尾宽度、拖尾浓度、反向扫动。 */
data class ShimmerSpec(
    val strokeWidthDp: Float,
    val trailScale: Float,
    val trailAlpha: Float,
    val reverse: Boolean,
) : AlertEffectSpec

/** 边缘波纹：波峰数 + 屏缘圆角。 */
data class EdgeRippleSpec(
    val crestCount: Int,
    val cornerRadiusDp: Int,
) : AlertEffectSpec

/** 屏缘烟花：爆点数 + 粒径（dp）+ 屏缘圆角。 */
data class EdgeFireworksSpec(
    val burstCount: Int,
    val particleSizeDp: Float,
    val cornerRadiusDp: Int,
) : AlertEffectSpec

/** 双刃上升：刃高（dp）。 */
data class EdgeBladeRiseSpec(
    val bladeHeightDp: Int,
) : AlertEffectSpec

/** 胶囊呼吸描边：描边宽度（dp）与呼吸缩放（1.0 = 0）。 */
data class BreathOutlineSpec(
    val strokeWidthDp: Float,
    val breatheScale: Float,
) : AlertEffectSpec

/** 胶囊内部扫光：主带宽度（胶囊宽占比）与反向扫动。 */
data class InnerSweepSpec(
    val bandWidth: Float,
    val reverse: Boolean,
) : AlertEffectSpec

/** 球心爆发：粒子数、粒径（dp）、迸射行程（dp）。 */
data class BallBurstSpec(
    val count: Int,
    val particleSizeDp: Float,
    val spreadDp: Float,
) : AlertEffectSpec

/** 球心光晕：半径（球径 %）与光晕强度缩放。 */
data class BallHaloSpec(
    val radiusPercent: Int,
    val haloScale: Float,
) : AlertEffectSpec

/** 缎带：沿屏缘的多层柔光带（宽度 / 拖尾 / 屏缘圆角）。 */
data class EdgeRibbonSpec(
    val bandWidthDp: Float,
    val trailScale: Float,
    val trailAlpha: Float,
    val cornerRadiusDp: Int,
) : AlertEffectSpec

/** 闪电：沿屏缘随机落点的折线闪电（落点数 / 屏缘圆角 / 线宽）。 */
data class EdgeLightningSpec(
    val strikeCount: Int,
    val cornerRadiusDp: Int,
    val lineWidthDp: Float,
) : AlertEffectSpec

/** 球锚点螺旋：粒子数、粒径、行程（球径 %）与旋转圈数。 */
data class BallSpiralSpec(
    val count: Int,
    val particleSizeDp: Float,
    val travelPercent: Int,
    val turns: Float,
) : AlertEffectSpec

/** 球锚点 3D 环绕：粒子数、粒径、轨道半径（球径 %）与倾角（0..1）。 */
data class BallOrbit3DSpec(
    val count: Int,
    val particleSizeDp: Float,
    val travelPercent: Int,
    val tilt: Float,
) : AlertEffectSpec

/** 胶囊 3D 翻转：描边宽度与翻转角度。 */
data class Flip3DSpec(
    val strokeWidthDp: Float,
    val flipDegrees: Int,
) : AlertEffectSpec

/** 胶囊双色追逐：描边宽度、主带宽度、拖尾与双色。 */
data class DualChaseSpec(
    val strokeWidthDp: Float,
    val bandWidth: Float,
    val trailScale: Float,
    val trailAlpha: Float,
) : AlertEffectSpec

/** 揭示：文字 / 图标、错峰、缩放与遮罩方向；内容来自播放请求。 */
data class RevealSpec(
    val pattern: RevealAlertStyle,
    val title: String,
    val body: String,
    val iconPackage: String?,
    val staggerPercent: Int,
    val scaleFrom: Float,
) : AlertEffectSpec

/** 全屏着色：形态 + 强度 + 缩放 / 柔化参数。 */
data class ScreenSpec(
    val pattern: ScreenAlertStyle,
    val amplitude: Float,
    val zoomPercent: Int,
    val vignetteSoftness: Float,
) : AlertEffectSpec

/**
 * 一层的播放计划：渲染样式、循环数与单循环时长（已含档位与速度倍率），加两个
 * **按样式归一化**的旋钮与该样式的专属绘制参数（[spec]）。
 *
 * - [brightness] 0..1：映射为各效果的 alpha 峰值。
 * - [amount] 0..1：量旋钮归一化（光带宽度 / 扩散行程 / 主带宽）；
 *   脉冲 / 涟漪等无量旋钮恒 0.5。
 */
data class AlertLayerPlan(
    val style: AlertRenderStyle,
    /** 循环次数（呼吸圈数 / 绕缘圈数 / 粒子波次 / 光环圈数 / 脉冲次数）。 */
    val cycles: Int,
    /** 单循环时长（ms）：预设基准 × 应用档位 ÷ 速度倍率。 */
    val cycleMillis: Int,
    val brightness: Float,
    val amount: Float,
    val spec: AlertEffectSpec,
    /** 该层的调色板（null = 跟随动效回退颜色）。 */
    val palette: AlertPaletteSpec? = null,
    /** 触发到达的通知来源包（App 图标形状用；真到达有值，试播无）。 */
    val appPackage: String? = null,
    /** 预设 id：粒子种子与日志用。 */
    val presetId: String? = null,
    /** 配置槽位（外部调用可为 null）；单槽试播按它过滤。 */
    val slot: AlertSlot? = null,
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
) {

    val durationMillis: Int get() = layers.maxOfOrNull { it.totalMillis } ?: 0

    /** 舞台层：画在全屏穿透窗上（边缘 / 粒子 / 球心 / 全屏着色）。 */
    val stageLayers: List<AlertLayerPlan>
        get() = layers.filter { it.style.category != AlertRenderCategory.Capsule }

    /** 胶囊层：画在胶囊窗自己的 Compose 层。 */
    val capsuleLayers: List<AlertLayerPlan>
        get() = layers.filter { it.style.category == AlertRenderCategory.Capsule }

    val isEmpty: Boolean get() = layers.isEmpty()

    /** 只保留指定槽位的层（试播单槽用）；[slots] 为空时原样返回。 */
    fun onlySlots(slots: Set<AlertSlot>): AlertPlayPlan =
        if (slots.isEmpty()) this else copy(layers = layers.filter { it.slot in slots })
}

/**
 * 是否播放：黑名单 + 场景抑制，全部通过才播（没有总开关，五槽全关时计划为空）。
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

/** 这份计划是否依赖胶囊的存在性：胶囊层要画在胶囊窗上；球心锚定的涟漪要胶囊矩形。 */
fun needsCapsule(plan: AlertPlayPlan): Boolean = plan.layers.any { layer ->
    when (layer.style) {
        is CapsuleAlertStyle -> true
        is AnchorAlertStyle ->
            layer.style == AnchorAlertStyle.Ring ||
                layer.style == AnchorAlertStyle.BallBurst ||
                layer.style == AnchorAlertStyle.BallHalo ||
                (layer.spec as RippleSpec).centerAtBall
        else -> false
    }
}