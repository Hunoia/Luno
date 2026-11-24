package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 到达提示动效 v6 的预设 / 素材库模型。
 *
 * v5 的 10 个渲染器族收敛为 **7 个**：同一宿主、且一次只能画一层的形态合并成
 * 一个族，族内用 `shape` 单选项切换形态；族与族之间才是「层」。
 *
 * - 屏缘（[AlertFamily.Edge]）：呼吸 / 滚动 / 波纹 / 雷电，四选一；
 * - 胶囊（[AlertFamily.Capsule]）：扫光 / 呼吸 / 翻转，三选一；
 * - 其余：锚点环 / 全屏着色 / 全屏覆盖 / 粒子 / 揭示。
 *
 * 参数分三层，**参数只在用它的那一层出现**：
 * - 通用（[AlertLayer]）：启用 / 类别 / 透明度 / 配色 / 时序，所有族同一套；
 * - 族级：本族所有形态真正共用的少量旋钮（如屏缘的后端与圆角）；
 * - 形态级：每个形态一份独立 data class，切走保留、切回原样。
 *
 * 形态切换只改参数，不新增绘制器；族群与形态都不存在「调这个那个失效」的
 * 共享字段——互斥参数被拆成了各形态自己的字段或可空子对象。
 */

/** 层类别：设置页 / 编辑器的分组口径；胶囊与球体同属 [Ball]。 */
@Serializable
@Keep
enum class AlertLayerCategory { Edge, Particle, Ball, Screen, Reveal }

/** 效果宿主：决定绘制面与几何来源。由 [AlertFamily] 唯一推导，不单独存储。 */
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

/**
 * 渲染器族（= 唯一选择绘制管线的单位）。
 *
 * - [Edge]：屏缘光带。`EdgeParams.shape` 在呼吸 / 滚动 / 波纹 / 雷电之间切换；
 *   四种形态共用圆角矩形周长几何与调色板，但同一时刻只画一种，不叠加。
 * - [Ring]：锚点扩张描边环（嵌套 / 三描边 / 光晕）。
 * - [Capsule]：胶囊轮廓。`CapsuleParams.shape` 在扫光 / 呼吸 / 翻转之间切换。
 * - [Wash]：全屏着色（平铺 / 径向 / 环形）。
 * - [Cover]：全屏覆盖（圆 / 圆瞳 / 亮缝 / 百叶 / 碎片）。
 * - [Particles]：统一粒子引擎（雨 / 喷泉 / 流星 / 彩带 / 萤火 / 烟花 / 球心爆）。
 * - [Reveal]：文字 / 图标揭示。
 */
@Serializable
@Keep
enum class AlertFamily {
    Edge,
    Ring,
    Capsule,
    Wash,
    Cover,
    Particles,
    Reveal,
    ;

    /** 胶囊窗一次只能画一层。 */
    val isCapsuleHost: Boolean get() = this == Capsule
}

/**
 * 预设自带的播放冲突策略：已有动效在播时如何处理本次播放。
 *
 * 与外部 API 的 `AlertConcurrency` 同口径（Reject / Replace / Queue 三种），
 * 但定义在配置层：预设是可持久化数据，对外契约不反向依赖配置模型。
 */
@Serializable
@Keep
enum class AlertPlaybackPolicy {
    /** 跳过本次（默认）：不排队、不打断。 */
    Skip,

    /** 先停掉当前动效，再播本次。 */
    Replace,

    /** 排队等当前播完（播放端上限 1 条待播，满了丢弃）。 */
    Queue,
}

/** 预设分类：单层素材 / 组合。编辑器与素材库按它分区，不再靠层数推断。 */
@Serializable
@Keep
enum class AlertPresetKind { Single, Combo }

/** 层的类别（揭示单列一类；胶囊与球体同属球体类）。 */
val AlertFamily.category: AlertLayerCategory
    get() = when (this) {
        AlertFamily.Edge -> AlertLayerCategory.Edge
        AlertFamily.Particles -> AlertLayerCategory.Particle
        AlertFamily.Ring, AlertFamily.Capsule -> AlertLayerCategory.Ball
        AlertFamily.Wash, AlertFamily.Cover -> AlertLayerCategory.Screen
        AlertFamily.Reveal -> AlertLayerCategory.Reveal
    }

