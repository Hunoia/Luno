package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertStyleId
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.ParticleAlertStyle
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 到达提示动效的**调校参数**（需求确认口径：每类目一个家族，类内各样式的单循环基准
 * 与曲线常量各按观感定值；类内通用旋钮见各类的设置块）。
 *
 * 这里只放「量的区间映射」之外的曲线形状常量（衰减 stop、层占比、缓动指数、辉光比例）
 * 与每样式的单循环基准；用户可调的旋钮区间在 `FloatBallDefaults` 里登记（滑杆约束与
 * 绘制映射同源），随 `AlertPlayPlan` / `AlertLayerPlan` / `AlertEffectSpec` 下发。
 * 所有 dp 值按 dp 语义命名，绘制处 `.dp.toPx()` 换算；改动任一值都应先试播确认观感。
 */

/**
 * 首尾淡入淡出包络占比：整段时长的这个比例内从 0 升到 1、再从 1 落到 0。
 * 所有「整段播完即止」的效果都乘这个包络，避免最后一帧还在全亮就戛然而止。
 */
internal const val AlertEnvelopeShare = 0.10f

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
 * 深度衰减同呼吸光口径；主带宽 / 硬度 / 拖尾浓度 / 贴边深度都是用户旋钮（刃感）。
 */
internal object FlowTuning {
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

    /** 光晕层半径倍数（低 alpha 大圆垫底）。 */
    const val HaloScale = 2.6f

    /** alpha 衰减指数：>1 先驻留后衰减，避免一出发就明显变暗。 */
    const val FadeExp = 1.6f
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
 * 扫动位置缓出（收尾减速）。主带宽度 / 拖尾宽度 / 拖尾浓度 / 方向是用户旋钮（[ShimmerSpec]）。
 */
internal object ShimmerTuning {
    /** 扫动位置缓出指数：1 = 匀速，>1 起步快、收尾减速。 */
    const val SweepEaseExp = 1.35f

    /** 主带 alpha 峰值下/上限：亮度旋钮的映射区间。 */
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.9f

    /** 沿轮廓扫光的分段数：主带 / 拖尾各按小段绘制（沿线渐变，不掉角）。 */
    const val BandSlices = 12
    const val TrailSlices = 8
}

/**
 * 边缘波纹（AGSL + CPU 回退）：波峰数、屏缘圆角、亮度。
 *
 * 波纹的波峰宽度用 `sigma`（周长占比）；CPU 回退沿周长采样，shader 用连续距离场。
 */
internal object EdgeRippleTuning {
    const val PeakAlphaMin = 0.30f
    const val PeakAlphaMax = 0.80f

    /** 波峰宽度（归一化周长）：越小越尖，越大越柔。 */
    const val Sigma = 0.03f

    /** CPU 回退的沿周长采样数（shader 不用）。 */
    const val FallbackSamples = 96
}

/** 屏缘烟花：爆点内粒子数、延迟摊开、缓出与粒径抖动（行程与爆点数是旋钮）。 */
internal object EdgeFireworksTuning {
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.90f
    const val PerBurst = 9
    const val DelaySpread = 0.5f
    const val DelayJitter = 0.1f
    const val AngleJitter = 0.4f
    const val SpeedMin = 0.5f
    const val SpeedMax = 1.1f
    const val RadiusMinScale = 0.7f
    const val RadiusMaxScale = 1.3f
    const val EaseExp = 1.6f
    const val SizeShrink = 0.4f
}

/** 双刃上升：亮刃的分段数、半径随尾迹的收缩、亮度衰减指数。 */
internal object EdgeBladeTuning {
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.90f
    const val Steps = 24
    const val RadiusScale = 0.5f
    const val RadiusFallScale = 0.3f
    const val FadeExp = 2f
}

/** 球心爆发：速度区间、缓出、粒径抖动与收缩。 */
internal object BallBurstTuning {
    const val PeakAlphaMin = 0.35f
    const val PeakAlphaMax = 0.90f
    const val SpeedMin = 0.55f
    const val SpeedMax = 1.15f
    const val RadiusMinScale = 0.7f
    const val RadiusMaxScale = 1.3f
    const val EaseExp = 1.5f
    const val SizeShrink = 0.5f
}

/** 球心光晕：呼吸缩放区间、中心亮到边缘透明的三档 alpha 比例。 */
internal object BallHaloTuning {
    const val PeakAlphaMin = 0.20f
    const val PeakAlphaMax = 0.70f
    const val RadiusMinScale = 0.85f
    const val RadiusBreathScale = 0.3f
    const val AlphaFloor = 0.4f
    const val AlphaBreath = 0.6f
    const val MidAlphaFraction = 0.35f
}

/** 胶囊内部扫光的亮度映射区间与扫动缓出（主带宽度与方向是旋钮）。 */
internal object InnerSweepTuning {
    const val PeakAlphaMin = 0.25f
    const val PeakAlphaMax = 0.70f
    const val SweepEaseExp = 1.2f
}

/** 胶囊呼吸描边的亮度映射区间（描边宽度与呼吸缩放是旋钮）。 */
internal object BreathOutlineTuning {
    const val BoostMin = 0.30f
    const val BoostMax = 0.90f
}

/** 屏缘采样内缩（dp）：边缘效果都贴屏缘画，取一个统一内缩量，避免被裁切。 */
internal object EdgeFrameTuning {
    const val InsetDp = 6f
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

/**
 * 每个样式的**单循环基准时长**（ms，标准档 1.0×）：类内各按观感定值，不共用。
 *
 * 这是「每类一个 `MotionFamily`」之后的唯一时长来源——家族只承载档位倍率，基准在这里。
 * `when` 必须穷尽 [AlertStyleId]：新增样式不登记就编译不过（这是门禁，不是风格）。
 */
internal fun alertStyleBaseMillis(style: AlertStyleId): Int = when (style) {
    // 边缘类
    EdgeAlertStyle.EdgeGlow -> 1400
    EdgeAlertStyle.EdgeFlow -> 1600
    EdgeAlertStyle.EdgeRipple -> 1300
    EdgeAlertStyle.EdgeFireworks -> 1400
    EdgeAlertStyle.EdgeBladeRise -> 1100
    // 粒子类（一波粒子从顶缘落到淡出）
    ParticleAlertStyle.TopRain -> 1000
    ParticleAlertStyle.BottomFountain -> 1100
    ParticleAlertStyle.Meteor -> 900
    ParticleAlertStyle.Confetti -> 1200
    ParticleAlertStyle.Firefly -> 1500
    // 胶囊本体类
    CapsuleAlertStyle.Pulse -> 1000
    CapsuleAlertStyle.Shimmer -> 1150
    CapsuleAlertStyle.BreathOutline -> 1100
    CapsuleAlertStyle.InnerSweep -> 1150
    // 球心·全屏类
    AnchorAlertStyle.Ring -> 1000
    AnchorAlertStyle.Ripple -> 1100
    AnchorAlertStyle.BallBurst -> 900
    AnchorAlertStyle.BallHalo -> 1200
}
