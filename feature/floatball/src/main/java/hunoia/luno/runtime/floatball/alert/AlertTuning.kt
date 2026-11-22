package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.FloatBallDefaults
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 到达提示动效的**每效果独立调校参数**（需求确认口径：参数不共用，按各效果实际观感定值）。
 *
 * 这里只放「量的区间映射」之外的曲线形状常量（衰减 stop、层占比、缓动指数、辉光比例）；
 * 用户可调的旋钮区间（光带宽度 / 粒子数量与形状 / 描边宽度 / 光晕缩放 / 主带宽 / 速度）
 * 在 `FloatBallDefaults` 里登记（滑杆约束与绘制映射同源），随 `AlertPlayPlan` /
 * `AlertEffectSpec` 下发；时长按效果走 `MotionFamily.Alert*` 家族（唯一事实源在
 * ui:design 的 Motion.kt），速度倍率在 buildPlan 统一折算。所有 dp 值按 dp 语义命名，
 * 绘制处 `.dp.toPx()` 换算；改动任一值都应先试播确认观感。
 */

/**
 * 屏幕边缘呼吸光（AGSL 距离场）：辉光沿圆角屏缘按距离指数衰减，alpha 按 sin 包络呼吸。
 * 强度映射 alpha 峰值；光带宽度（衰减尺度）、屏缘圆角、衰减硬度是用户旋钮。
 */
internal object EdgeGlowTuning {
    /** alpha 峰值下/上限：亮度旋钮的映射区间（sin 包络的乘数）。 */
    const val PeakAlphaMin = 0.25f
    const val PeakAlphaMax = 0.8f

    /** 外侧衰减（px）：圆角裁掉的区域外辉光快速收尾，避免角外残留硬边。 */
    const val OuterFalloffDp = 12f
}

/**
 * 屏幕边缘流光（AGSL 距离场 + 周长参数化）：亮带沿屏缘环绕，主带后随更宽更暗的拖尾。
 * 深度衰减同呼吸光口径（光带宽度 = 衰减尺度，用户旋钮）；沿周长的带形是固定占比的高斯。
 */
internal object FlowTuning {
    /** 主带高斯 σ（周长占比）：可见带 ≈ ±2σ。 */
    const val CoreBandFraction = 0.03f

    /** 拖尾带 alpha 占主带峰值的比例（拖尾宽度 = 主带 σ × 拖尾旋钮）。 */
    const val TrailAlphaFraction = 0.4f

    /** alpha 峰值下/上限：亮度旋钮的映射区间。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.9f
}

/**
 * 顶部粒子掉落：粒子从顶缘错峰出发，重力加速下落 + 横向正弦摆动，
 * 双层圆点辉光（实心核 + 低 alpha 光晕），alpha 先驻留后衰减。
 * 粒子大小 / 摆幅 / 重力 / 行程是用户旋钮（[ParticlesSpec]），这里只留形状常量。
 */
internal object ParticlesTuning {
    /** alpha 峰值下/上限：亮度旋钮的映射区间。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.8f

    /** 粒子核基准半径下/上限（dp）：大小旋钮的缩放基准。 */
    const val RadiusMinDp = 2f
    const val RadiusMaxDp = 4.5f

    /** 少量大颗粒：占比 × 半径放大倍数，制造层次（其余按均匀随机半径）。 */
    const val HeroFraction = 0.15f
    const val HeroRadiusScale = 1.8f

    /** 光晕层：半径倍数与 alpha 占比（核亮、晕软）。 */
    const val HaloScale = 2.6f
    const val HaloAlphaFraction = 0.35f

    /** alpha 衰减指数：>1 先驻留后衰减，避免一出发就明显变暗。 */
    const val FadeExp = 1.6f

    /** 横摆：基准摆幅（宽度占比，摆幅旋钮的缩放基准）与单次下落内的正弦周期数。 */
    const val SwayAmpNorm = 0.02f
    const val SwayCycles = 1.5f

