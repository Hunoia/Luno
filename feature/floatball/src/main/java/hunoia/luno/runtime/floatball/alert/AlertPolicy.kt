package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.AlertStyleId
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleAlertStyle
import kotlin.math.roundToInt

/**
 * 到达提示动效的决策与播放计划（纯 JVM，可单测）。
 *
 * 触发语义（需求确认口径）：默认**只在「胶囊从隐藏被拉起」时播一次**；胶囊已可见时的
 * 后续到达不播（合并进当前展示，不叠加）；播放中的再次到达不重启。
 * 「每次新通知都播」打开后，已可见时也播——但仍有动画在播就不播（不排队、不打断）。
 * 熄屏 / 锁屏 / 勿扰 / 动效黑名单命中一律不播。所有判定都是到达时机的一次性读数，不轮询。
 * 全屏沉浸判定按用户决策不做（`:service` 进程没有可靠的系统栏可见性来源）。
 *
 * 计划是**分层**的：四类目各产生一层（类内单选样式），全部塞进同一份 [AlertPlayPlan]。
 * 舞台层画在一个全屏窗上，胶囊层画在胶囊窗自己的 Compose 层；总时长取各层最大值。
 */

/** 到达时机的环境快照：调用方在事件线程一次性采齐，不在 [shouldPlay] 里碰平台 API。 */
data class AlertEnvironment(
    /** 屏幕是否亮着（`PowerManager.isInteractive`）。 */
    val screenOn: Boolean,
    /** 是否锁屏（`KeyguardManager.isKeyguardLocked`）。 */
    val keyguardLocked: Boolean,
    /** 勿扰是否开启（勿扰档位 ≠ 全部；未知档位按不开启处理）。 */
    val dndActive: Boolean,
)

/**
 * 各样式的专属绘制参数：由 [buildPlan] 从配置夹取归一化，随计划下发给渲染层。
 * 渲染层只读计划不读配置——一份计划携带「这次播放怎么画」的全部信息。
 * 「速度」不进 spec：它统一作用于 [AlertLayerPlan.cycleMillis]（样式基准 ÷ 速度倍率）。
 */
sealed interface AlertEffectSpec

/** 呼吸光：屏缘圆角（dp）与衰减硬度（指数，1 = 现行指数衰减）。 */
data class EdgeGlowSpec(
    val cornerRadiusDp: Int,
    val hardness: Float,
) : AlertEffectSpec

/**
 * 边缘流光：带数与拖尾宽度、刃感三件套（主带宽 / 深度硬度 / 拖尾浓度）、屏缘圆角。
 *
 * [coreBand] 是周长占比（0.01 = 1%），越小越像一道利刃；[hardness] 越大主体越实、
 * 外缘收得越干脆；[trailAlpha] 是拖尾带自己的 alpha 峰值占比。
 */
data class EdgeFlowSpec(
    val dualBand: Boolean,
    val trailScale: Float,
    val dualColor: Boolean,
    val coreBand: Float,
    val hardness: Float,
    val trailAlpha: Float,
    val cornerRadiusDp: Int,
) : AlertEffectSpec

/** 顶部粒子：大小缩放、摆幅缩放、重力指数、下落行程（dp，绘制带高与下落距离同源）。 */
data class ParticlesSpec(
    val sizeScale: Float,
    val swayScale: Float,
    val gravityExp: Float,
    val travelDp: Int,
) : AlertEffectSpec

/** 球光环：核描边宽度（dp）、每循环环数、光晕缩放（0 = 无光晕）。 */
data class RingSpec(
    val strokeWidthDp: Int,
    val ringsPerCycle: Int,
    val haloScale: Float,
) : AlertEffectSpec

/** 全屏涟漪：核描边宽度（dp）、锚点是否通知球心（false = 屏幕中心）。 */
data class RippleSpec(
    val strokeWidthDp: Int,
    val centerAtBall: Boolean,
) : AlertEffectSpec

/** 胶囊脉冲：辉光宽度（dp）、光晕缩放（0 = 只剩核心描边）。 */
data class PulseSpec(
    val glowWidthDp: Float,
    val haloScale: Float,
) : AlertEffectSpec

/** 胶囊边框流光：拖尾宽度（主带倍数，0 = 无拖尾）、反向扫动。 */
data class ShimmerSpec(
    val trailScale: Float,
    val reverse: Boolean,
) : AlertEffectSpec

