package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertFamily
import hunoia.luno.core.effect.AlertContent
import hunoia.luno.runtime.floatball.alert.AlertEffectSpec
import hunoia.luno.runtime.floatball.alert.AlertLayerPlan
import hunoia.luno.runtime.floatball.alert.AlertPlayPlan
import hunoia.luno.runtime.floatball.alert.AlertRenderStyle
import hunoia.luno.runtime.floatball.alert.CapsuleSpec
import hunoia.luno.runtime.floatball.alert.CoverSpec
import hunoia.luno.runtime.floatball.alert.EdgeSpec
import hunoia.luno.runtime.floatball.alert.ParticlesSpec
import hunoia.luno.runtime.floatball.alert.RevealSpec
import hunoia.luno.runtime.floatball.alert.RingSpec
import hunoia.luno.runtime.floatball.alert.WashSpec

/**
 * 渲染计划 → 播放计划：「族 = 绘制器」，形态是 spec 里的 `shape` + 独立参数块。
 *
 * 每个族的参数在这里做一次夹取（范围是渲染端的安全区间），渲染层只读 spec。
 */
internal fun AlertRenderPlan.toPlayPlan(): AlertPlayPlan {
    val layers = layers.mapNotNull { it.toLayerPlan(content) }
    return AlertPlayPlan(layers = layers, seed = seed, playbackPolicy = playbackPolicy)
}

