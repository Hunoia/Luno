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
 * 渲染计划 → 播放计划：v6 起「族 = 绘制器」，屏缘 / 胶囊的形态是 spec 里的 `shape`。
 *
 * 每个族的参数在这里做一次夹取（范围是渲染端的安全区间），渲染层只读 spec。
 */
internal fun AlertRenderPlan.toPlayPlan(): AlertPlayPlan {
    val layers = layers.mapNotNull { it.toLayerPlan(content) }
    return AlertPlayPlan(layers = layers, seed = seed, playbackPolicy = playbackPolicy)
}

private fun AlertRenderLayer.toLayerPlan(content: AlertContent?): AlertLayerPlan? {
    val cycles = if (family == AlertFamily.Particles) {
        // 粒子层只有「波次」：编辑器里不再暴露 timing.cycles，避免两个循环旋钮。
        maxOf(particle?.emitter?.waves ?: 1, 1)
    } else {
        cycles.coerceAtLeast(1)
    }
    val cycle = cycleMillis.coerceAtLeast(1)
    val baseBrightness = (0.55f * intensity * opacity).coerceIn(0.02f, 1f)
    val spec: AlertEffectSpec = when (family) {
        AlertFamily.Edge -> {
            val params = edge ?: return null
            EdgeSpec(
                shape = params.shape,
                backend = params.backend,
                cornerRadiusDp = params.cornerRadiusDp.coerceIn(0, 96),
                depthDp = params.depthDp.coerceIn(2f, 64f),
                hardness = params.hardness.coerceIn(1f, 4f),
                breathAmount = params.breathAmount.coerceIn(0f, 1f),
                path = params.path,
                travel = params.travel.coerceIn(0f, 1f),
                headWidthDp = params.headWidthDp.coerceIn(2f, 48f),
                tailLength = params.tailLength.coerceIn(0f, 0.9f),
                tailFalloff = params.tailFalloff.coerceIn(0.5f, 4f),
                tailAlpha = params.tailAlpha.coerceIn(0f, 1f),
                headBoost = params.headBoost.coerceIn(0f, 1f),
                noise = params.noise.coerceIn(0f, 1f),
                dual = params.dual,
                reverse = params.reverse,
                crests = params.crests.coerceIn(2, 6),
                waveWidthDp = params.waveWidthDp.coerceIn(2f, 64f),
                bolts = params.bolts.coerceIn(0, 8),
                boltFlashes = params.boltFlashes.coerceIn(1, 8),
                boltWindow = params.boltWindow.coerceIn(0.05f, 0.9f),
                boltWidthDp = params.boltWidthDp.coerceIn(0.5f, 6f),
                boltReachPercent = params.boltReachPercent.coerceIn(10, 90),
            )
        }
        AlertFamily.Ring -> {
            val params = ring ?: return null
            RingSpec(
                style = params.style,
                ringsPerCycle = params.ringsPerCycle.coerceIn(1, 4),
                travelPercent = params.travelPercent.coerceIn(0, 300),
                strokeWidthDp = params.strokeWidthDp.coerceIn(1f, 12f),
                haloScale = params.haloScale.coerceIn(0f, 2f),
                centerAtAnchor = params.centerAtAnchor,
            )
        }
        AlertFamily.Capsule -> {
            val params = capsule ?: return null
            CapsuleSpec(
                shape = params.shape,
                strokeWidthDp = params.strokeWidthDp.coerceIn(0.5f, 6f),
                fill = params.fill,
                heads = params.heads.coerceIn(1, 2),
                bandWidth = (params.bandPercent.coerceIn(5, 90) / 100f),
                travel = params.travel.coerceIn(0f, 1f),
                trailScale = params.trailScale.coerceIn(0f, 4f),
                trailAlpha = params.trailAlpha.coerceIn(0f, 1f),
                reverse = params.reverse,
                glowLayers = params.glowLayers.coerceIn(0, 2),
                glowScale = params.glowScale.coerceIn(0f, 2f),
                breatheScale = params.breatheScale.coerceIn(0f, 1f),
                flipDegrees = params.flipDegrees.coerceIn(0, 360),
            )
        }
        AlertFamily.Wash -> {
            val params = wash ?: return null
            WashSpec(
                shape = params.shape,
                amplitude = params.amplitude.coerceIn(0f, 1f),
                zoomPercent = params.zoomPercent.coerceIn(0, 40),
                softness = params.softness.coerceIn(0f, 1f),
                breath = params.breath,
            )
        }
        AlertFamily.Cover -> {
            val params = cover ?: return null
            CoverSpec(
                mask = params.mask,
                amplitude = params.amplitude.coerceIn(0f, 1f),
                bands = params.bands.coerceIn(3, 15),
                shards = params.shards.coerceIn(6, 30),
                slitDp = params.slitDp.coerceIn(2f, 24f),
                rimDp = params.rimDp.coerceIn(0f, 6f),
                softness = params.softness.coerceIn(0f, 1f),
                curve = params.curve.coerceIn(1f, 4f),
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
        cycles = cycles,
        cycleMillis = cycle,
        brightness = brightness,
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
