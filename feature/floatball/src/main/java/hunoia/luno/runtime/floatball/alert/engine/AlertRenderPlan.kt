package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertSlot
import hunoia.luno.config.model.BallParams
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.RevealParams
import hunoia.luno.config.model.ScreenParams
import hunoia.luno.runtime.floatball.alert.AlertPaletteSpec
import kotlin.math.roundToInt

/**
 * 渲染层计划（纯 JVM）：预设 + 宿主 + 覆盖 → 渲染端可直接消费的层。
 *
 * 与 [AlertPresetPlan] 的区别：这里已经合并了速度 / 强度覆盖、解析了调色板，
 * 并只保留该 [AlertKind] 对应的参数块。渲染端不再读配置，也不再判 `when(槽位)`。
 */
data class AlertRenderLayer(
    val presetId: String?,
    /** 配置槽位（外部调用可能为 null）；只影响日志与粒子种子。 */
    val slot: AlertSlot? = null,
    val host: AlertHost,
    val kind: AlertKind,
    val cycleMillis: Int,
    val cycles: Int,
    /** 覆盖后的强度倍率（1 = 预设原样，0..2）。 */
    val intensity: Float = 1f,
    val palette: AlertPaletteSpec? = null,
    val appPackage: String? = null,
    val particle: ParticleParams? = null,
    val edge: EdgeParams? = null,
    val ball: BallParams? = null,
    val capsule: CapsuleParams? = null,
    val screen: ScreenParams? = null,
    val reveal: RevealParams? = null,
) {
    val totalMillis: Int get() = cycleMillis * cycles
}

/**
 * 一次播放的渲染计划。
 *
 * [skipped] 保留解析失败原因（未知预设 / 宿主缺失），由服务侧记日志；
 * 渲染端只消费 [layers]。
 */
data class AlertRenderPlan(
    val layers: List<AlertRenderLayer>,
    val seed: Int = 0,
    val skipped: List<PresetResolveError> = emptyList(),
) {
    val isEmpty: Boolean get() = layers.isEmpty()

    val durationMillis: Int get() = layers.maxOfOrNull { it.totalMillis } ?: 0

    /** 舞台窗层（除胶囊宿主外的全部）。 */
    val stageLayers: List<AlertRenderLayer>
        get() = layers.filter { it.host != AlertHost.Capsule }

    /** 胶囊窗层（通知路径；外部调用可传 rect 近似）。 */
    val capsuleLayers: List<AlertRenderLayer>
        get() = layers.filter { it.host == AlertHost.Capsule }
}

/** 一次槽位请求：预设 + 宿主 + 覆盖（强度 / 速度倍率）。 */
data class AlertSlotRequest(
    val presetId: String,
    val host: AlertHost,
    val slot: AlertSlot? = null,
    val intensity: Float? = null,
    val speedScale: Float? = null,
    /** 槽位颜色覆盖（优先于预设变体颜色）。 */
    val colorOverride: AlertColorRef? = null,
)

object AlertRenderPlanBuilder {

    /**
     * @param paletteFor 预设颜色引用 → 调色板（null = 跟随动效颜色）；由调用侧按素材库解析。
     * @param tierFactor 动效档位倍率（1 = 标准档）。
     */
    fun build(
        requests: List<AlertSlotRequest>,
        presets: Map<String, AlertPreset>,
        paletteFor: (AlertColorRef) -> AlertPaletteSpec?,
        appPackage: String? = null,
        tierFactor: Float = 1f,
        seed: Int = 0,
    ): AlertRenderPlan {
        val layers = mutableListOf<AlertRenderLayer>()
        val skipped = mutableListOf<PresetResolveError>()
        requests.forEach { request ->
            val preset = presets[request.presetId]
            val resolved = AlertPresetResolver.resolve(
                preset = preset,
                presetId = request.presetId,
                host = request.host,
                tierFactor = tierFactor,
            )
            when (resolved) {
                is PresetResolveResult.Failed -> skipped += resolved.error
                is PresetResolveResult.Ok -> {
                    val layer = resolved.layer
                    val variant = layer.variant
                    val speedScale = request.speedScale?.coerceIn(0.25f, 4f) ?: 1f
                    val cycle = (layer.cycleMillis / speedScale).roundToInt().coerceAtLeast(1)
                    layers += AlertRenderLayer(
                        presetId = layer.presetId,
                        slot = request.slot,
                        host = layer.host,
                        kind = layer.kind,
                        cycleMillis = cycle,
                        cycles = layer.cycles,
                        intensity = (request.intensity ?: 1f).coerceIn(0f, 2f),
                        palette = paletteFor(request.colorOverride ?: variant.color),
                        appPackage = appPackage,
                        particle = variant.particle.takeIf { variant.kind == AlertKind.Particle },
                        edge = variant.edge.takeIf { variant.kind == AlertKind.Edge },
                        ball = variant.ball.takeIf { variant.kind == AlertKind.Ball },
                        capsule = variant.capsule.takeIf { variant.kind == AlertKind.Capsule },
                        screen = variant.screen.takeIf { variant.kind == AlertKind.Screen },
                        reveal = variant.reveal.takeIf { variant.kind == AlertKind.Reveal },
                    )
                }
            }
        }
        return AlertRenderPlan(layers = layers, seed = seed, skipped = skipped)
    }
}