package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 到达提示动效 v4 的预设 / 素材库模型。
 *
 * 与 v3 的关系：
 * - **预设 = 1–5 个层**：一个预设可以只做单类动画，也可以组合多类（受 [AlertLimits] 约束）；
 * - **层**声明自己的 [AlertKind]（宿主由 kind 推导），不再有「一个预设多宿主变体」；
 * - **设置页只选一个预设**：五槽位删除，槽位概念降级为层类别（[AlertLayerCategory]）；
 * - 内置预设全部是单层素材，组合只由用户拼装（[AlertLibrary.presets]）。
 *
 * 本文件只放可序列化 schema 与约束；内置预设是代码常量，放在功能模块。
 */

/** 层类别：设置页 / 编辑器的分组口径；[AlertKind.Capsule] 与球体同属 [Ball]。 */
@Serializable
@Keep
enum class AlertLayerCategory { Edge, Particle, Ball, Screen, Reveal }

/** 效果宿主：决定绘制面与几何来源。由 [AlertKind] 唯一推导，不单独存储。 */
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

/** 层的类别（揭示单列一类；胶囊与球体同属球体类）。 */
val AlertKind.category: AlertLayerCategory
    get() = when (this) {
        AlertKind.Edge -> AlertLayerCategory.Edge
        AlertKind.Particle -> AlertLayerCategory.Particle
        AlertKind.Ball, AlertKind.Capsule -> AlertLayerCategory.Ball
        AlertKind.Screen -> AlertLayerCategory.Screen
        AlertKind.Reveal -> AlertLayerCategory.Reveal
    }

/** 层的绘制宿主。 */
val AlertKind.host: AlertHost
    get() = when (this) {
        AlertKind.Edge -> AlertHost.Edge
        AlertKind.Particle, AlertKind.Reveal -> AlertHost.Stage
        AlertKind.Ball -> AlertHost.Ball
        AlertKind.Capsule -> AlertHost.Capsule
        AlertKind.Screen -> AlertHost.Screen
    }

/**
 * 组合约束（唯一事实源）。
 *
 * **只数启用层**：禁用的层不占 5 层名额，但一个预设最多存 [MaxStoredLayers] 层，
 * 避免编辑器里无限堆积。胶囊窗一次只能画一层，[MaxCapsuleLayers] 是渲染硬约束。
 */
object AlertLimits {
    const val MaxEnabledLayers = 5
    const val MaxStoredLayers = 10
    const val MaxPerCategory = 2
    const val MaxCapsuleLayers = 1
    const val MaxScreenLayers = 1
    const val MaxRevealLayers = 1

    /** 跨层共享的粒子总预算；按层顺序分配，按 `count × waves` 计（不是每层 600）。 */
    const val ParticleBudget = 600

    /** UI 下限取 50：0 会让效果完全不可见（外部请求仍可用 0..2 的倍率）。 */
    const val IntensityMin = 50
    const val IntensityMax = 200
    const val SpeedMin = 25
    const val SpeedMax = 400

    /** 编辑器层的稳定 id 前缀；内置层用它区分「来自素材」。 */
    const val LayerIdPrefix = "layer_"
}

/** 组合校验结果：编辑器提示与计划构建共用，不各自判一套。 */
enum class AlertLimit {
    EmptyPreset,
    TooManyEnabledLayers,
    TooManyStoredLayers,
    TooManyInCategory,
    TooManyCapsuleLayers,
    TooManyScreenLayers,
    TooManyRevealLayers,
    MissingParams,
}

/**
 * 时序：单循环基准 + 循环次数 + 预设自带的相对速度（100 = 基准）。
 *
 * 全局速度（设置页）在计划构建时再乘一次；这里存的是预设作者的意图。
 */
@Serializable
@Keep
data class AlertTiming(
    val baseMillis: Int = 1000,
    val cycles: Int = 1,
    val speedPercent: Int = 100,
)

/**
 * 颜色引用：调色板 id 优先；[colors] 是逐色 ARGB（1–5 色，自定义预设可直接存色）。
 * 两者都为空 = 跟随预设级调色板 / 「动效颜色」/ 主题主色。
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

/**
 * 预设：名称 + 1–[AlertLimits.MaxEnabledLayers] 个层。
 *
 * 内置预设是代码常量（全部单层、只读）；用户预设存在 [AlertLibrary.presets]，
 * 单层素材与组合共用同一类型，靠 [builtin] 与 [layers] 数量在 UI 上分组。
 *
 * [paletteId] 是组合级调色板：设了就覆盖所有层自己的颜色引用，避免 5 层各带一套色。
 */
