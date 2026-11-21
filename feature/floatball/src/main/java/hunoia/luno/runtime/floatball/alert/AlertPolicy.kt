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

/** 一次播放的完整计划：效果、强度、循环数与单循环时长（已含应用动效档位）。 */
data class AlertPlayPlan(
    val effect: NotificationAlertEffect,
    /** 强度 0..1（已归一），各效果自行映射为透明度峰值 / 粒子数量等。 */
    val intensity: Float,
    /** 循环次数（呼吸圈数 / 粒子波次 / 光环圈数 / 扫光次数）。 */
    val cycles: Int,
    /** 单循环时长（ms），按效果来自各自 `MotionFamily.Alert*` 家族（已含应用档位）。 */
    val cycleMillis: Int,
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

/** 配置 → 播放计划：滑杆值夹进 [FloatBallDefaults] 约束，时长由调用方按家族基准传入。 */
fun buildPlan(alert: NotificationAlertSettings, cycleMillis: Int): AlertPlayPlan = AlertPlayPlan(
    effect = alert.effect,
    intensity = alert.intensity
        .coerceIn(FloatBallDefaults.AlertIntensityMin, FloatBallDefaults.AlertIntensityMax) / 100f,
    cycles = alert.pace.coerceIn(FloatBallDefaults.AlertPaceMin, FloatBallDefaults.AlertPaceMax),
    cycleMillis = cycleMillis.coerceAtLeast(1),
)
