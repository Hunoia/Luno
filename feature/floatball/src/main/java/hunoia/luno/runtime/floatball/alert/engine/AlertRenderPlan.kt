package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertFamily
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertLayerCategory
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.CoverParams
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.RevealParams
import hunoia.luno.config.model.RingParams
import hunoia.luno.config.model.WashParams
import hunoia.luno.config.model.category
import hunoia.luno.config.model.host
import hunoia.luno.core.effect.AlertContent
import hunoia.luno.runtime.floatball.alert.AlertPaletteSpec
import kotlin.math.roundToInt

/**
 * 渲染层计划（纯 JVM）：预设的启用层 + 全局强度 / 速度 → 渲染端可直接消费的层。
 *
 * 渲染端不再读配置，也不再判类别：宿主 / 参数块在构建时就已落到具体层。
 */
data class AlertRenderLayer(
    val presetId: String?,
    /** 预设内稳定的层 id（渲染 key 用；不再用列表序号）。 */
    val layerId: String? = null,
    val host: AlertHost,
    val family: AlertFamily,
    val cycleMillis: Int,
    val cycles: Int,
    /** 全局强度倍率（1 = 预设原样，0..2）；各组渲染器再映射到自己的 alpha 区间。 */
    val intensity: Float = 1f,
    /** 层自己的不透明度（0..1）：所有族通用，乘进最终 alpha。 */
    val opacity: Float = 1f,
    val palette: AlertPaletteSpec? = null,
    val appPackage: String? = null,
    val edge: EdgeParams? = null,
    val ring: RingParams? = null,
    val capsule: CapsuleParams? = null,
    val wash: WashParams? = null,
    val cover: CoverParams? = null,
    val particle: ParticleParams? = null,
    val reveal: RevealParams? = null,
) {
    val totalMillis: Int get() = cycleMillis * cycles
}

/**
 * 一次播放的渲染计划。
 *
 * [skipped] 保留解析失败原因（未知预设 / 空预设），由服务侧记日志；
 * 渲染端只消费 [layers]。
 */
data class AlertRenderPlan(
    val layers: List<AlertRenderLayer>,
    val seed: Int = 0,
    /** 预设自带的冲突策略，随计划一路带到播放端（调用方不判）。 */
    val playbackPolicy: AlertPlaybackPolicy = AlertPlaybackPolicy.Skip,
    val skipped: List<PresetResolveError> = emptyList(),
    /** 揭示类内容（标题 / 正文 / 图标来源）；无揭示层时为 null。 */
    val content: AlertContent? = null,
) {
    val isEmpty: Boolean get() = layers.isEmpty()

    val durationMillis: Int get() = layers.maxOfOrNull { it.totalMillis } ?: 0

    /** 舞台窗层（除胶囊宿主外的全部）。 */
    val stageLayers: List<AlertRenderLayer>
        get() = layers.filter { it.host != AlertHost.Capsule }

    /** 胶囊窗层（通知路径；外部调用可传 rect 近似）。 */
    val capsuleLayers: List<AlertRenderLayer>
        get() = layers.filter { it.host == AlertHost.Capsule }

    /** 只保留指定层（编辑器「试播这一层」用）；null / 空 = 原样返回。 */
    fun onlyLayer(layerId: String?): AlertRenderPlan =
        if (layerId == null) this else copy(layers = layers.filter { it.layerId == layerId })
}

/** 解析失败的原因（调用侧用于日志 / Toast，不在这里做 UI）。 */
sealed interface PresetResolveError {
    data class UnknownPreset(val presetId: String) : PresetResolveError
    data class EmptyPreset(val presetId: String) : PresetResolveError
}

object AlertRenderPlanBuilder {