/** 层的绘制宿主。 */
val AlertFamily.host: AlertHost
    get() = when (this) {
        AlertFamily.Edge -> AlertHost.Edge
        AlertFamily.Ring -> AlertHost.Ball
        AlertFamily.Capsule -> AlertHost.Capsule
        AlertFamily.Wash, AlertFamily.Cover -> AlertHost.Screen
        AlertFamily.Particles, AlertFamily.Reveal -> AlertHost.Stage
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
    const val MaxPerCategory = 3
    const val MaxCapsuleLayers = 1
    const val MaxScreenLayers = 1
    const val MaxRevealLayers = 1

    /** 跨层共享的粒子总预算；按层顺序分配，按 `count × cycles × anchors` 计（不是每层 600）。 */
    const val ParticleBudget = 600

    /** 层延迟上限（ms）：通用时序旋钮，避免透明窗挂太久。 */
    const val MaxDelayMillis = 3000

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
 * 时序：单循环基准 + 循环次数 + 预设自带的相对速度（100 = 基准）+ 层延迟。
 *
 * 全局速度（设置页）在计划构建时再乘一次；这里存的是预设作者的意图。
 * [delayMillis] 是相对播放起点的偏移：组合里靠它做先后节奏，所有族通用。
 */
@Serializable
@Keep
data class AlertTiming(
    val baseMillis: Int = 1000,
    val cycles: Int = 1,
    val speedPercent: Int = 100,
    val delayMillis: Int = 0,
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
 * v6 起只有用户预设（内置素材删除）：[kind] 显式区分单层素材与组合，
 * 播放时也可由调用端随机挑一个（见设置里的「随机预设」）。
 *
 * [paletteId] 是组合级调色板：设了就覆盖所有层自己的颜色引用，避免 5 层各带一套色。
 */
@Serializable
@Keep
data class AlertPreset(
    val id: String,
    val name: String,
    val kind: AlertPresetKind = AlertPresetKind.Single,
    val paletteId: String? = null,
    val layers: List<AlertLayer> = emptyList(),
    /**
     * 播放冲突策略：有动效在播时怎么办。
     *
     * 策略归预设所有、调用方不判（到达提示 / 动作动效 / 试播三条路径共用）。
     */
    val playbackPolicy: AlertPlaybackPolicy = AlertPlaybackPolicy.Skip,
) {
    val enabledLayers: List<AlertLayer> get() = layers.filter { it.enabled }

    val enabledLayerCount: Int get() = layers.count { it.enabled }

    /** 实际分类：单层预设若被加了第二层，按组合算（避免编辑器忘了翻 kind）。 */
    val resolvedKind: AlertPresetKind
        get() = if (kind == AlertPresetKind.Combo || layers.size > 1) {
            AlertPresetKind.Combo
        } else {
            AlertPresetKind.Single
        }

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
            val category = layer.family.category
            val used = perCategory[category] ?: 0
            if (used >= AlertLimits.MaxPerCategory) return@forEach
            when (layer.family.host) {
                AlertHost.Capsule -> {
                    if (capsule >= AlertLimits.MaxCapsuleLayers) return@forEach
                    capsule++
                }
                AlertHost.Screen -> {
                    if (screen >= AlertLimits.MaxScreenLayers) return@forEach
                    screen++
                }
                else -> Unit
            }
            if (layer.family == AlertFamily.Reveal) {
                if (reveal >= AlertLimits.MaxRevealLayers) return@forEach
                reveal++
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
        val perCategory = enabled.groupingBy { it.family.category }.eachCount()
        if (perCategory.values.any { it > AlertLimits.MaxPerCategory }) {
            issues += AlertLimit.TooManyInCategory
        }
        if (enabled.count { it.family.host == AlertHost.Capsule } > AlertLimits.MaxCapsuleLayers) {
            issues += AlertLimit.TooManyCapsuleLayers
        }
        if (enabled.count { it.family.host == AlertHost.Screen } > AlertLimits.MaxScreenLayers) {
            issues += AlertLimit.TooManyScreenLayers
        }
        if (enabled.count { it.family == AlertFamily.Reveal } > AlertLimits.MaxRevealLayers) {
            issues += AlertLimit.TooManyRevealLayers
        }
        if (enabled.any { !it.matchesFamily() }) issues += AlertLimit.MissingParams
        return issues
    }

    /**
     * 能否再启用一个 [family] 的层。
     *
     * 返回 null = 可以；否则返回阻断原因（编辑器禁用「添加」并显示原因）。
     * [addingStored] 为 true 时同时检查总存储层数（新建层）；仅切换启用状态时传 false。
     */
    fun addBlockedBy(family: AlertFamily, addingStored: Boolean = true): AlertLimit? {
        if (addingStored && layers.size >= AlertLimits.MaxStoredLayers) {
            return AlertLimit.TooManyStoredLayers
        }
        val enabled = enabledLayers
        if (enabled.size >= AlertLimits.MaxEnabledLayers) return AlertLimit.TooManyEnabledLayers
        val category = family.category
        if (enabled.count { it.family.category == category } >= AlertLimits.MaxPerCategory) {
            return AlertLimit.TooManyInCategory
        }
        return when (family.host) {
            AlertHost.Capsule ->
                if (enabled.count { it.family.host == AlertHost.Capsule } >=
                    AlertLimits.MaxCapsuleLayers
                ) {
                    AlertLimit.TooManyCapsuleLayers
                } else {
                    null
                }
            AlertHost.Screen ->
                if (enabled.count { it.family.host == AlertHost.Screen } >=
                    AlertLimits.MaxScreenLayers
                ) {
                    AlertLimit.TooManyScreenLayers
                } else {
                    null
                }
            else ->
                if (family == AlertFamily.Reveal &&
                    enabled.count { it.family == AlertFamily.Reveal } >=
                    AlertLimits.MaxRevealLayers
                ) {
                    AlertLimit.TooManyRevealLayers
                } else {
                    null
                }
        }
    }
}

/**
 * 一个层：族 + 参数块 + 时序 + 颜色。
 *
 * [id] 在预设内稳定，渲染层 key 用它（不再用列表序号）；[name] 只用于编辑器列表；
 * [sourcePresetId] 记录快照来源（旧的素材 id），仅展示，播放不依赖。
 * 参数块恰好一个非空，见 [matchesFamily]；[opacity] 是所有族通用的存在感旋钮。
 */
@Serializable
@Keep
data class AlertLayer(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val family: AlertFamily = AlertFamily.Particles,
    val timing: AlertTiming = AlertTiming(),
    val color: AlertColorRef = AlertColorRef(),
    val opacity: Float = 1f,
    val sourcePresetId: String? = null,
    val edge: EdgeParams? = null,
    val ring: RingParams? = null,
    val capsule: CapsuleParams? = null,
    val wash: WashParams? = null,
    val cover: CoverParams? = null,
    val particle: ParticleParams? = null,
    val reveal: RevealParams? = null,
) {
    /** 该层当前使用的参数块（不需要知道族就能遍历 / 复制）。 */
    fun hasParams(): Boolean = paramsPresent().size == 1

    private fun paramsPresent(): List<AlertFamily> = listOfNotNull(
        edge?.let { AlertFamily.Edge },
        ring?.let { AlertFamily.Ring },
        capsule?.let { AlertFamily.Capsule },
        wash?.let { AlertFamily.Wash },
        cover?.let { AlertFamily.Cover },
        particle?.let { AlertFamily.Particles },
        reveal?.let { AlertFamily.Reveal },
    )
}

/** 校验层自洽：只有 [AlertLayer.family] 对应的参数块非空（其余必须为空）。 */
fun AlertLayer.matchesFamily(): Boolean {
    val blocks = listOfNotNull(
        edge?.let { AlertFamily.Edge },
        ring?.let { AlertFamily.Ring },
        capsule?.let { AlertFamily.Capsule },
        wash?.let { AlertFamily.Wash },
        cover?.let { AlertFamily.Cover },
        particle?.let { AlertFamily.Particles },
        reveal?.let { AlertFamily.Reveal },
    )
    return blocks.size == 1 && blocks.single() == family
}

// ---------------------------------------------------------------- 屏缘

/** 光带路径：圆角矩形周长 / 左右两条竖线 / 上下两条横线。 */
@Serializable
@Keep
enum class BandPath { Perimeter, Vertical, Horizontal }

/**
 * 绘制后端：AGSL 着色器优先（平滑距离场），CPU 分段描边兜底，也可手动指定。
 *
 * [Auto] = 支持的几何走着色器、其余走 CPU；[Shader] = 强制着色器（不支持的几何仍回退）；
 * [Cpu] = 强制 CPU（对比观感 / 规避个别设备的着色器问题）。
 */
@Serializable
@Keep
enum class AlertRenderBackend { Auto, Shader, Cpu }

/**
 * 屏缘形态（单选项，决定当前画哪一种，不叠加）：
 *
 * - [Breathing]：整圈呼吸光，不移动；
 * - [Sweep]：沿路径扫动的光带，头 + 拖尾；
 * - [Wave]：等距波峰沿周长起伏；
 * - [Lightning]：多道独立落点的折线放电。
 */
@Serializable
@Keep
enum class EdgeShape { Breathing, Sweep, Wave, Lightning }

/**
 * 屏缘族参数：族级只有「绘制后端 + 圆角」真正四形态共用；
 * 每个形态一份独立参数块，切换形态不串值，也不存在共享字段的失效问题。
 */
@Serializable
@Keep
data class EdgeParams(
    val shape: EdgeShape = EdgeShape.Sweep,
    val backend: AlertRenderBackend = AlertRenderBackend.Auto,
    val cornerRadiusDp: Int = 24,
    val breathing: EdgeBreathing = EdgeBreathing(),
    val sweep: EdgeSweep = EdgeSweep(),
    val wave: EdgeWave = EdgeWave(),
    val lightning: EdgeLightning = EdgeLightning(),
)

/** 呼吸：整圈亮度脉冲，不移动。 */
@Serializable
@Keep
data class EdgeBreathing(
    /** 发光深度（dp）。 */
    val depthDp: Float = 14f,
    /** 边缘硬度（1 = 柔，4 = 锐）。 */
    val hardness: Float = 2f,
    /** 呼吸幅度：0 = 常亮，1 = 从全灭到全亮。 */
    val breathAmount: Float = 1f,
)

/** 滚动光带：头 + 单侧拖尾沿路径移动。 */
@Serializable
@Keep
data class EdgeSweep(
    val path: BandPath = BandPath.Perimeter,
    /** 每循环走完整条路径的比例（0 = 不动，1 = 走一圈）。 */
    val travel: Float = 1f,
    /** 光带宽度（dp）。 */
    val depthDp: Float = 14f,
    val hardness: Float = 2f,
    /** 头部沿路径方向的宽度（dp）。 */
    val headWidthDp: Float = 12f,
    /** 拖尾占整条路径的百分比（0–60，单侧弧长，超出即截断）。 */
    val tailPercent: Int = 15,
    val tailFalloff: Float = 2f,
    val tailAlpha: Float = 0.5f,
    val headBoost: Float = 0.5f,
    /** 0 = 平滑带，1 = 折线抖动。 */
    val noise: Float = 0f,
    val dual: Boolean = false,
    val reverse: Boolean = false,
)

/** 波纹：等距波峰沿周长起伏。 */
@Serializable
@Keep
data class EdgeWave(
    val crests: Int = 3,
    /** 单个波峰沿周长方向的宽度（dp）。 */
    val waveWidthDp: Float = 10f,
    /** 每循环行进的比例（0 = 原地起伏）。 */
    val travel: Float = 1f,
    val depthDp: Float = 14f,
    val hardness: Float = 2f,
)

/** 雷电：闪断次数与每次道数分开，落点每循环重掷。 */
@Serializable
@Keep
data class EdgeLightning(
    /** 每循环闪断几次。 */
    val flashes: Int = 3,
    /** 每次闪断画几道独立折线。 */
    val bolts: Int = 1,
    /** 单次闪断的可见窗口（占单循环比例）。 */
    val window: Float = 0.34f,
    val widthDp: Float = 1.5f,
    /** 向屏幕内延伸的行程（屏高百分比）。 */
    val reachPercent: Int = 38,
)

// ---------------------------------------------------------------- 锚点环

/** 环的描边层次：单环 + 光晕 / 三层嵌套描边 / 纯光晕。 */
@Serializable
@Keep
enum class RingStyle { Nested, ThreeStroke, Halo }

/**
 * 锚点环：三种描边层次共用「环数 / 行程 / 描边宽度 / 锚点」，
 * 每层需要的额外参数各自一份。
 */
@Serializable
@Keep
data class RingParams(
    val style: RingStyle = RingStyle.Nested,
    /** true = 围绕锚点；false = 屏幕中心。 */
    val centerAtAnchor: Boolean = true,
    val strokeWidthDp: Float = 3f,
    /** 每循环环数。 */
    val ringsPerCycle: Int = 1,
    /** 扩张行程（球径 %）；0 = 原地呼吸光晕。 */
    val travelPercent: Int = 95,
    val nested: RingNested = RingNested(),
    val halo: RingHalo = RingHalo(),
)

/** 嵌套描边：主环 + 外圈柔光。 */
@Serializable
@Keep
data class RingNested(val haloScale: Float = 1f)

/** 纯光晕：扩散半径倍数。 */
@Serializable
@Keep
data class RingHalo(val haloScale: Float = 1.4f)

// ---------------------------------------------------------------- 胶囊

/**
 * 胶囊形态（单选项）：
 *
 * - [Sweep]：轮廓扫光（描边 / 行内填充，单头 / 双头）；
 * - [Pulse]：描边呼吸（辉光层数 + 呼吸缩放）；
 * - [Flip]：3D 翻转。
 */
@Serializable
@Keep
enum class CapsuleShape { Sweep, Pulse, Flip }

/**
 * 胶囊族参数：三种形态各自一份，切换不串值。
 */
@Serializable
@Keep
data class CapsuleParams(
    val shape: CapsuleShape = CapsuleShape.Sweep,
    val sweep: CapsuleSweep = CapsuleSweep(),
    val pulse: CapsulePulse = CapsulePulse(),
    val flip: CapsuleFlip = CapsuleFlip(),
)

/** 扫光：沿轮廓或行内填充。 */
@Serializable
@Keep
data class CapsuleSweep(
    val strokeWidthDp: Float = 1.5f,
    /** true = 行内填充，false = 沿轮廓。 */
    val fill: Boolean = false,
    val heads: Int = 1,
    /** 主带宽度（胶囊宽占比，5–90）。 */
    val bandPercent: Int = 25,
    /** 每循环扫过的比例（0 = 停住，1 = 扫一圈）。 */
    val travel: Float = 1f,
    val trailScale: Float = 2f,
    val trailAlpha: Float = 0.4f,
    val reverse: Boolean = false,
)

/** 描边呼吸：辉光层数 + 呼吸缩放。 */
@Serializable
@Keep
data class CapsulePulse(
    val strokeWidthDp: Float = 1.5f,
    /** 辉光层数（0–2）。 */
    val glowLayers: Int = 2,
    val glowScale: Float = 1f,
    val breatheScale: Float = 1f,
)

/** 3D 翻转。 */
@Serializable
@Keep
data class CapsuleFlip(
    val strokeWidthDp: Float = 1.5f,
    /** 0 = 不翻；90–360 = 单循环翻转角度。 */
    val degrees: Int = 180,
)

// ---------------------------------------------------------------- 全屏

/** 全屏着色形状：平铺 / 径向暗角 / 向外扩散的环。 */
@Serializable
@Keep
enum class WashShape { Flat, Radial, Ring }

/**
 * 全屏着色：三种造型共用「强度 / 是否呼吸」，各自的几何参数分开。
 */
@Serializable
@Keep
data class WashParams(
    val shape: WashShape = WashShape.Flat,
    /** 峰值不透明度。 */
    val amplitude: Float = 0.6f,
    /** true = 正弦呼吸，false = 单次淡出。 */
    val breath: Boolean = true,
    val radial: WashRadial = WashRadial(),
    val ring: WashRing = WashRing(),
)

/** 径向暗角：羽化。 */
@Serializable
@Keep
data class WashRadial(val softness: Float = 0.5f)

/** 环形脉冲：扩散缩放。 */
@Serializable
@Keep
data class WashRing(val zoomPercent: Int = 8)

/** 全屏覆盖的遮罩形状：圆盘 / 圆瞳 / 中间亮缝 / 百叶 / 碎片。 */
@Serializable
@Keep
enum class CoverMask { Circle, Iris, Slit, Bands, Shards }

/**
 * 全屏覆盖：五种遮罩共用时序与曲线，几何参数各自一份。
 */
@Serializable
@Keep
data class CoverParams(
    val mask: CoverMask = CoverMask.Circle,
    /** 最终不透明度（关闭屏幕一般 1）。 */
    val amplitude: Float = 1f,
    /** 覆盖进度曲线指数（越大越后段发力）。 */
    val curve: Float = 2f,
    val circle: CoverCircle = CoverCircle(),
    val iris: CoverIris = CoverIris(),
    val slit: CoverSlit = CoverSlit(),
    val bands: CoverBands = CoverBands(),
    val shards: CoverShards = CoverShards(),
)

/** 圆盘：边缘羽化。 */
@Serializable
@Keep
data class CoverCircle(val softness: Float = 0.5f)

/** 圆瞳：边缘描边宽度。 */
@Serializable
@Keep
data class CoverIris(val rimDp: Float = 2f)

/** 电视关闭：最大亮缝厚度。 */
@Serializable
@Keep
data class CoverSlit(val slitDp: Float = 10f)

/** 百叶：条数。 */
@Serializable
@Keep
data class CoverBands(val count: Int = 7)

/** 碎片：片数与羽化。 */
@Serializable
@Keep
data class CoverShards(val count: Int = 16, val softness: Float = 0.5f)

// ---------------------------------------------------------------- 粒子

/** 粒子类的完整参数：发射器 + 锚点簇 + 运动方程 + 外观。 */
@Serializable
@Keep
data class ParticleParams(
    val emitter: ParticleEmitter = ParticleEmitter(),
    /** 锚点簇：null = 每颗粒子各取一点、原地爆（雨 / 喷泉 / 萤火）。 */
    val cluster: ParticleCluster? = null,
    val motion: ParticleMotion = ParticleMotion(),
    val look: ParticleLook = ParticleLook(),
)

/** 发射区域：决定粒子从哪里产生。 */
@Serializable
@Keep
enum class ParticleArea { TopEdge, BottomEdge, ScreenRect, EdgePerimeter, Point, BallRing }

/**
 * 发射方向：
 *
 * - [Fixed]：按 [ParticleEmitter.directionDeg]；
 * - [Inward]：从出生点指向屏幕中心（屏缘烟花靠它往屏幕内炸）；
 * - [Outward]：背离屏幕中心；
 * - [Radial]：360° 均匀（升空烟花 / 球心爆发），不使用方向角。
 */
@Serializable
@Keep
enum class ParticleDirection { Fixed, Inward, Outward, Radial }

/** 单循环内的发射方式。 */
@Serializable
@Keep
enum class ParticleEmission {
    /** 爆发：一拍发出（烟花 / 球心爆）。 */
    Burst,

    /** 连续：铺满整段循环，短寿命首尾相接（雨 / 雪 / 星尘）。 */
    Stream,
}

/**
 * 发射器：数量、方向/散开、初速与错峰。所有字段对每种发射方式都有效。
 *
 * [delaySpread]：Burst 是「单循环减寿命」的比例（0 = 同刻出发，1 = 铺满）；
 * Stream 是整段循环的比例（1 = 整段均匀发射）。
 */
@Serializable
@Keep
data class ParticleEmitter(
    val area: ParticleArea = ParticleArea.TopEdge,
    val count: Int = 60,
    val emission: ParticleEmission = ParticleEmission.Burst,
    val direction: ParticleDirection = ParticleDirection.Fixed,
    /** 方向角（屏幕系，90 = 向下）；仅 [ParticleDirection.Fixed] 使用。 */
    val directionDeg: Float = 90f,
    /** 散开角（度）；[ParticleDirection.Radial] 固定 360°，不使用。 */
    val spreadDeg: Float = 30f,
    val speed: Float = 0.6f,
    val speedJitter: Float = 0.2f,
    val delaySpread: Float = 0.6f,
    val lifeShare: Float = 0.65f,
)

/**
 * 锚点簇：一次波次共用一个随机锚点（而不是每颗各取一点）。
 *
 * [anchors] 是每次波次同时用几个锚点，每个锚点各发射 [count] 颗；
 * [launchHeight] > 0 时先升空到顶点再爆（真烟花）。
 */
@Serializable
@Keep
data class ParticleCluster(
    /** 每次波次的锚点数（1–8）。 */
    val anchors: Int = 1,
    /** 0 = 原地爆；>0 = 先向上飞这段屏高再爆。 */
    val launchHeight: Float = 0f,
    /** 升空占单次波次的比例（0.1–0.9）。 */
    val launchShare: Float = 0.45f,
)

/** 运动方程族：自由积分 / 预置路径。 */
@Serializable
@Keep
enum class ParticleMotionMode { Free, Path }

/** 运动路径族的预置形状。 */
@Serializable
@Keep
enum class ParticlePath { Bezier, Arc, Sine, ZigZag }

/**
 * 运动：自转 / 3D 翻面两种模式都生效；其余按 [mode] 各取一份。
 *
 * [free] 是「重力 + 阻力 + 风 + 噪声 + 轨道」的组合式方程；
 * [path] 会接管位置，所以两种模式互斥、只画当前模式那组旋钮。
 */
@Serializable
@Keep
data class ParticleMotion(
    val mode: ParticleMotionMode = ParticleMotionMode.Free,
    /** 自转速度（0–2）。 */
    val spin: Float = 0f,
    /** 3D 翻面幅度（0–1）。 */
    val tilt: Float = 0f,
    val free: ParticleFreeMotion = ParticleFreeMotion(),
    val path: ParticlePathMotion = ParticlePathMotion(),
)

/**
 * 自由运动：所有参数都归一化，渲染层按屏幕尺寸换算。
 *
 * - [gravity] 0 = 无重力，1 = 真重力，2–3 更强；负值 = 向上（气泡 / 升腾）；
 * - [drag] 0..1（终端速度曲线）；[wind] -1..1（负左正右）；
 * - [turbulence] / [brownian] 0..1（curl 噪声 / 随机游走强度）；
 * - [orbit] / [spiral] 0..1（绕锚点环行 / 向内卷吸）；[attract] 0..1；
 * - [sway] 振幅（屏宽比例）、[swayCycles] 单次寿命内的正弦周期；[bounce] 0..1（边界反弹）。
 */
@Serializable
@Keep
data class ParticleFreeMotion(
    val gravity: Float = 1f,
    val drag: Float = 0.3f,
    val wind: Float = 0f,
    val turbulence: Float = 0f,
    val brownian: Float = 0f,
    val orbit: Float = 0f,
    val spiral: Float = 0f,
    val sway: Float = 0.2f,
    val swayCycles: Float = 1.5f,
    val attract: Float = 0f,
    val bounce: Float = 0f,
)

/** 预置路径：形状 + 行程缩放（乘在「初速 × 寿命」上）。 */
@Serializable
@Keep
data class ParticlePathMotion(
    val shape: ParticlePath = ParticlePath.Bezier,
    val travelScale: Float = 0.4f,
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
    /** 透明度抖动（0 = 每颗粒子一样亮，1 = 最暗到全亮）。 */
    val alphaJitter: Float = 0.15f,
    val haloStyle: ParticleHaloStyle = ParticleHaloStyle.Off,
    val haloAlpha: Float = 0.35f,
    val haloScale: Float = 1.8f,
    /** 拖尾长度（dp）；对**所有形状**生效，Line 形状会额外拉长。 */
    val trailDp: Float = 0f,
    val trailAlpha: Float = 0.5f,
    /** 闪烁：每颗粒子用自己的相位明灭（萤火 / 星点 / 烟花余烬）。 */
    val blink: Boolean = false,
    /** 单次寿命内的闪烁周期数。 */
    val blinkCycles: Float = 2f,
    /** 尺寸随寿命收缩的比例（0 = 不缩，0.8 = 末帧只剩 20%）。 */
    val sizeShrink: Float = 0f,
    /** 淡出曲线指数：1 = 线性，>1 = 后段更快消失。 */
    val fadeExp: Float = 1.5f,
    /** 一簇一色：同一次波次（烟花 / 球心爆）共用一个调色板颜色。 */
    val colorPerWave: Boolean = false,
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

// ---------------------------------------------------------------- 揭示

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
 * 导入的预设一律按用户预设落库（内置只读，不能从外部塞进来）。
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

/** 当前导出格式版本（v7 = 三层参数：通用 / 族 / 形态）。 */
const val AlertLibraryExportVersion: Int = 7
