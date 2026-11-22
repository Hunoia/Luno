package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.NotificationAlertEffect
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.FloatBallDefaults
import kotlin.math.roundToInt

/**
 * 到达提示动效的决策与播放计划（纯 JVM，可单测）。
 *
 * 触发语义（需求确认口径）：**仅新通知到达时播放一次**——「胶囊从隐藏被拉起」的时机；
 * 胶囊已可见时的后续到达不播（合并进当前展示，不叠加）；播放中的再次到达不重启。
 * 熄屏 / 锁屏 / 勿扰一律不播。所有判定都是到达时机的一次性读数，不轮询。
 * 全屏沉浸判定按用户决策不做（`:service` 进程没有可靠的系统栏可见性来源）。
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
 * 各效果的专属绘制参数：由 [buildPlan] 从配置夹取归一化，随计划下发给渲染层。
 * 渲染层只读计划不读配置——一份计划携带「这次播放怎么画」的全部信息。
 * 「速度」不进 spec：它统一作用于 [AlertPlayPlan.cycleMillis]（家族基准 ÷ 速度倍率）。
 */
sealed interface AlertEffectSpec

/** 呼吸光：屏缘圆角（dp）与衰减硬度（指数，1 = 现行指数衰减）。 */
data class EdgeGlowSpec(
    val cornerRadiusDp: Int,
    val hardness: Float,
) : AlertEffectSpec

/** 边缘流光：双带追逐、拖尾宽度（主带 σ 倍数，0 = 无拖尾）、第二带用第二颜色。 */
data class EdgeFlowSpec(
    val dualBand: Boolean,
    val trailScale: Float,
    val dualColor: Boolean,
) : AlertEffectSpec

/** 顶部粒子：大小缩放、摆幅缩放、重力指数、下落行程（dp，顶部条窗高度同源）。 */
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
 * 一次播放的完整计划：效果、循环数与单循环时长（已含应用档位与速度倍率），加两个
 * **按效果归一化**的旋钮与该效果的专属绘制参数（[spec]）。
 *
 * - [brightness] 0..1：亮度滑杆归一化，映射为各效果的 alpha 峰值。
 * - [amount] 0..1：量旋钮归一化（光带宽度 / 粒子数量 / 扩散行程 / 主带宽）；
 *   脉冲 / 涟漪无量旋钮，恒 0。窗口几何（光环行程、粒子窗高）也从它或 [spec] 推导，
 *   宿主与效果共用同一份口径。
 */
data class AlertPlayPlan(
    val effect: NotificationAlertEffect,
    /** 循环次数（呼吸圈数 / 绕缘圈数 / 粒子波次 / 光环圈数 / 脉冲次数 / 扫过次数）。 */
    val cycles: Int,
    /** 单循环时长（ms）：效果家族基准 × 应用档位 ÷ 速度倍率。 */
    val cycleMillis: Int,
    val brightness: Float,
    val amount: Float,
    val spec: AlertEffectSpec,
) {
    /** 总时长（ms）：熔断兜底与窗口拆除都以此为基准。 */
    val totalMillis: Int get() = cycleMillis * cycles
}

/** 是否播放：总开关 + 场景抑制，全部通过才播。 */
fun shouldPlay(alert: NotificationAlertSettings, env: AlertEnvironment): Boolean {
    if (!alert.enabled) return false
    if (!env.screenOn) return false
    if (env.keyguardLocked) return false
    if (env.dndActive) return false
    return true
}

/** buildPlan 的中间量：配置原值（含专属 spec），统一在出口处夹取归一化。 */
private data class PlanInputs(
    val cycles: Int,
    val brightness: Int,
    val amount: Int,
    val amountMin: Int,
    val amountMax: Int,
    val speed: Int,
    val spec: AlertEffectSpec,
)

