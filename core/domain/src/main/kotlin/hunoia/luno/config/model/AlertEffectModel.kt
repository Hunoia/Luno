package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 到达提示动效 v3 的预设 / 素材库模型（尚未接线到设置页与渲染，先落数据层）。
 *
 * 与旧模型的关系：
 * - 预设是**全局**的：一个预设可带 1–2 个宿主变体，跨类目复用只受宿主约束；
 * - 类目退化为设置页分组（槽位），真正决定绘制方式的是 [AlertVariant.host] + [AlertVariant.kind]；
 * - 旧的 18 个样式枚举与每样式设置块在切换那一版删除（`alert_v3` 一次性重置）。
 *
 * 本文件只放可序列化 schema 与常量；内置预设是代码常量，放在功能模块；用户预设落在
 * [AlertLibrary.presets]（`notificationCapsule.alertLibrary`，不随 `alert_v3` 重置）。
 */

/** 效果宿主：决定绘制面与几何来源。 */
@Serializable
@Keep
enum class AlertHost {
    /** 全屏舞台窗（粒子 / 路径 / 揭示 / 屏幕着色共用）。 */
    Stage,

    /** 舞台窗 + 屏缘周长几何。 */
    Edge,

    /** 舞台窗 + 球矩形锚点。 */
    Ball,

    /** 胶囊窗 Compose 层，或外部 rect 的圆角近似。 */
    Capsule,

    /** 舞台窗 + 全屏着色 shader。 */
    Screen,
}

/** 效果类型：决定使用哪个参数块与渲染管线。 */
@Serializable
@Keep
enum class AlertKind { Particle, Edge, Ball, Capsule, Screen, Reveal }

/** 时序：单循环基准、循环次数与速度倍率（100 = 预设基准）。 */
@Serializable
@Keep
data class AlertTiming(
    val baseMillis: Int = 1000,
    val cycles: Int = 1,
    val speedPercent: Int = 100,
)

/**
 * 颜色引用：调色板 id 优先；[colors] 是逐色 ARGB（1–5 色，自定义预设可直接存色）。
 * 两者都为空 = 跟随「动效颜色」/主题主色。
 */
@Serializable
@Keep
data class AlertColorRef(
    val paletteId: String? = null,
    val colors: List<Int> = emptyList(),
)

/** 素材库：调色板 + 用户预设；独立于 `alert` 子树，样式换代不重置。 */
@Serializable
@Keep
data class AlertLibrary(
    val palettes: List<AlertPalette> = emptyList(),
    val presets: List<AlertPreset> = emptyList(),
)

/** 全局预设：名称 + 1–2 个宿主变体。内置预设是代码常量，不进备份。 */
@Serializable
@Keep
data class AlertPreset(
    val id: String,
    val name: String,
    /** true = 代码内置（只读）；false = 用户预设（可编辑 / 删除）。 */
    val builtin: Boolean = false,
    val variants: List<AlertVariant> = emptyList(),
)

/**
 * 预设的宿主变体：同一观感在不同绘制面上的参数化。
 *
 * [kind] 决定使用哪个参数块（[particle] / [edge] / [ball] / [capsule] / [screen] / [reveal]），
 * 其余块应为 null；解析层负责校验。
 */
@Serializable
@Keep
data class AlertVariant(
    val host: AlertHost,
    val kind: AlertKind,
    val timing: AlertTiming = AlertTiming(),
    val color: AlertColorRef = AlertColorRef(),
    val particle: ParticleParams? = null,
    val edge: EdgeParams? = null,
    val ball: BallParams? = null,
    val capsule: CapsuleParams? = null,
    val screen: ScreenParams? = null,
    val reveal: RevealParams? = null,
)

// ---------------------------------------------------------------- 粒子类

/** 粒子类的完整参数：发射器 + 运动方程 + 外观。 */
@Serializable
@Keep
data class ParticleParams(
    val emitter: ParticleEmitter = ParticleEmitter(),
    val motion: ParticleMotion = ParticleMotion(),
    val look: ParticleLook = ParticleLook(),
)

