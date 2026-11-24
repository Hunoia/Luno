package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertVariant
import kotlin.math.roundToInt

/**
 * 预设 → 播放层（纯 JVM，可单测）：只做选择与折算，不读配置、不碰 Android。
 *
 * [tierFactor] 是应用动效档位倍率（由调用侧按 `MotionFamily` 给出，1 = 标准档）；
 * 单循环时长 = 基准 × 档位 ÷ 速度倍率，循环数来自预设时序。
 */
data class ResolvedAlertLayer(
    val presetId: String,
    val host: AlertHost,
    val kind: AlertKind,
    /** 已含档位与速度的单循环时长（ms）。 */
    val cycleMillis: Int,
    val cycles: Int,
    val variant: AlertVariant,
) {
    val totalMillis: Int get() = cycleMillis * cycles
}

/** 一次播放的解析结果；[seed] 由调用侧取随机数，测试传固定值。 */
data class AlertPresetPlan(
    val layers: List<ResolvedAlertLayer>,
    val seed: Int = 0,
) {
    val durationMillis: Int get() = layers.maxOfOrNull { it.totalMillis } ?: 0

    val isEmpty: Boolean get() = layers.isEmpty()

    val stageLayers: List<ResolvedAlertLayer>
        get() = layers.filter { it.host != AlertHost.Capsule }

    val capsuleLayers: List<ResolvedAlertLayer>
        get() = layers.filter { it.host == AlertHost.Capsule }
}

/** 解析失败的原因（调用侧用于日志 / Toast，不在这里做 UI）。 */
sealed interface PresetResolveError {
    data class UnknownPreset(val presetId: String) : PresetResolveError
    data class MissingHost(val presetId: String, val host: AlertHost) : PresetResolveError
}

sealed interface PresetResolveResult {
    data class Ok(val layer: ResolvedAlertLayer) : PresetResolveResult
    data class Failed(val error: PresetResolveError) : PresetResolveResult
}

object AlertPresetResolver {

    /**
     * 解析一个（预设, 宿主）请求。
     *
     * @param tierFactor 1 = 标准档；>1 表示更快（时长变短）由调用方按动效档位给出。
     */
    fun resolve(
        preset: AlertPreset?,
        presetId: String,
        host: AlertHost,
        tierFactor: Float = 1f,
    ): PresetResolveResult {
        if (preset == null) return PresetResolveResult.Failed(PresetResolveError.UnknownPreset(presetId))
        val variant = preset.variants.firstOrNull { it.host == host }
            ?: return PresetResolveResult.Failed(PresetResolveError.MissingHost(presetId, host))

        val speed = variant.timing.speedPercent.coerceIn(10, 300)
        val tier = tierFactor.coerceAtLeast(0.01f)
        val cycleMillis = (variant.timing.baseMillis * tier * 100f / speed)
            .roundToInt()
            .coerceAtLeast(1)
        return PresetResolveResult.Ok(
            ResolvedAlertLayer(
                presetId = preset.id,
                host = host,
                kind = variant.kind,
                cycleMillis = cycleMillis,
                cycles = variant.timing.cycles.coerceIn(1, 20),
                variant = variant,
            ),
        )
    }

    /** 批量解析：跳过解析失败的层（调用侧按需记日志）。 */
    fun resolveAll(
        presets: Map<String, AlertPreset>,
        requests: List<Pair<String, AlertHost>>,
        tierFactor: Float = 1f,
        seed: Int = 0,
    ): AlertPresetPlan = AlertPresetPlan(
        layers = requests.mapNotNull { (id, host) ->
            (resolve(presets[id], id, host, tierFactor) as? PresetResolveResult.Ok)?.layer
        },
        seed = seed,
    )
}