@Serializable
@Keep
data class AlertPreset(
    val id: String,
    val name: String,
    /** true = 代码内置（只读）；false = 用户预设（可编辑 / 删除）。 */
    val builtin: Boolean = false,
    val paletteId: String? = null,
    val layers: List<AlertLayer> = emptyList(),
) {
    val enabledLayers: List<AlertLayer> get() = layers.filter { it.enabled }

    val enabledLayerCount: Int get() = layers.count { it.enabled }

    /** 真实可渲染的层（顺序 = 绘制顺序，已套用全部上限）。 */
    fun renderableLayers(): List<AlertLayer> {
        var capsule = 0
        var screen = 0
        var reveal = 0
        val result = mutableListOf<AlertLayer>()
        val perCategory = HashMap<AlertLayerCategory, Int>()
        layers.forEach { layer ->
            if (!layer.enabled) return@forEach
            if (result.size >= AlertLimits.MaxEnabledLayers) return@forEach
            val category = layer.kind.category
            val used = perCategory[category] ?: 0
            if (used >= AlertLimits.MaxPerCategory) return@forEach
            when (layer.kind) {
                AlertKind.Capsule -> {
                    if (capsule >= AlertLimits.MaxCapsuleLayers) return@forEach
                    capsule++
                }
                AlertKind.Screen -> {
                    if (screen >= AlertLimits.MaxScreenLayers) return@forEach
                    screen++
                }
                AlertKind.Reveal -> {
                    if (reveal >= AlertLimits.MaxRevealLayers) return@forEach
                    reveal++
                }
                else -> Unit
            }
            perCategory[category] = used + 1
            result += layer
        }
        return result
    }

    /** 校验组合约束；空列表 = 合法。 */
    fun validate(): List<AlertLimit> {
        val issues = mutableListOf<AlertLimit>()
        if (layers.none { it.enabled }) issues += AlertLimit.EmptyPreset
        if (layers.size > AlertLimits.MaxStoredLayers) issues += AlertLimit.TooManyStoredLayers
        if (enabledLayerCount > AlertLimits.MaxEnabledLayers) {
            issues += AlertLimit.TooManyEnabledLayers
        }
        val enabled = enabledLayers
        val perCategory = enabled.groupingBy { it.kind.category }.eachCount()
        if (perCategory.values.any { it > AlertLimits.MaxPerCategory }) {
            issues += AlertLimit.TooManyInCategory
        }
        if (enabled.count { it.kind == AlertKind.Capsule } > AlertLimits.MaxCapsuleLayers) {
            issues += AlertLimit.TooManyCapsuleLayers
        }
        if (enabled.count { it.kind == AlertKind.Screen } > AlertLimits.MaxScreenLayers) {
            issues += AlertLimit.TooManyScreenLayers
        }
        if (enabled.count { it.kind == AlertKind.Reveal } > AlertLimits.MaxRevealLayers) {
            issues += AlertLimit.TooManyRevealLayers
        }
        if (enabled.any { !it.matchesKind() }) issues += AlertLimit.MissingParams
        return issues
    }

    /**
     * 能否再启用一个 [kind] 的层。
     *
     * 返回 null = 可以；否则返回阻断原因（编辑器禁用「添加」并显示原因）。
     * [addingStored] 为 true 时同时检查总存储层数（新建层）；仅切换启用状态时传 false。
     */
    fun addBlockedBy(kind: AlertKind, addingStored: Boolean = true): AlertLimit? {
        if (addingStored && layers.size >= AlertLimits.MaxStoredLayers) {
            return AlertLimit.TooManyStoredLayers
        }
        val enabled = enabledLayers
        if (enabled.size >= AlertLimits.MaxEnabledLayers) return AlertLimit.TooManyEnabledLayers
        val category = kind.category
        if (enabled.count { it.kind.category == category } >= AlertLimits.MaxPerCategory) {
            return AlertLimit.TooManyInCategory
        }
        return when (kind) {
            AlertKind.Capsule ->
                if (enabled.count { it.kind == AlertKind.Capsule } >= AlertLimits.MaxCapsuleLayers) {
                    AlertLimit.TooManyCapsuleLayers
                } else {
                    null
                }
            AlertKind.Screen ->
                if (enabled.count { it.kind == AlertKind.Screen } >= AlertLimits.MaxScreenLayers) {
                    AlertLimit.TooManyScreenLayers
                } else {
                    null
                }
            AlertKind.Reveal ->
                if (enabled.count { it.kind == AlertKind.Reveal } >= AlertLimits.MaxRevealLayers) {
                    AlertLimit.TooManyRevealLayers
                } else {
                    null
                }
            else -> null
        }
    }
}

/**
 * 一个层：类型 + 参数块 + 时序 + 颜色。
 *
 * [id] 在预设内稳定，渲染层 key 用它（不再用列表序号）；[name] 只用于编辑器列表；
 * [sourcePresetId] 记录快照来源（内置素材 id），仅展示，播放不依赖。
 * 参数块恰好一个非空，见 [matchesKind]。
 */