    /**
     * @param paletteFor 预设 / 层颜色引用 → 调色板（null = 跟随动效回退颜色）。
     * @param intensityScale 全局强度（1 = 预设原样，0..2）。
     * @param speedScale 全局速度（1 = 基准，0.25..4；>1 更快）。
     * @param tierForKind 应用动效档位倍率（1 = 标准档，按层类别折叠）。
     */
    fun build(
        preset: AlertPreset?,
        presetId: String,
        paletteFor: (AlertColorRef) -> AlertPaletteSpec?,
        appPackage: String? = null,
        intensityScale: Float = 1f,
        speedScale: Float = 1f,
        tierForKind: (AlertLayerCategory) -> Float = { 1f },
        seed: Int = 0,
        content: AlertContent? = null,
        playbackPolicy: AlertPlaybackPolicy = AlertPlaybackPolicy.Skip,
    ): AlertRenderPlan {
        if (preset == null) {
            return AlertRenderPlan(
                layers = emptyList(),
                seed = seed,
                playbackPolicy = playbackPolicy,
                skipped = listOf(PresetResolveError.UnknownPreset(presetId)),
                content = content,
            )
        }
        val globalSpeed = speedScale.coerceIn(0.25f, 4f)
        val globalIntensity = intensityScale.coerceIn(0f, 2f)
        val rendered = preset.renderableLayers().mapNotNull { layer ->
            val tier = tierForKind(layer.family.category).coerceAtLeast(0.01f)
            val layerSpeed = layer.timing.speedPercent.coerceIn(
                AlertLimits.SpeedMin,
                300,
            ) / 100f
            val effective = globalSpeed * layerSpeed
            val cycle = (layer.timing.baseMillis * tier / effective)
                .roundToInt()
                .coerceAtLeast(1)
            val color = preset.paletteId?.let { AlertColorRef(paletteId = it) } ?: layer.color
            AlertRenderLayer(
                presetId = preset.id,
                layerId = layer.id,
                host = layer.family.host,
                family = layer.family,
                cycleMillis = cycle,
                cycles = layer.timing.cycles.coerceIn(1, 20),
                intensity = globalIntensity,
                opacity = layer.opacity.coerceIn(0f, 1f),
                palette = paletteFor(color),
                appPackage = appPackage,
                edge = layer.edge,
                ring = layer.ring,
                capsule = layer.capsule,
                wash = layer.wash,
                cover = layer.cover,
                particle = layer.particle,
                reveal = layer.reveal,
            )
        }
        if (rendered.isEmpty()) {
            return AlertRenderPlan(
                layers = emptyList(),
                seed = seed,
                playbackPolicy = playbackPolicy,
                skipped = listOf(PresetResolveError.EmptyPreset(presetId)),
                content = content,
            )
        }
        return AlertRenderPlan(
            layers = applyParticleBudget(rendered),
            seed = seed,
            playbackPolicy = playbackPolicy,
            skipped = emptyList(),
            content = content,
        )
    }

    /**
     * 跨层粒子总预算：按层顺序分配，超出截断（后面的层拿剩余额度，最少 0 = 丢弃）。
     *
     * 不是每层 600——5 层各带 600 会直接压垮帧率。
     * 预算按**实际粒子数**算：`count × waves`（渲染端每波都会再乘一份 count）。
     */
    private fun applyParticleBudget(layers: List<AlertRenderLayer>): List<AlertRenderLayer> {
        if (layers.sumOf { it.particleTotalCount() } <= AlertLimits.ParticleBudget) {
            return layers
        }
        var remaining = AlertLimits.ParticleBudget
        return layers.mapNotNull { layer ->
            val params = layer.particle ?: return@mapNotNull layer
            val waves = params.emitter.waves.coerceAtLeast(1)
            val anchors = if (params.emitter.anchorPerWave) {
                params.emitter.anchorCount.coerceIn(1, 8)
            } else {
                1
            }
            val requested = params.emitter.count.coerceAtLeast(0) * waves * anchors
            val allowed = requested.coerceAtMost(remaining)
            remaining -= allowed
            if (allowed <= 0) {
                null
            } else {
                val perWave = allowed / (waves * anchors)
                if (perWave <= 0) {
                    null
                } else {
                    layer.copy(particle = params.copy(emitter = params.emitter.copy(count = perWave)))
                }
            }
        }
    }

    private fun AlertRenderLayer.particleTotalCount(): Int {
        val emitter = particle?.emitter ?: return 0
        val anchors = if (emitter.anchorPerWave) emitter.anchorCount.coerceIn(1, 8) else 1
        return emitter.count.coerceAtLeast(0) *
            emitter.waves.coerceAtLeast(1) *
            anchors
    }
}
