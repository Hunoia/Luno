package hunoia.luno.floatball.settings.alert

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLimit
import hunoia.luno.config.model.allPalettes
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.BallParams
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.RevealParams
import hunoia.luno.config.model.ScreenParams
import java.util.UUID

/**
 * 预设 / 层编辑的纯数据操作（无 Compose、可单测）。
 *
 * 快照口径：从内置素材添加层时**复制参数**并记 [AlertLayer.sourcePresetId]，
 * 之后内置素材怎么改都不会静默改动用户组合。
 */

/** 把 [layer] 追加到预设末尾（调用方负责先判 [AlertPreset.addBlockedBy]）。 */
internal fun AlertPreset.withAddedLayer(layer: AlertLayer): AlertPreset =
    copy(layers = layers + layer)

/** 替换同 id 的层；id 不存在时原样返回（不偷偷追加）。 */
internal fun AlertPreset.withReplacedLayer(layer: AlertLayer): AlertPreset =
    copy(layers = layers.map { if (it.id == layer.id) layer else it })

internal fun AlertPreset.withoutLayer(layerId: String): AlertPreset =
    copy(layers = layers.filterNot { it.id == layerId })

/** 改绘制顺序：[-1] 上移、[+1] 下移；越界时原样返回。 */
internal fun AlertPreset.withMovedLayer(layerId: String, delta: Int): AlertPreset {
    val index = layers.indexOfFirst { it.id == layerId }
    if (index < 0) return this
    val target = (index + delta).coerceIn(0, layers.lastIndex)
    if (target == index) return this
    val mutable = layers.toMutableList()
    val layer = mutable.removeAt(index)
    mutable.add(target, layer)
    return copy(layers = mutable)
}

/** 复制一层（新 id，名称加「副本」后缀由 UI 负责传递）。 */
internal fun AlertLayer.duplicated(): AlertLayer = copy(id = newLayerId())

/** 复制内置素材 / 他人的预设为用户预设（新 id，内置位清除）。 */
internal fun AlertPreset.asUserCopy(newId: String = newUserPresetId()): AlertPreset = copy(
    id = newId,
    builtin = false,
    layers = layers.map { it.copy(id = newLayerId(), enabled = true) },
)

internal fun newUserPresetId(): String = "user_${UUID.randomUUID()}"

/** 新建预设：一个默认粒子层，打开就能播，不等用户先拼。 */
internal fun newUserPreset(name: String): AlertPreset = AlertPreset(
    id = newUserPresetId(),
    name = name,
    builtin = false,
    layers = listOf(blankLayer(AlertKind.Particle, newLayerId())),
)

/** 空白层：按 kind 给一份能直接播的默认参数（不是全零，否则加完像没加）。 */
internal fun blankLayer(kind: AlertKind, id: String = newLayerId()): AlertLayer {
    val base = AlertLayer(
        id = id,
        enabled = true,
        kind = kind,
        timing = AlertTiming(baseMillis = 1000),
        color = AlertColorRef(),
    )
    return when (kind) {
        AlertKind.Edge -> base.copy(
            edge = EdgeParams(),
        )
        AlertKind.Particle -> base.copy(
            particle = ParticleParams(
                emitter = ParticleEmitter(count = 40),
                motion = ParticleMotion(),
                look = ParticleLook(),
            ),
        )
        AlertKind.Ball -> base.copy(ball = BallParams())
        AlertKind.Capsule -> base.copy(capsule = CapsuleParams())
        AlertKind.Screen -> base.copy(screen = ScreenParams())
        AlertKind.Reveal -> base.copy(reveal = RevealParams())
    }
}

/** 素材库里的全部调色板候选（含内置，按 [AlertLibrary.allPalettes][hunoia.luno.config.model.allPalettes] 口径取）。 */
internal fun AlertLibrary.paletteChoices(): List<hunoia.luno.config.model.AlertPalette> =
    allPalettes()

/** 该预设还能不能再加一层；不能时返回原因（UI 显示禁用状态）。 */
internal fun AlertPreset.addBlockedReason(kind: AlertKind): AlertLimit? = addBlockedBy(kind)

/** 重名避让：base、base (2)、base (3)…（素材库 / 导入共用）。 */
internal fun uniquePresetName(base: String, existing: Collection<String>): String {
    val trimmed = base.trim().ifBlank { "效果" }
    if (trimmed !in existing) return trimmed
    var index = 2
    while ("$trimmed ($index)" in existing) index++
    return "$trimmed ($index)"
}

/** 预设（组合）最多 5 层，编辑器文案用。 */
internal const val MaxEnabledLayers = AlertLimits.MaxEnabledLayers
// ---------------------------------------------------------------- 层参数替换助手

internal fun AlertLayer.withLook(look: hunoia.luno.config.model.ParticleLook): AlertLayer =
    copy(particle = particle?.copy(look = look))

internal fun AlertLayer.withMotion(motion: hunoia.luno.config.model.ParticleMotion): AlertLayer =
    copy(particle = particle?.copy(motion = motion))

internal fun AlertLayer.withReveal(reveal: hunoia.luno.config.model.RevealParams): AlertLayer =
    copy(reveal = reveal)