/**
 * 一层的播放计划：样式、循环数与单循环时长（已含应用档位与速度倍率），加两个
 * **按样式归一化**的旋钮与该样式的专属绘制参数（[spec]）。
 *
 * - [brightness] 0..1：亮度滑杆归一化，映射为各效果的 alpha 峰值。
 * - [amount] 0..1：量旋钮归一化（光带宽度 / 粒子数量 / 扩散行程 / 主带宽）；
 *   脉冲 / 涟漪无量旋钮，恒 0。
 */
data class AlertLayerPlan(
    val style: AlertStyleId,
    /** 循环次数（呼吸圈数 / 绕缘圈数 / 粒子波次 / 光环圈数 / 脉冲次数 / 扫过次数）。 */
    val cycles: Int,
    /** 单循环时长（ms）：样式基准 × 应用档位 ÷ 速度倍率。 */
    val cycleMillis: Int,
    val brightness: Float,
    val amount: Float,
    val spec: AlertEffectSpec,
) {
    /** 总时长（ms）：各层取最大值后作为舞台窗的生命周期基准。 */
    val totalMillis: Int get() = cycleMillis * cycles
}

/**
 * 一次播放的完整计划：四类目各自的一层（类内单选样式，跨类可叠加）。
 *
 * [durationMillis] = 各层 [AlertLayerPlan.totalMillis] 的最大值——组合动效同时起手、
 * 最长的那类播完就整体收场（不把短层强行拉长）。
 */
data class AlertPlayPlan(val layers: List<AlertLayerPlan>) {

    val durationMillis: Int get() = layers.maxOfOrNull { it.totalMillis } ?: 0

    /** 舞台层：画在全屏穿透窗上（边缘 / 粒子 / 球心·全屏）。 */
    val stageLayers: List<AlertLayerPlan>
        get() = layers.filter { it.style.category != AlertCategory.Capsule }

    /** 胶囊层：画在胶囊窗自己的 Compose 层（不新增窗口、不动几何）。 */
    val capsuleLayers: List<AlertLayerPlan>
        get() = layers.filter { it.style.category == AlertCategory.Capsule }

    val isEmpty: Boolean get() = layers.isEmpty()

    /** 只保留一个类目（试播单类用）；[category] 为 null 时原样返回。 */
    fun onlyCategory(category: AlertCategory?): AlertPlayPlan =
        if (category == null) this else AlertPlayPlan(layers.filter { it.style.category == category })
}

/**
 * 是否播放：总开关 + 黑名单 + 场景抑制，全部通过才播。
 *
 * [packageName] 是本次到达通知的来源包（试播等无来源场景传 null，不参与黑名单判定）。
 */
fun shouldPlay(
    alert: NotificationAlertSettings,
    env: AlertEnvironment,
    packageName: String? = null,
): Boolean {
    if (!alert.enabled) return false
    if (packageName != null && packageName in alert.appBlocklist) return false
    if (!env.screenOn) return false
    if (env.keyguardLocked) return false
    if (env.dndActive) return false
    return true
}

/**
 * 这份计划是否依赖胶囊的存在性：
 * 胶囊层要画在胶囊窗上；球心锚定的层要胶囊的屏幕矩形（涟漪可选屏幕中心，不算）。
 */
fun needsCapsule(plan: AlertPlayPlan): Boolean = plan.layers.any { layer ->
    when (layer.style) {
        is CapsuleAlertStyle -> true
        is AnchorAlertStyle ->
            layer.style == AnchorAlertStyle.Ring || (layer.spec as RippleSpec).centerAtBall
        is EdgeAlertStyle, is ParticleAlertStyle -> false
    }
}

/** buildLayer 的中间量：配置原值（含专属 spec），统一在出口处夹取归一化。 */
private data class LayerInputs(
    val cycles: Int,
    val brightness: Int,
    val amount: Int,
    val amountMin: Int,
    val amountMax: Int,
    val speed: Int,
    val spec: AlertEffectSpec,
)

