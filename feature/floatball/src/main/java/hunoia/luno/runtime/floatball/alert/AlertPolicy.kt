package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.NotificationAlertEffect
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.FloatBallDefaults

/**
 * 到达提示动效的决策与播放计划（纯 JVM，可单测）。
 *
 * 触发语义（需求确认结论）：**仅新通知到达时播放一次**——「胶囊从隐藏被拉起」的时机；
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
 * 一次播放的完整计划：效果、循环数与单循环时长（已含应用动效档位），加两个**按效果
 * 归一化**的旋钮——各效果在绘制层按自己的语义映射（见 AlertTuning），计划层不解释含义。
 *
 * - [brightness] 0..1：亮度滑杆归一化，映射为各效果的 alpha 峰值。
 * - [amount] 0..1：量旋钮归一化（光带宽度 / 粒子数量 / 扩散行程 / 主带宽）；
 *   脉冲无量旋钮，恒 0。窗口几何（光环行程）也从它推导，宿主与效果共用同一份口径。
 */
data class AlertPlayPlan(
    val effect: NotificationAlertEffect,
    /** 循环次数（呼吸圈数 / 粒子波次 / 光环圈数 / 脉冲次数 / 扫过次数）。 */
    val cycles: Int,
    /** 单循环时长（ms），按效果来自各自 `MotionFamily.Alert*` 家族（已含应用档位）。 */
    val cycleMillis: Int,
    val brightness: Float,
    val amount: Float,
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

/** 所选效果的专属参数 → 归一化计划：滑杆值夹进 [FloatBallDefaults] 约束，时长由调用方按家族传入。 */
fun buildPlan(
    alert: NotificationAlertSettings,
    cycleMillis: (NotificationAlertEffect) -> Int,
): AlertPlayPlan {
    val effect = alert.effect
    val d = FloatBallDefaults
    val raw: Triple<Int, Int, IntArray> = when (effect) {
        NotificationAlertEffect.EdgeGlow -> Triple(
            alert.edgeGlow.cycles,
            alert.edgeGlow.brightness,
            intArrayOf(alert.edgeGlow.bandWidthDp, d.AlertEdgeGlowBandWidthDpMin, d.AlertEdgeGlowBandWidthDpMax),
        )

        NotificationAlertEffect.TopParticles -> Triple(
            alert.particles.waves,
            alert.particles.brightness,
            intArrayOf(alert.particles.count, d.AlertParticlesCountMin, d.AlertParticlesCountMax),
        )

        NotificationAlertEffect.BallRing -> Triple(
            alert.ring.cycles,
            alert.ring.brightness,
            intArrayOf(alert.ring.travel, d.AlertRingTravelMin, d.AlertRingTravelMax),
        )

        NotificationAlertEffect.CapsulePulse -> Triple(
            alert.pulse.cycles,
            alert.pulse.brightness,
            intArrayOf(0, 0, 0),
        )

        NotificationAlertEffect.CapsuleShimmer -> Triple(
            alert.shimmer.cycles,
            alert.shimmer.brightness,
            intArrayOf(alert.shimmer.bandWidth, d.AlertShimmerBandWidthMin, d.AlertShimmerBandWidthMax),
        )
    }
    val (cycles, brightness, amountSpec) = raw
    val amountMin = amountSpec[1].toFloat()
    val amountMax = amountSpec[2].toFloat().coerceAtLeast(amountMin + 1f)
    return AlertPlayPlan(
        effect = effect,
        cycles = cycles.coerceIn(d.AlertCyclesMin, d.AlertCyclesMax),
        cycleMillis = cycleMillis(effect).coerceAtLeast(1),
        brightness = brightness.coerceIn(d.AlertBrightnessMin, d.AlertBrightnessMax) / 100f,
        amount = (amountSpec[0].coerceIn(amountSpec[1], amountSpec[2]) - amountMin) / (amountMax - amountMin),
    )
}