/** 发射区域：决定粒子从哪里产生。 */
@Serializable
@Keep
enum class ParticleArea { TopEdge, BottomEdge, ScreenRect, EdgePerimeter, Point, BallRing }

/**
 * 发射器：数量、方向/散开、初速与错峰。
 *
 * [directionDeg] 以屏幕坐标系为准（90 = 向下，270 = 向上）；
 * [speed] 是屏高/秒的归一化初速，[speedJitter] 是 ± 比例。
 */
@Serializable
@Keep
data class ParticleEmitter(
    val area: ParticleArea = ParticleArea.TopEdge,
    val count: Int = 60,
    val waves: Int = 1,
    val directionDeg: Float = 90f,
    val spreadDeg: Float = 30f,
    val speed: Float = 0.6f,
    val speedJitter: Float = 0.2f,
    val delaySpread: Float = 0.6f,
    val lifeShare: Float = 0.65f,
)

/** 运动路径族的预置形状（需要更多控制时由 motion 参数叠加）。 */
@Serializable
@Keep
enum class ParticlePath { Bezier, Arc, Sine, ZigZag }

/**
 * 运动方程族（组合式）：所有参数都归一化，渲染层按屏幕尺寸换算。
 *
 * - [gravity] 0 = 无重力，1 = 真重力，2–3 更强；
 * - [drag] 0..1（终端速度曲线）；[wind] -1..1（负左正右）；
 * - [turbulence] / [brownian] 0..1（curl 噪声 / 随机游走强度）；
 * - [orbit] / [spiral] 0..1（绕锚点环行 / 向内卷吸）；[attract] 0..1；
 * - [sway] 振幅（屏宽比例）、[swayCycles] 单次寿命内的正弦周期；
 * - [spin] / [tilt] 0..1（自转 / 3D 翻面幅度）；[bounce] 0..1（边界反弹）。
 */
@Serializable
@Keep
data class ParticleMotion(
    val gravity: Float = 1f,
    val drag: Float = 0.3f,
    val wind: Float = 0f,
    val turbulence: Float = 0f,
    val brownian: Float = 0f,
    val orbit: Float = 0f,
    val spiral: Float = 0f,
    val sway: Float = 0.2f,
    val swayCycles: Float = 1.5f,
    val spin: Float = 0f,
    val tilt: Float = 0f,
    val path: ParticlePath? = null,
    val attract: Float = 0f,
    val bounce: Float = 0f,
)

/** 粒子形状（v3 全集；图片 / 图标 / Emoji / 文字由渲染层决定加载方式）。 */
@Serializable
@Keep
enum class AlertParticleShape {
    Dot,
    Square,
    Triangle,
    Star,
    Hexagon,
    Ring,
    Hollow,
    Line,
    Heart,
    Petal,
    Snowflake,
    Leaf,
    Emoji,
    Text,
    CustomImage,
    AppIcon,
}

/** 外观：形状、大小与抖动、亮度、光晕、拖尾、自转、混色。 */
@Serializable
@Keep
data class ParticleLook(
    val shape: AlertParticleShape = AlertParticleShape.Dot,
    val sizeDp: Float = 3f,
    val sizeJitter: Float = 0.2f,
    val alpha: Float = 0.8f,
    val halo: Float = 0.35f,
    val trailDp: Float = 0f,
    val trailAlpha: Float = 0.5f,
    val rotate: Boolean = false,
    val blendAdd: Boolean = false,
)

// ---------------------------------------------------------------- 屏缘类

/** 屏缘族的具体形态。 */
@Serializable
@Keep
enum class EdgePattern { Glow, Flow, Ripple, Fireworks, Blade, Comet, Ribbon, Lightning }

/**
 * 屏缘参数：光带宽度 / 圆角 / 衰减硬度是所有形态的公共轴；
 * 双带 / 拖尾只对流光有效，波峰数只对波纹有效，刃宽刃高只对双刃有效。
 */