/** 所选样式的专属参数 → 归一化层计划：滑杆值夹进 [FloatBallDefaults] 约束。 */
private fun LayerInputs.toLayer(
    style: AlertStyleId,
    tierFactor: (AlertStyleId) -> Float,
): AlertLayerPlan {
    val d = FloatBallDefaults
    val rawMin = amountMin.toFloat()
    val rawMax = amountMax.toFloat().coerceAtLeast(rawMin + 1f)
    val speedPercent = speed
        .coerceIn(d.AlertSpeedPercentMin, d.AlertSpeedPercentMax).toFloat()
    val base = alertStyleBaseMillis(style)
    val tier = tierFactor(style).coerceAtLeast(0.01f)
    return AlertLayerPlan(
        style = style,
        cycles = cycles.coerceIn(d.AlertCyclesMin, d.AlertCyclesMax),
        cycleMillis = (base * tier * 100f / speedPercent).roundToInt().coerceAtLeast(1),
        brightness = brightness.coerceIn(d.AlertBrightnessMin, d.AlertBrightnessMax) / 100f,
        amount = (amount.coerceIn(amountMin, amountMax) - rawMin) / (rawMax - rawMin),
        spec = spec,
    )
}

/**
 * 当前配置 → 分层播放计划：只收集开启的类目，类内按所选样式取旋钮。
 * [tierFactor] 由服务按 `MotionFamily` 档位传入（纯 JVM 不依赖 ui:design）。
 */
fun buildPlan(
    alert: NotificationAlertSettings,
    tierFactor: (AlertStyleId) -> Float,
): AlertPlayPlan {
    val layers = buildList {
        if (alert.edge.enabled) add(buildEdgeLayer(alert, tierFactor))
        if (alert.particle.enabled) add(buildParticleLayer(alert, tierFactor))
        if (alert.capsule.enabled) add(buildCapsuleLayer(alert, tierFactor))
        if (alert.anchor.enabled) add(buildAnchorLayer(alert, tierFactor))
    }
    return AlertPlayPlan(layers)
}

private fun buildEdgeLayer(
    alert: NotificationAlertSettings,
    tierFactor: (AlertStyleId) -> Float,
): AlertLayerPlan {
    val settings = alert.edge
    val d = FloatBallDefaults
    return when (val style = settings.style) {
        EdgeAlertStyle.EdgeGlow -> {
            val s = settings.edgeGlow
            LayerInputs(
                cycles = s.cycles,
                brightness = s.brightness,
                amount = s.bandWidthDp,
                amountMin = d.AlertEdgeGlowBandWidthDpMin,
                amountMax = d.AlertEdgeGlowBandWidthDpMax,
                speed = s.speed,
                spec = EdgeGlowSpec(
                    cornerRadiusDp = s.cornerRadiusDp
                        .coerceIn(d.AlertEdgeGlowCornerDpMin, d.AlertEdgeGlowCornerDpMax),
                    hardness = s.hardness
                        .coerceIn(d.AlertEdgeGlowHardnessMin, d.AlertEdgeGlowHardnessMax).toFloat(),
                ),
            ).toLayer(style, tierFactor)
        }

        EdgeAlertStyle.EdgeFlow -> {
            val s = settings.edgeFlow
            LayerInputs(
                cycles = s.cycles,
                brightness = s.brightness,
                amount = s.bandWidthDp,
                amountMin = d.AlertFlowBandWidthDpMin,
                amountMax = d.AlertFlowBandWidthDpMax,
                speed = s.speed,
                spec = EdgeFlowSpec(
                    dualBand = s.dualBand,
                    trailScale = s.trailScale
                        .coerceIn(d.AlertFlowTrailScaleMin, d.AlertFlowTrailScaleMax) / 100f,
                    dualColor = s.dualColor,
                    coreBand = s.coreBandPermille
                        .coerceIn(d.AlertFlowCoreBandPermilleMin, d.AlertFlowCoreBandPermilleMax) /
                        1000f,
                    hardness = s.hardness
                        .coerceIn(d.AlertFlowHardnessMin, d.AlertFlowHardnessMax).toFloat(),
                    trailAlpha = s.trailAlpha
                        .coerceIn(d.AlertFlowTrailAlphaMin, d.AlertFlowTrailAlphaMax) / 100f,
                    cornerRadiusDp = s.cornerRadiusDp
                        .coerceIn(d.AlertEdgeGlowCornerDpMin, d.AlertEdgeGlowCornerDpMax),
                ),
            ).toLayer(style, tierFactor)
        }
    }
}