private fun AlertRenderLayer.toLayerPlan(content: AlertContent?): AlertLayerPlan? {
    val cycle = cycleMillis.coerceAtLeast(1)
    val baseBrightness = (0.55f * intensity * opacity).coerceIn(0.02f, 1f)
    val spec: AlertEffectSpec = when (family) {
        AlertFamily.Edge -> {
            val params = edge ?: return null
            EdgeSpec(
                shape = params.shape,
                backend = params.backend,
                cornerRadiusDp = params.cornerRadiusDp.coerceIn(0, 96),
                breathing = params.breathing.copy(
                    depthDp = params.breathing.depthDp.coerceIn(2f, 64f),
                    hardness = params.breathing.hardness.coerceIn(1f, 4f),
                    breathAmount = params.breathing.breathAmount.coerceIn(0f, 1f),
                ),
                sweep = params.sweep.copy(
                    travel = params.sweep.travel.coerceIn(0f, 1f),
                    depthDp = params.sweep.depthDp.coerceIn(2f, 64f),
                    hardness = params.sweep.hardness.coerceIn(1f, 4f),
                    headWidthDp = params.sweep.headWidthDp.coerceIn(2f, 48f),
                    tailPercent = params.sweep.tailPercent.coerceIn(0, 60),
                    tailFalloff = params.sweep.tailFalloff.coerceIn(0.5f, 4f),
                    tailAlpha = params.sweep.tailAlpha.coerceIn(0f, 1f),
                    headBoost = params.sweep.headBoost.coerceIn(0f, 1f),
                    noise = params.sweep.noise.coerceIn(0f, 1f),
                ),
                wave = params.wave.copy(
                    crests = params.wave.crests.coerceIn(2, 6),
                    waveWidthDp = params.wave.waveWidthDp.coerceIn(2f, 64f),
                    travel = params.wave.travel.coerceIn(0f, 1f),
                    depthDp = params.wave.depthDp.coerceIn(2f, 64f),
                    hardness = params.wave.hardness.coerceIn(1f, 4f),
                ),
                lightning = params.lightning.copy(
                    flashes = params.lightning.flashes.coerceIn(1, 8),
                    bolts = params.lightning.bolts.coerceIn(1, 8),
                    window = params.lightning.window.coerceIn(0.05f, 0.9f),
                    widthDp = params.lightning.widthDp.coerceIn(0.5f, 6f),
                    reachPercent = params.lightning.reachPercent.coerceIn(10, 90),
                ),
            )
        }
        AlertFamily.Ring -> {
            val params = ring ?: return null
            RingSpec(
                style = params.style,
                centerAtAnchor = params.centerAtAnchor,
                strokeWidthDp = params.strokeWidthDp.coerceIn(1f, 12f),
                ringsPerCycle = params.ringsPerCycle.coerceIn(1, 4),
                travelPercent = params.travelPercent.coerceIn(0, 300),
                nested = params.nested.copy(haloScale = params.nested.haloScale.coerceIn(0f, 2f)),
                halo = params.halo.copy(haloScale = params.halo.haloScale.coerceIn(0f, 3f)),
            )
        }
        AlertFamily.Capsule -> {
            val params = capsule ?: return null
            CapsuleSpec(
                shape = params.shape,
                sweep = params.sweep.copy(
                    strokeWidthDp = params.sweep.strokeWidthDp.coerceIn(0.5f, 6f),
                    heads = params.sweep.heads.coerceIn(1, 2),
                    bandPercent = params.sweep.bandPercent.coerceIn(5, 90),
                    travel = params.sweep.travel.coerceIn(0f, 1f),
                    trailScale = params.sweep.trailScale.coerceIn(0f, 4f),
                    trailAlpha = params.sweep.trailAlpha.coerceIn(0f, 1f),
                ),
                pulse = params.pulse.copy(
                    strokeWidthDp = params.pulse.strokeWidthDp.coerceIn(0.5f, 6f),
                    glowLayers = params.pulse.glowLayers.coerceIn(0, 2),
                    glowScale = params.pulse.glowScale.coerceIn(0f, 2f),
                    breatheScale = params.pulse.breatheScale.coerceIn(0f, 1f),
                ),
                flip = params.flip.copy(
                    strokeWidthDp = params.flip.strokeWidthDp.coerceIn(0.5f, 6f),
                    degrees = params.flip.degrees.coerceIn(0, 360),
                ),
            )
        }
        AlertFamily.Wash -> {
            val params = wash ?: return null
            WashSpec(
                shape = params.shape,
                amplitude = params.amplitude.coerceIn(0f, 1f),
                breath = params.breath,
                radial = params.radial.copy(softness = params.radial.softness.coerceIn(0f, 1f)),
                ring = params.ring.copy(zoomPercent = params.ring.zoomPercent.coerceIn(0, 40)),
            )
        }
        AlertFamily.Cover -> {
            val params = cover ?: return null
            CoverSpec(
                mask = params.mask,
                amplitude = params.amplitude.coerceIn(0f, 1f),
                curve = params.curve.coerceIn(1f, 4f),
                circle = params.circle.copy(softness = params.circle.softness.coerceIn(0f, 1f)),
                iris = params.iris.copy(rimDp = params.iris.rimDp.coerceIn(0f, 6f)),
                slit = params.slit.copy(slitDp = params.slit.slitDp.coerceIn(2f, 24f)),
                bands = params.bands.copy(count = params.bands.count.coerceIn(3, 15)),
                shards = params.shards.copy(
                    count = params.shards.count.coerceIn(6, 30),
                    softness = params.shards.softness.coerceIn(0f, 1f),
                ),
            )
        }
        AlertFamily.Particles -> {
            val params = particle ?: return null
            ParticlesSpec(params = params)
        }
        AlertFamily.Reveal -> {
            val params = reveal ?: return null
            val title = content?.title.orEmpty().trim().take(RevealTitleMax)
            val body = content?.body.orEmpty().trim().take(RevealBodyMax)
            if (title.isEmpty() && body.isEmpty() && content?.iconPackage == null) return null
            RevealSpec(
                pattern = params.pattern,
                title = title,
                body = body,
                iconPackage = content?.iconPackage,
                staggerPercent = params.staggerPercent,
                scaleFrom = params.scaleFrom,
                direction = params.maskDirection,
            )
        }
    }
    val brightness = when (family) {
        AlertFamily.Particles -> {
            ((particle?.look?.alpha ?: 0.8f) * intensity * opacity).coerceIn(0.02f, 1f)
        }
        // Wash 的 amplitude 在 spec 里另有用途（形状/缩放），这里把强度峰值算进 brightness；
        // Cover 的最终不透明度由 spec.amplitude 在渲染层相乘，避免两处各乘一遍。
        AlertFamily.Wash -> {
            val amplitude = (spec as WashSpec).amplitude
            (amplitude * intensity * opacity).coerceIn(0.02f, 1f)
        }
        AlertFamily.Cover -> (intensity * opacity).coerceIn(0.02f, 1f)
        else -> baseBrightness
    }
    return AlertLayerPlan(
        style = family.toRenderStyle(),
        cycles = cycles.coerceIn(1, 20),
        cycleMillis = cycle,
        brightness = brightness,
        startAt = startAt.coerceAtLeast(0),
        spec = spec,
        palette = palette,
        appPackage = appPackage,
        presetId = presetId,
        layerId = layerId,
    )
}

/** 揭示文字上限：标题 24 / 正文 40（与通知路径口径一致）。 */
private const val RevealTitleMax = 24
private const val RevealBodyMax = 40

internal fun AlertFamily.toRenderStyle(): AlertRenderStyle = when (this) {
    AlertFamily.Edge -> AlertRenderStyle.Edge
    AlertFamily.Ring -> AlertRenderStyle.Ring
    AlertFamily.Capsule -> AlertRenderStyle.Capsule
    AlertFamily.Wash -> AlertRenderStyle.Wash
    AlertFamily.Cover -> AlertRenderStyle.Cover
    AlertFamily.Particles -> AlertRenderStyle.Particles
    AlertFamily.Reveal -> AlertRenderStyle.Reveal
}