@Serializable
@Keep
data class EdgeParams(
    val pattern: EdgePattern = EdgePattern.Glow,
    val bandWidthDp: Float = 12f,
    val cornerRadiusDp: Int = 24,
    val hardness: Float = 2f,
    val dualBand: Boolean = false,
    val trailScale: Float = 2f,
    val trailAlpha: Float = 0.4f,
    val crests: Int = 3,
    val bladeHeightDp: Int = 120,
    val strikeCount: Int = 1,
)

// ---------------------------------------------------------------- 球体类

/** 球 / 胶囊锚定的形态。 */
@Serializable
@Keep
enum class BallPattern { Ring, Ripple, Burst, Halo, Shockwave, Spiral, Orbit3D }

/** 球体参数：行程 / 描边 / 每循环环数 / 光晕强度；[centerAtBall] 只对涟漪有效。 */
@Serializable
@Keep
data class BallParams(
    val pattern: BallPattern = BallPattern.Ring,
    val travelPercent: Int = 95,
    val strokeWidthDp: Float = 3f,
    val ringsPerCycle: Int = 1,
    val haloScale: Float = 1f,
    val centerAtBall: Boolean = true,
    val particleCount: Int = 18,
    val particleSizeDp: Float = 4f,
)

// ---------------------------------------------------------------- 胶囊类

/** 胶囊本体形态。 */
@Serializable
@Keep
enum class CapsulePattern { Pulse, Shimmer, BreathOutline, InnerSweep, Flip3D, DualChase }

/** 胶囊参数：描边宽度 / 主带宽度 / 拖尾 / 扫动方向 / 翻转幅度。 */
@Serializable
@Keep
data class CapsuleParams(
    val pattern: CapsulePattern = CapsulePattern.Pulse,
    val strokeWidthDp: Float = 1.5f,
    val bandWidthPercent: Int = 25,
    val trailScale: Float = 2f,
    val trailAlpha: Float = 0.4f,
    val reverse: Boolean = false,
    val flipDegrees: Int = 180,
)

// ---------------------------------------------------------------- 全屏氛围 / 揭示

/** 全屏着色形态。 */
@Serializable
@Keep
enum class ScreenPattern { Flash, Vignette, ZoomPulse }

/** 屏幕着色参数：强度 / 缩放幅度 / 颜色；[r,g,b,a] 跟随颜色引用时不填。 */
@Serializable
@Keep
data class ScreenParams(
    val pattern: ScreenPattern = ScreenPattern.Flash,
    val amplitude: Float = 0.6f,
    val zoomPercent: Int = 8,
    val vignetteSoftness: Float = 0.5f,
)

/** 揭示形态：文字逐字 / 图标逐元素。 */
@Serializable
@Keep
enum class RevealPattern { Text, Icon }

/** 揭示参数：错峰、缩放、遮罩方向；内容由播放请求的 payload 提供。 */
@Serializable
@Keep
data class RevealParams(
    val pattern: RevealPattern = RevealPattern.Text,
    val staggerPercent: Int = 40,
    val scaleFrom: Float = 0.6f,
    val maskDirection: RevealDirection = RevealDirection.LeftToRight,
)

/** 揭示遮罩方向。 */
@Serializable
@Keep
enum class RevealDirection { LeftToRight, RightToLeft, TopToBottom, BottomToTop }
/** 校验变体自洽：只有 [AlertVariant.kind] 对应的参数块非空（其余必须为空）。 */
fun AlertVariant.matchesKind(): Boolean {
    val blocks = listOfNotNull(
        particle?.let { AlertKind.Particle },
        edge?.let { AlertKind.Edge },
        ball?.let { AlertKind.Ball },
        capsule?.let { AlertKind.Capsule },
        screen?.let { AlertKind.Screen },
        reveal?.let { AlertKind.Reveal },
    )
    return blocks.size == 1 && blocks.single() == kind
}