private fun buildParticleLayer(
    alert: NotificationAlertSettings,
    tierFactor: (AlertStyleId) -> Float,
): AlertLayerPlan {
    val settings = alert.particle
    val d = FloatBallDefaults
    return when (val style = settings.style) {
        ParticleAlertStyle.TopRain -> {
            val s = settings.particles
            LayerInputs(
                cycles = s.waves,
                brightness = s.brightness,
                amount = s.count,
                amountMin = d.AlertParticlesCountMin,
                amountMax = d.AlertParticlesCountMax,
                speed = s.speed,
                spec = ParticlesSpec(
                    sizeScale = s.sizeScale
                        .coerceIn(d.AlertParticlesSizeScaleMin, d.AlertParticlesSizeScaleMax) / 100f,
                    swayScale = s.swayScale
                        .coerceIn(d.AlertParticlesSwayScaleMin, d.AlertParticlesSwayScaleMax) / 100f,
                    gravityExp = s.gravity
                        .coerceIn(d.AlertParticlesGravityMin, d.AlertParticlesGravityMax).toFloat(),
                    travelDp = s.travelDp
                        .coerceIn(d.AlertParticlesTravelDpMin, d.AlertParticlesTravelDpMax),
                ),
            ).toLayer(style, tierFactor)
        }
    }
}

private fun buildCapsuleLayer(
    alert: NotificationAlertSettings,
    tierFactor: (AlertStyleId) -> Float,
): AlertLayerPlan {
    val settings = alert.capsule
    val d = FloatBallDefaults
    return when (val style = settings.style) {
        CapsuleAlertStyle.Pulse -> {
            val s = settings.pulse
            LayerInputs(
                cycles = s.cycles,
                brightness = s.brightness,
                amount = 0,
                amountMin = 0,
                amountMax = 1,
                speed = s.speed,
                spec = PulseSpec(
                    glowWidthDp = s.glowWidthDpTenths
                        .coerceIn(d.AlertPulseGlowWidthTenthsMin, d.AlertPulseGlowWidthTenthsMax) / 10f,
                    haloScale = s.haloScale
                        .coerceIn(d.AlertHaloScaleMin, d.AlertHaloScaleMax) / 100f,
                ),
            ).toLayer(style, tierFactor)
        }

        CapsuleAlertStyle.Shimmer -> {
            val s = settings.shimmer
            LayerInputs(
                cycles = s.cycles,
                brightness = s.brightness,
                amount = s.bandWidth,
                amountMin = d.AlertShimmerBandWidthMin,
                amountMax = d.AlertShimmerBandWidthMax,
                speed = s.speed,
                spec = ShimmerSpec(
                    trailScale = s.trailScale
                        .coerceIn(d.AlertShimmerTrailScaleMin, d.AlertShimmerTrailScaleMax) / 100f,
                    reverse = s.reverse,
                ),
            ).toLayer(style, tierFactor)
        }
    }
}

private fun buildAnchorLayer(
    alert: NotificationAlertSettings,
    tierFactor: (AlertStyleId) -> Float,
): AlertLayerPlan {
    val settings = alert.anchor
    val d = FloatBallDefaults
    return when (val style = settings.style) {
        AnchorAlertStyle.Ring -> {
            val s = settings.ring
            LayerInputs(
                cycles = s.cycles,
                brightness = s.brightness,
                amount = s.travel,
                amountMin = d.AlertRingTravelMin,
                amountMax = d.AlertRingTravelMax,
                speed = s.speed,
                spec = RingSpec(
                    strokeWidthDp = s.strokeWidthDp
                        .coerceIn(d.AlertRingStrokeWidthDpMin, d.AlertRingStrokeWidthDpMax),
                    ringsPerCycle = s.ringsPerCycle
                        .coerceIn(d.AlertRingRingsPerCycleMin, d.AlertRingRingsPerCycleMax),
                    haloScale = s.haloScale
                        .coerceIn(d.AlertHaloScaleMin, d.AlertHaloScaleMax) / 100f,
                ),
            ).toLayer(style, tierFactor)
        }

        AnchorAlertStyle.Ripple -> {
            val s = settings.fullRipple
            LayerInputs(
                cycles = s.cycles,
                brightness = s.brightness,
                amount = 0,
                amountMin = 0,
                amountMax = 1,
                speed = s.speed,
                spec = RippleSpec(
                    strokeWidthDp = s.strokeWidthDp
                        .coerceIn(d.AlertRippleStrokeWidthDpMin, d.AlertRippleStrokeWidthDpMax),
                    centerAtBall = s.centerAtBall,
                ),
            ).toLayer(style, tierFactor)
        }
    }
}