@Serializable
@Keep
data class AlertLayer(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val kind: AlertKind = AlertKind.Particle,
    val timing: AlertTiming = AlertTiming(),
    val color: AlertColorRef = AlertColorRef(),
    val sourcePresetId: String? = null,
    val particle: ParticleParams? = null,
    val edge: EdgeParams? = null,
    val ball: BallParams? = null,
    val capsule: CapsuleParams? = null,
    val screen: ScreenParams? = null,
    val reveal: RevealParams? = null,
)

/** 校验层自洽：只有 [AlertLayer.kind] 对应的参数块非空（其余必须为空）。 */
fun AlertLayer.matchesKind(): Boolean {
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

/** 粒子形状全集（图片 / 图标 / Emoji / 文字由渲染层决定加载方式）。 */
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

/**
 * 光晕形态：**不再画硬边大圆垫底**（那会把花瓣 / 雪花读成「圆里套形状」）。
 *
 * - [Off]：不画光晕（几何形状的默认值）；
 * - [Soft]：径向渐变柔光，边缘自然衰减；
 * - [Shape]：同形状放大 + 低 alpha（雪花辉光是雪花轮廓）。
 */
@Serializable
@Keep
enum class ParticleHaloStyle { Off, Soft, Shape }

/**
 * 粒子外观：形状、尺寸与抖动、亮度、光晕、拖尾、自转、混色。
 *
 * [sizeDp] 是**外接盒边长**（不是半径）：换形状时视觉大小一致，同一预设不会因为
 * 从圆点换成雪花就大一圈。
 */
@Serializable
@Keep
data class ParticleLook(
    val shape: AlertParticleShape = AlertParticleShape.Dot,
    val sizeDp: Float = 3f,
    val sizeJitter: Float = 0.2f,
    val alpha: Float = 0.8f,
    val haloStyle: ParticleHaloStyle = ParticleHaloStyle.Off,
    val haloAlpha: Float = 0.35f,
    val haloScale: Float = 1.8f,
    val trailDp: Float = 0f,
    val trailAlpha: Float = 0.5f,
    /** 闪烁：每颗粒子用自己的相位明灭（萤火 / 星点）。 */
    val blink: Boolean = false,
    /** 单次寿命内的闪烁周期数。 */
    val blinkCycles: Float = 2f,
    val rotate: Boolean = false,
    val blendAdd: Boolean = false,
    /**
     * 自定义图片的**文件名**（位于 [ImageDirName] 目录下）；空 = 回落圆点。
     * 只存文件名：整机备份换机后 filesDir 路径会变，绝对路径必然失效。
     */
    val customImage: String = "",
    /** 自定义图片是否跟随调色板染色（App 图标始终保留品牌原色）。 */
    val tintImage: Boolean = false,
    /** [AlertParticleShape.Text] / [AlertParticleShape.Emoji] 的内容；空 = 回落圆点。 */
    val text: String = "",
) {
    companion object {
        /** 自定义形状图片目录（`filesDir` 下）：选择页写入、渲染端读取共用。 */
        const val ImageDirName = "floatball_particle_shape"
    }
}

// ---------------------------------------------------------------- 屏缘类

/** 屏缘族的具体形态。 */
@Serializable
@Keep
enum class EdgePattern { Glow, Flow, Ripple, Fireworks, Blade, Comet, Ribbon, Lightning }

/**
 * 屏缘参数：光带宽度 / 圆角 / 衰减硬度是所有形态的公共轴；
 * 双带 / 拖尾只对流光与彗星有效，波峰数只对波纹有效，刃高只对双刃有效，
 * 落点数对烟花与闪电有效。
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

/** 揭示参数：错峰、缩放、揭示方向（字符出现次序与标题/正文先后）；内容由播放请求提供。 */
@Serializable
@Keep
data class RevealParams(
    val pattern: RevealPattern = RevealPattern.Text,
    val staggerPercent: Int = 40,
    val scaleFrom: Float = 0.6f,
    val maskDirection: RevealDirection = RevealDirection.LeftToRight,
)

/**
 * 揭示方向：[LeftToRight] / [RightToLeft] 决定逐字出现次序，
 * [TopToBottom] / [BottomToTop] 决定标题与正文谁先出现。
 */
@Serializable
@Keep
enum class RevealDirection { LeftToRight, RightToLeft, TopToBottom, BottomToTop }
/**
 * 动效素材的专用导出格式（预设 + 调色板）。
 *
 * 与整机备份分开：整机备份是整域覆盖，这里是往素材库里**合并**。
 * 内置预设不进导出；导入的预设一律按用户预设落库（内置只读，不能从外部塞进来）。
 */
@Serializable
@Keep
data class AlertLibraryExport(
    /** 导出格式版本；以后结构再变时用它拒收旧档。 */
    val schemaVersion: Int = AlertLibraryExportVersion,
    val exportedAt: Long = 0L,
    val palettes: List<AlertPalette> = emptyList(),
    val presets: List<AlertPreset> = emptyList(),
)

/** 当前导出格式版本（v4 = 组合层模型）。 */
const val AlertLibraryExportVersion: Int = 4