/** 所选效果的专属参数 → 归一化计划：滑杆值夹进 [FloatBallDefaults] 约束，时长由调用方按家族传入。 */
fun buildPlan(
    alert: NotificationAlertSettings,
    cycleMillis: (NotificationAlertEffect) -> Int,
): AlertPlayPlan {
    val effect = alert.effect
    val d = FloatBallDefaults
    val inputs = when (effect) {
        NotificationAlertEffect.EdgeGlow -> PlanInputs(
            cycles = alert.edgeGlow.cycles,
            brightness = alert.edgeGlow.brightness,
            amount = alert.edgeGlow.bandWidthDp,
            amountMin = d.AlertEdgeGlowBandWidthDpMin,
            amountMax = d.AlertEdgeGlowBandWidthDpMax,
            speed = alert.edgeGlow.speed,
            spec = EdgeGlowSpec(
                cornerRadiusDp = alert.edgeGlow.cornerRadiusDp
                    .coerceIn(d.AlertEdgeGlowCornerDpMin, d.AlertEdgeGlowCornerDpMax),
                hardness = alert.edgeGlow.hardness
                    .coerceIn(d.AlertEdgeGlowHardnessMin, d.AlertEdgeGlowHardnessMax).toFloat(),
            ),
        )

        NotificationAlertEffect.EdgeFlow -> PlanInputs(
            cycles = alert.edgeFlow.cycles,
            brightness = alert.edgeFlow.brightness,
            amount = alert.edgeFlow.bandWidthDp,
            amountMin = d.AlertFlowBandWidthDpMin,
            amountMax = d.AlertFlowBandWidthDpMax,
            speed = alert.edgeFlow.speed,
            spec = EdgeFlowSpec(
                dualBand = alert.edgeFlow.dualBand,
                trailScale = alert.edgeFlow.trailScale
                    .coerceIn(d.AlertFlowTrailScaleMin, d.AlertFlowTrailScaleMax) / 100f,
                dualColor = alert.edgeFlow.dualColor,
            ),
        )

        NotificationAlertEffect.TopParticles -> PlanInputs(
            cycles = alert.particles.waves,
            brightness = alert.particles.brightness,
            amount = alert.particles.count,
            amountMin = d.AlertParticlesCountMin,
            amountMax = d.AlertParticlesCountMax,
            speed = alert.particles.speed,
            spec = ParticlesSpec(
                sizeScale = alert.particles.sizeScale
                    .coerceIn(d.AlertParticlesSizeScaleMin, d.AlertParticlesSizeScaleMax) / 100f,
                swayScale = alert.particles.swayScale
                    .coerceIn(d.AlertParticlesSwayScaleMin, d.AlertParticlesSwayScaleMax) / 100f,
                gravityExp = alert.particles.gravity
                    .coerceIn(d.AlertParticlesGravityMin, d.AlertParticlesGravityMax).toFloat(),
                travelDp = alert.particles.travelDp
                    .coerceIn(d.AlertParticlesTravelDpMin, d.AlertParticlesTravelDpMax),
            ),
        )

        NotificationAlertEffect.BallRing -> PlanInputs(
            cycles = alert.ring.cycles,
            brightness = alert.ring.brightness,
            amount = alert.ring.travel,
            amountMin = d.AlertRingTravelMin,
            amountMax = d.AlertRingTravelMax,
            speed = alert.ring.speed,
            spec = RingSpec(
                strokeWidthDp = alert.ring.strokeWidthDp
                    .coerceIn(d.AlertRingStrokeWidthDpMin, d.AlertRingStrokeWidthDpMax),
                ringsPerCycle = alert.ring.ringsPerCycle
                    .coerceIn(d.AlertRingRingsPerCycleMin, d.AlertRingRingsPerCycleMax),
                haloScale = alert.ring.haloScale
                    .coerceIn(d.AlertHaloScaleMin, d.AlertHaloScaleMax) / 100f,
            ),
        )

        NotificationAlertEffect.FullRipple -> PlanInputs(
            cycles = alert.fullRipple.cycles,
            brightness = alert.fullRipple.brightness,
            amount = 0,
            amountMin = 0,
            amountMax = 1,
            speed = alert.fullRipple.speed,
            spec = RippleSpec(
                strokeWidthDp = alert.fullRipple.strokeWidthDp
                    .coerceIn(d.AlertRippleStrokeWidthDpMin, d.AlertRippleStrokeWidthDpMax),
                centerAtBall = alert.fullRipple.centerAtBall,
            ),
        )

        NotificationAlertEffect.CapsulePulse -> PlanInputs(
            cycles = alert.pulse.cycles,
            brightness = alert.pulse.brightness,
            amount = 0,
            amountMin = 0,
            amountMax = 1,
            speed = alert.pulse.speed,
            spec = PulseSpec(
                glowWidthDp = alert.pulse.glowWidthDpTenths
                    .coerceIn(d.AlertPulseGlowWidthTenthsMin, d.AlertPulseGlowWidthTenthsMax) / 10f,
                haloScale = alert.pulse.haloScale
                    .coerceIn(d.AlertHaloScaleMin, d.AlertHaloScaleMax) / 100f,
            ),
        )

        NotificationAlertEffect.CapsuleShimmer -> PlanInputs(
            cycles = alert.shimmer.cycles,
            brightness = alert.shimmer.brightness,
            amount = alert.shimmer.bandWidth,
            amountMin = d.AlertShimmerBandWidthMin,
            amountMax = d.AlertShimmerBandWidthMax,
            speed = alert.shimmer.speed,
            spec = ShimmerSpec(
                trailScale = alert.shimmer.trailScale
                    .coerceIn(d.AlertShimmerTrailScaleMin, d.AlertShimmerTrailScaleMax) / 100f,
                reverse = alert.shimmer.reverse,
            ),
        )
    }
    val amountMin = inputs.amountMin.toFloat()
    val amountMax = inputs.amountMax.toFloat().coerceAtLeast(amountMin + 1f)
    val speedPercent = inputs.speed
        .coerceIn(d.AlertSpeedPercentMin, d.AlertSpeedPercentMax).toFloat()
    return AlertPlayPlan(
        effect = effect,
        cycles = inputs.cycles.coerceIn(d.AlertCyclesMin, d.AlertCyclesMax),
        cycleMillis = (cycleMillis(effect).coerceAtLeast(1) * 100f / speedPercent)
            .roundToInt().coerceAtLeast(1),
        brightness = inputs.brightness.coerceIn(d.AlertBrightnessMin, d.AlertBrightnessMax) / 100f,
        amount = (inputs.amount.coerceIn(inputs.amountMin, inputs.amountMax) - amountMin) /
            (amountMax - amountMin),
        spec = inputs.spec,
    )
}