    /** 随机横向漂移（宽度占比，居中 ± 一半）。 */
    const val DriftNorm = 0.06f
}

/**
 * 球光环扩散（雷达 ping）：圆环减速扩张 + alpha 衰减，核环 + 宽光晕双描边。
 * 扩散行程（球径 %）是用户旋钮；描边宽度 / 每循环环数 / 光晕缩放是用户旋钮（[RingSpec]）。
 */
internal object RingTuning {
    /** 光晕环：宽度倍数与 alpha 占比（光晕缩放旋钮的基准）。 */
    const val HaloStrokeScale = 2.2f
    const val HaloAlphaFraction = 0.3f

    /** 半径缓出指数：1 = 线性，>1 起步快、收尾减速（减速扩张观感）。 */
    const val EaseExp = 2.2f

    /** alpha 峰值下/上限：亮度旋钮的映射区间。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.8f

    /** alpha 衰减指数：>1 先驻留后衰减。 */
    const val FadeExp = 1.3f
}

/**
 * 全屏涟漪：从锚点向外减速扩张抵达屏缘并淡出的水波纹，核环 + 宽光晕双描边。
 * 每循环一道波（圈数 = 波数）；描边宽度 / 锚点是用户旋钮（[RippleSpec]）。
 */
internal object RippleTuning {
    /** 光晕环：宽度倍数与 alpha 占比。 */
    const val HaloStrokeScale = 2.2f
    const val HaloAlphaFraction = 0.3f

    /** 半径缓出指数：减速扩张观感，与光环同族。 */
    const val EaseExp = 2.2f

    /** alpha 峰值下/上限：亮度旋钮的映射区间。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.8f

    /** alpha 衰减指数：>1 先驻留后衰减。 */
    const val FadeExp = 1.3f
}

/**
 * 胶囊本体脉冲：沿胶囊轮廓叠 3 层嵌套描边辉光（核心描边 + 两层逐级加宽减淡的光晕），
 * 亮度按 sin 包络呼吸。辉光宽度 / 光晕强度是用户旋钮（[PulseSpec]）。
 */
internal object PulseTuning {
    /** 光晕层宽度倍数（由内到外）。 */
    const val GlowWidthScale1 = 2.4f
    const val GlowWidthScale2 = 4f

    /** 光晕层 alpha 占峰值增量的比例（由内到外）。 */
    const val GlowAlphaFraction1 = 0.45f
    const val GlowAlphaFraction2 = 0.18f

    /** 峰值增量下/上限：亮度旋钮的映射区间（呼吸顶点的描边亮度增量）。 */
    const val BoostMin = 0.3f
    const val BoostMax = 0.8f
}

/**
 * 胶囊边框流光：一道亮带沿边框自左向右扫过，主带后随一道更宽更暗的拖尾，
 * 扫动位置缓出（收尾减速）。主带宽度 / 拖尾宽度 / 方向是用户旋钮（[ShimmerSpec]）。
 */
internal object ShimmerTuning {
    /** 拖尾带 alpha 占主带峰值的比例。 */
    const val TrailAlphaFraction = 0.4f

    /** 扫动位置缓出指数：1 = 匀速，>1 起步快、收尾减速。 */
    const val SweepEaseExp = 1.35f

    /** 主带 alpha 峰值下/上限：亮度旋钮的映射区间。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.9f
}

/** 球光环的扩散行程（px）：随量旋钮拉远，宿主与效果共用同一份口径定窗口余量。 */
internal fun ringTravelPx(ballPx: Int, amount: Float): Int {
    val min = FloatBallDefaults.AlertRingTravelMin / 100f
    val max = FloatBallDefaults.AlertRingTravelMax / 100f
    val fraction = amount.lerpInRange(min, max)
    return (ballPx * fraction).roundToInt().coerceAtLeast(0)
}

/** 量 0..1 → 区间 [min, max] 线性映射（各效果共用此小工具，不共用观感量本身）。 */
internal fun Float.lerpInRange(min: Float, max: Float): Float = min + (max - min) * this

/** 量 0..1 → 缓出曲线：k = 1 退化为线性，k > 1 起步快、收尾减速。 */
internal fun Float.easedOut(exp: Float): Float = 1f - (1f - this).pow(exp)
