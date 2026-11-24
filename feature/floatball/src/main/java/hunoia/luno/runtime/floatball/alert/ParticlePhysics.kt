package hunoia.luno.runtime.floatball.alert

import kotlin.math.exp

/**
 * 粒子类的共享物理曲线（纯 JVM，可单测）。
 *
 * 三项旋钮（风 / 阻力 / 方差）对全部运动生效；这里只放「滑杆归一化值 → 曲线」的
 * 映射，不做像素换算，也不依赖 Compose。绘制层用它们求位置与尺寸。
 */

/**
 * 阻力：归一化位移随时间曲线（t=0 → 0，t=1 → 1）。
 *
 * 指数阻尼 `v(t) ∝ e^(-kt)` 的位移形式；`k` 由阻力滑杆映射到 0..[ParticlesTuning.DragMaxExp]。
 * k≈0 退化为线性（无阻力）。用在「运动进度」上而不是 alpha 上：终点仍严格落在 1。
 */
internal fun dragEase(t: Float, drag: Float): Float {
    val k = drag.coerceIn(0f, 1f) * ParticlesTuning.DragMaxExp
    if (k < 1e-3f) return t
    val denom = 1f - exp(-k)
    if (denom < 1e-6f) return t
    return ((1f - exp(-k * t)) / denom).coerceIn(0f, 1f)
}

/**
 * 风：水平位移（屏宽归一化）。负 = 左、正 = 右；二次增长，越到后段偏得越多。
 * 返回的是「屏宽的倍数」，调用方乘 `size.width`。
 */
internal fun windOffsetNorm(wind: Float, t: Float): Float =
    wind.coerceIn(-1f, 1f) * ParticlesTuning.WindAccelNorm * t * t

/**
 * 行程（屏高倍数）：滑杆百分比 × 运动自己的基准系数。
 * [percent] 已被夹到 0.1..2（见 `FloatBallDefaults`）。
 */
internal fun travelNorm(percent: Float, basis: Float): Float =
    percent.coerceIn(0f, 2f) * basis

/**
 * 粒度方差：单颗粒子的随机系数（1 ± amp），[random01] 传 0..1 的随机数。
 * 尺寸 / alpha 共用；amp 由方差滑杆映射到 0..[ParticlesTuning.VarianceAmplitude]。
 */
internal fun varianceScale(variance: Float, random01: Float): Float {
    val amp = variance.coerceIn(0f, 1f) * ParticlesTuning.VarianceAmplitude
    return 1f + (random01 * 2f - 1f) * amp
}