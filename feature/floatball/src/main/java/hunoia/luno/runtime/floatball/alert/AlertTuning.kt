package hunoia.luno.runtime.floatball.alert

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 到达提示动效的**每效果独立调校参数**（需求确认口径：参数不共用，按各效果实际观感定值）。
 *
 * 只放绘制曲线的形状常量与映射区间（强度旋钮 → 各自的观感量）；时长不在这里——
 * 单循环时长按效果走 `MotionFamily.Alert*` 家族（唯一事实源在 ui:design 的 Motion.kt）。
 * 所有 dp 值按 dp 语义命名，绘制处 `.dp.toPx()` 换算；改动任一值都应先试播确认观感。
 */

/**
 * 屏幕边缘呼吸光：四条边各一条向内衰减的双层光带（宽软带 + 窄亮核）。
 * 强度映射光带宽度与 alpha 峰值；节奏 = 呼吸圈数。
 */
internal object EdgeGlowTuning {
    /** 光带总宽下/上限（dp）：强度 0 → 下限，强度 1 → 上限。 */
    const val BandMinDp = 64f
    const val BandMaxDp = 160f

    /** 窄亮核占光带总宽的比例：贴边缘的高亮内层，负责「锐」；宽软带负责「晕」。 */
    const val CoreBandFraction = 0.35f

    /** 宽软带相对峰值 alpha 的亮度比例（<1：晕层让位给核层）。 */
    const val SoftLayerBrightness = 0.55f

    /** alpha 峰值下/上限：sin 包络的乘数。 */
    const val PeakAlphaMin = 0.25f
    const val PeakAlphaMax = 0.8f

    /** 衰减渐变的中途 stop（0..1）：>0.5 表示靠边处更亮、衰减更缓（软边）。 */
    const val FalloffMidStop = 0.55f

    /** 衰减渐变中途 stop 的 alpha：边缘亮 → 中途已衰减 → 内缘透明。 */
    const val FalloffMidBrightness = 0.45f
}

/**
 * 顶部粒子掉落：粒子从顶缘错峰出发，重力加速下落 + 横向正弦摆动，
 * 双层圆点辉光（实心核 + 低 alpha 光晕），alpha 先驻留后衰减。
 */
internal object ParticlesTuning {
    /** 粒子数量下/上限：强度线性映射。 */
    const val CountMin = 20
    const val CountMax = 80

    /** alpha 峰值下/上限：粒子本体亮度乘数。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.8f

    /** 粒子核半径下/上限（dp）。 */
    const val RadiusMinDp = 2f
    const val RadiusMaxDp = 4.5f

    /** 少量大颗粒：占比 × 半径放大倍数，制造层次（其余按均匀随机半径）。 */
    const val HeroFraction = 0.15f
    const val HeroRadiusScale = 1.8f

    /** 光晕层：半径倍数与 alpha 占比（核亮、晕软）。 */
    const val HaloScale = 2.6f
    const val HaloAlphaFraction = 0.35f

    /** 下落重力指数：1 = 匀速，2 = 真重力；>1 起步慢、越落越快。 */
    const val GravityExp = 2f

    /** alpha 衰减指数：>1 先驻留后衰减，避免一出发就明显变暗。 */
    const val FadeExp = 1.6f

    /** 横摆：摆幅（宽度占比）与单次下落内的正弦周期数。 */
    const val SwayAmpNorm = 0.02f
    const val SwayCycles = 1.5f

    /** 随机横向漂移（宽度占比，居中 ± 一半）。 */
    const val DriftNorm = 0.06f
}

/**
 * 球光环扩散（雷达 ping）：圆环减速扩张 + alpha 衰减，核环 + 宽光晕双描边。
 * 强度映射扩散行程、描边宽度与 alpha 峰值；节奏 = 圈数（每循环可发多圈错峰）。
 */
internal object RingTuning {
    /** 扩散行程（球径倍数）下/上限：强度线性映射；宿主按此撑窗口余量。 */
    const val TravelMinFraction = 0.7f
    const val TravelMaxFraction = 1.2f

    /** 核描边宽度下/上限（dp）：强度线性映射。 */
    const val StrokeMinDp = 2.5f
    const val StrokeMaxDp = 3.5f

    /** 光晕环：宽度倍数与 alpha 占比。 */
    const val HaloStrokeScale = 2.2f
    const val HaloAlphaFraction = 0.3f

    /** 每循环发出的环数：环间错峰 = 单循环时长 ÷ 环数。 */
    const val RingsPerCycle = 2

    /** 半径缓出指数：1 = 线性，>1 起步快、收尾减速（减速扩张观感）。 */
    const val EaseExp = 2.2f

    /** alpha 衰减指数：>1 先驻留后衰减，环不至于一出生就明显变暗。 */
    const val FadeExp = 1.3f

    /** alpha 峰值下/上限。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.8f
}

/**
 * 胶囊本体脉冲：沿胶囊轮廓（主题层的 CapsuleShape）叠 3 层嵌套描边辉光
 * （核心描边 + 两层逐级加宽减淡的光晕），亮度按 sin 包络呼吸。
 * 强度映射峰值增量（叠加在常驻描边 alpha 之上）。
 */
internal object PulseTuning {
    /** 核心描边宽度（dp）：与常驻描边同量级；光晕层宽度 = 核心 × 各级倍数。 */
    const val CoreWidthDp = 1.5f

    /** 光晕层宽度倍数（由内到外）。 */
    const val GlowWidthScale1 = 2.4f
    const val GlowWidthScale2 = 4f

    /** 光晕层 alpha 占峰值增量的比例（由内到外）。 */
    const val GlowAlphaFraction1 = 0.45f
    const val GlowAlphaFraction2 = 0.18f

    /** 峰值增量下/上限：呼吸顶点的描边亮度增量。 */
    const val BoostMin = 0.3f
    const val BoostMax = 0.8f
}

/**
 * 胶囊边框流光：一道亮带沿边框自左向右扫过，主带后随一道更宽更暗的拖尾，
 * 扫动位置缓出（收尾减速）。强度映射 alpha 峰值。
 */
internal object ShimmerTuning {
    /** 主带宽度（占胶囊宽比例）。 */
    const val BandWidth = 0.25f

    /** 拖尾带：宽度（占胶囊宽比例）与 alpha 占主带峰值的比例。 */
    const val TrailWidth = 0.5f
    const val TrailAlphaFraction = 0.4f

    /** 扫动位置缓出指数：1 = 匀速，>1 起步快、收尾减速。 */
    const val SweepEaseExp = 1.35f

    /** 主带 alpha 峰值下/上限。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.9f
}

/** 球光环的扩散行程（px）：随强度拉远，宿主与效果共用同一份口径定窗口余量。 */
internal fun ringTravelPx(ballPx: Int, intensity: Float): Int {
    val fraction = intensity.lerpInRange(RingTuning.TravelMinFraction, RingTuning.TravelMaxFraction)
    return (ballPx * fraction).roundToInt().coerceAtLeast(0)
}

/** 强度 0..1 → 区间 [min, max] 线性映射（各效果共用此小工具，不共用观感量本身）。 */
internal fun Float.lerpInRange(min: Float, max: Float): Float = min + (max - min) * this

/** 强度 0..1 → 缓出曲线：k = 1 退化为线性，k > 1 起步快、收尾减速。 */
internal fun Float.easedOut(exp: Float): Float = 1f - (1f - this).pow(exp)
