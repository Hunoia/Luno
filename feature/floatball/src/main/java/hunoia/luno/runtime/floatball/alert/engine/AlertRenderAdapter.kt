package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.BallPattern
import hunoia.luno.config.model.CapsulePattern
import hunoia.luno.config.model.EdgePattern
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.ScreenPattern
import hunoia.luno.runtime.floatball.alert.AlertLayerPlan
import hunoia.luno.runtime.floatball.alert.AlertPlayPlan
import hunoia.luno.runtime.floatball.alert.AlertRenderStyle
import hunoia.luno.runtime.floatball.alert.AnchorAlertStyle
import hunoia.luno.runtime.floatball.alert.BallBurstSpec
import hunoia.luno.runtime.floatball.alert.BallOrbit3DSpec
import hunoia.luno.runtime.floatball.alert.BallSpiralSpec
import hunoia.luno.runtime.floatball.alert.BallHaloSpec
import hunoia.luno.runtime.floatball.alert.BreathOutlineSpec
import hunoia.luno.runtime.floatball.alert.CapsuleAlertStyle
import hunoia.luno.runtime.floatball.alert.EdgeAlertStyle
import hunoia.luno.runtime.floatball.alert.EdgeBladeRiseSpec
import hunoia.luno.runtime.floatball.alert.EdgeCometSpec
import hunoia.luno.runtime.floatball.alert.EdgeFireworksSpec
import hunoia.luno.runtime.floatball.alert.EdgeFlowSpec
import hunoia.luno.runtime.floatball.alert.EdgeLightningSpec
import hunoia.luno.runtime.floatball.alert.EdgeRibbonSpec
import hunoia.luno.runtime.floatball.alert.EdgeGlowSpec
import hunoia.luno.runtime.floatball.alert.EdgeRippleSpec
import hunoia.luno.runtime.floatball.alert.InnerSweepSpec
import hunoia.luno.runtime.floatball.alert.ParticleAlertStyle
import hunoia.luno.runtime.floatball.alert.ParticlesSpec
import hunoia.luno.runtime.floatball.alert.DualChaseSpec
import hunoia.luno.runtime.floatball.alert.Flip3DSpec
import hunoia.luno.runtime.floatball.alert.PulseSpec
import hunoia.luno.runtime.floatball.alert.RingSpec
import hunoia.luno.runtime.floatball.alert.RippleSpec
import hunoia.luno.runtime.floatball.alert.ScreenAlertStyle
import hunoia.luno.runtime.floatball.alert.RevealAlertStyle
import hunoia.luno.runtime.floatball.alert.RevealSpec
import hunoia.luno.runtime.floatball.alert.ScreenSpec
import hunoia.luno.runtime.floatball.alert.ShimmerSpec
import hunoia.luno.runtime.floatball.alert.ShockwaveSpec
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.core.effect.AlertContent

/**
 * 渲染计划 → 舞台 / 胶囊播放计划。
 *
 * 这一层是 v4 层参数（[AlertRenderLayer]）到绘制器的桥：粒子直接走统一引擎
 * （参数原样透传），其余族把新参数映射到上次重做过的专用绘制器。新增形态（缎带 /
 * 闪电 / 螺旋卷吸等）先按最接近的绘制器回落，Step 2 再换独立实现。
 *
 * 纯 JVM，可单测：不读配置、不碰 Android。
 */
internal fun AlertRenderPlan.toPlayPlan(): AlertPlayPlan {
    val layers = layers.mapNotNull { it.toLayerPlan(content) }
    return AlertPlayPlan(layers = layers, seed = seed, playbackPolicy = playbackPolicy)
}

private fun AlertRenderLayer.toLayerPlan(content: AlertContent?): AlertLayerPlan? {
    val cycles = if (kind == AlertKind.Particle) {
        // 粒子层只有「波次」：编辑器里不再暴露 timing.cycles，避免两个循环旋钮。
        maxOf(particle?.emitter?.waves ?: 1, 1)
    } else {
        cycles.coerceAtLeast(1)
    }
    val cycle = cycleMillis.coerceAtLeast(1)
    // 强度 1 = 预设原样；映射到旧亮度旋钮的 alpha 峰值区间。
    val brightness = (0.55f * intensity).coerceIn(0.08f, 1f)
    return when (kind) {
        AlertKind.Edge -> edge?.let { params ->
            val (style, spec, amount) = when (params.pattern) {
                EdgePattern.Glow -> Triple(
                    EdgeAlertStyle.EdgeGlow,
                    EdgeGlowSpec(
                        cornerRadiusDp = params.cornerRadiusDp,
                        hardness = params.hardness.coerceIn(1f, 4f),
                    ),
                    norm(
                        params.bandWidthDp,
                        FloatBallDefaults.AlertEdgeGlowBandWidthDpMin.toFloat(),
                        FloatBallDefaults.AlertEdgeGlowBandWidthDpMax.toFloat(),
                    ),
                )
                EdgePattern.Flow -> Triple(
                    EdgeAlertStyle.EdgeFlow,
                    paramEdgeFlow(params),
                    norm(
                        params.bandWidthDp,
                        FloatBallDefaults.AlertFlowBandWidthDpMin.toFloat(),
                        FloatBallDefaults.AlertFlowBandWidthDpMax.toFloat(),
                    ),
                )
                EdgePattern.Comet -> Triple(
                    EdgeAlertStyle.Comet,
                    EdgeCometSpec(
                        headWidthDp = params.bandWidthDp.coerceIn(4f, 28f),
                        // trailScale 3 ≈ 9% 周长；换分辨率不会改变观感。
                        tailFraction = (params.trailScale * 0.03f).coerceIn(0.03f, 0.35f),
                        tailAlpha = params.trailAlpha.coerceIn(0f, 1f),
                        cornerRadiusDp = params.cornerRadiusDp,
                    ),
                    norm(
                        params.bandWidthDp,
                        FloatBallDefaults.AlertFlowBandWidthDpMin.toFloat(),
                        FloatBallDefaults.AlertFlowBandWidthDpMax.toFloat(),
                    ),
                )
                EdgePattern.Ripple -> Triple(
                    EdgeAlertStyle.EdgeRipple,
                    EdgeRippleSpec(
                        crestCount = params.crests.coerceIn(2, 6),
                        cornerRadiusDp = params.cornerRadiusDp,
                    ),
                    norm(
                        params.bandWidthDp * 2f,
                        FloatBallDefaults.AlertEdgeRippleSizeDpMin.toFloat(),
                        FloatBallDefaults.AlertEdgeRippleSizeDpMax.toFloat(),
                    ),
                )
                EdgePattern.Fireworks -> Triple(
                    EdgeAlertStyle.EdgeFireworks,
                    EdgeFireworksSpec(
                        burstCount = params.strikeCount.coerceIn(1, 6),
                        particleSizeDp = params.bandWidthDp.coerceIn(2f, 10f),
                        cornerRadiusDp = params.cornerRadiusDp,
                    ),
                    0.5f,
                )
                EdgePattern.Blade -> Triple(
                    EdgeAlertStyle.EdgeBladeRise,
                    EdgeBladeRiseSpec(bladeHeightDp = params.bladeHeightDp.coerceIn(60, 360)),
                    norm(
                        params.bandWidthDp,
                        FloatBallDefaults.AlertEdgeBladeWidthDpMin.toFloat(),
                        FloatBallDefaults.AlertEdgeBladeWidthDpMax.toFloat(),
                    ),
                )
                EdgePattern.Ribbon -> Triple(
                    EdgeAlertStyle.Ribbon,
                    EdgeRibbonSpec(
                        bandWidthDp = params.bandWidthDp.coerceIn(6f, 48f),
                        trailScale = params.trailScale.coerceIn(0f, 4f),
                        trailAlpha = params.trailAlpha.coerceIn(0f, 1f),
                        cornerRadiusDp = params.cornerRadiusDp,
                    ),
                    norm(
                        params.bandWidthDp,
                        FloatBallDefaults.AlertFlowBandWidthDpMin.toFloat(),
                        FloatBallDefaults.AlertFlowBandWidthDpMax.toFloat(),
                    ),
                )
                EdgePattern.Lightning -> Triple(
                    EdgeAlertStyle.Lightning,
                    EdgeLightningSpec(
                        strikeCount = params.strikeCount.coerceIn(1, 4),
                        cornerRadiusDp = params.cornerRadiusDp,
                        lineWidthDp = (params.bandWidthDp * 0.35f).coerceIn(1.5f, 8f),
                    ),
                    0.75f,
                )
            }
            AlertLayerPlan(
                style = style,
                cycles = cycles,
                cycleMillis = cycle,
                brightness = brightness,
                amount = amount,
                spec = spec,
                palette = palette,
                appPackage = appPackage,
                presetId = presetId,
                layerId = layerId,
            )
        }
        AlertKind.Particle -> particle?.let { params ->
            AlertLayerPlan(
                style = particleStyle(params),
                cycles = cycles,
                cycleMillis = cycle,
                brightness = (params.look.alpha * intensity).coerceIn(0.08f, 1f),
                amount = 0.5f,
                spec = ParticlesSpec(params = params),
                palette = palette,
                appPackage = appPackage,
                presetId = presetId,
                layerId = layerId,
            )
        }
        AlertKind.Ball -> ball?.let { params ->
            val pattern = params.pattern
            val style: AlertRenderStyle = when (pattern) {
                BallPattern.Ripple -> AnchorAlertStyle.Ripple
                BallPattern.Shockwave -> AnchorAlertStyle.Shockwave
                BallPattern.Burst -> AnchorAlertStyle.BallBurst
                BallPattern.Halo -> AnchorAlertStyle.BallHalo
                BallPattern.Spiral -> AnchorAlertStyle.Spiral
                BallPattern.Orbit3D -> AnchorAlertStyle.Orbit3D
                BallPattern.Ring -> AnchorAlertStyle.Ring
            }
            val spec = when (style) {
                AnchorAlertStyle.Ripple -> RippleSpec(
                    strokeWidthDp = params.strokeWidthDp,
                    haloScale = params.haloScale,
                    centerAtBall = params.centerAtBall,
                    travelPercent = params.travelPercent.coerceIn(40, 300),
                )
                AnchorAlertStyle.Shockwave -> ShockwaveSpec(
                    strokeWidthDp = params.strokeWidthDp.coerceIn(1f, 12f),
                    travelPercent = params.travelPercent.coerceIn(60, 300),
                )
                AnchorAlertStyle.BallBurst -> BallBurstSpec(
                    count = params.particleCount.coerceIn(12, 80),
                    particleSizeDp = params.particleSizeDp.coerceIn(1f, 8f),
                    spreadDp = (params.travelPercent * 1.6f).coerceIn(60f, 360f),
                )
                AnchorAlertStyle.BallHalo -> BallHaloSpec(
                    radiusPercent = params.travelPercent.coerceIn(60, 300),
                    haloScale = params.haloScale,
                )
                AnchorAlertStyle.Spiral -> BallSpiralSpec(
                    count = params.particleCount.coerceIn(8, 80),
                    particleSizeDp = params.particleSizeDp.coerceIn(1f, 8f),
                    travelPercent = params.travelPercent.coerceIn(40, 300),
                    turns = params.ringsPerCycle.coerceIn(1, 4).toFloat(),
                )
                AnchorAlertStyle.Orbit3D -> BallOrbit3DSpec(
                    count = params.particleCount.coerceIn(8, 80),
                    particleSizeDp = params.particleSizeDp.coerceIn(1f, 8f),
                    travelPercent = params.travelPercent.coerceIn(40, 300),
                    tilt = 0.55f,
                )
                else -> RingSpec(
                    strokeWidthDp = params.strokeWidthDp,
                    ringsPerCycle = params.ringsPerCycle.coerceIn(1, 4),
                    haloScale = params.haloScale,
                )
            }
            AlertLayerPlan(
                style = style,
                cycles = cycles,
                cycleMillis = cycle,
                brightness = brightness,
                amount = norm(
                    params.travelPercent.toFloat(),
                    FloatBallDefaults.AlertRingTravelMin.toFloat(),
                    FloatBallDefaults.AlertRingTravelMax.toFloat(),
                ),
                spec = spec,
                palette = palette,
                appPackage = appPackage,
                presetId = presetId,
                layerId = layerId,
            )
        }
        AlertKind.Capsule -> capsule?.let { params ->
            val pattern = params.pattern
            val style: AlertRenderStyle = when (pattern) {
                CapsulePattern.Pulse -> CapsuleAlertStyle.Pulse
                CapsulePattern.Shimmer -> CapsuleAlertStyle.Shimmer
                CapsulePattern.BreathOutline -> CapsuleAlertStyle.BreathOutline
                CapsulePattern.InnerSweep -> CapsuleAlertStyle.InnerSweep
                CapsulePattern.Flip3D -> CapsuleAlertStyle.Flip3D
                CapsulePattern.DualChase -> CapsuleAlertStyle.DualChase
            }
            val spec = when (style) {
                CapsuleAlertStyle.Pulse -> PulseSpec(
                    glowWidthDp = params.strokeWidthDp,
                    haloScale = 1f,
                )
                CapsuleAlertStyle.Shimmer -> ShimmerSpec(
                    strokeWidthDp = params.strokeWidthDp,
                    trailScale = params.trailScale,
                    trailAlpha = params.trailAlpha,
                    reverse = params.reverse,
                )
                CapsuleAlertStyle.BreathOutline -> BreathOutlineSpec(
                    strokeWidthDp = params.strokeWidthDp,
                    breatheScale = 1f + params.strokeWidthDp / 100f,
                )
                CapsuleAlertStyle.Flip3D -> Flip3DSpec(
                    strokeWidthDp = params.strokeWidthDp,
                    flipDegrees = params.flipDegrees.coerceIn(90, 360),
                )
                CapsuleAlertStyle.DualChase -> DualChaseSpec(
                    strokeWidthDp = params.strokeWidthDp,
                    bandWidth = params.bandWidthPercent.coerceIn(10, 60) / 100f,
                    trailScale = params.trailScale.coerceIn(0f, 4f),
                    trailAlpha = params.trailAlpha.coerceIn(0f, 1f),
                )
                else -> InnerSweepSpec(
                    bandWidth = params.bandWidthPercent.coerceIn(10, 80) / 100f,
                    reverse = params.reverse,
                )
            }
            val amount = when (style) {
                CapsuleAlertStyle.Shimmer -> norm(
                    params.bandWidthPercent.toFloat(),
                    FloatBallDefaults.AlertShimmerBandWidthMin.toFloat(),
                    FloatBallDefaults.AlertShimmerBandWidthMax.toFloat(),
                )
                CapsuleAlertStyle.InnerSweep -> norm(
                    params.bandWidthPercent.toFloat(),
                    FloatBallDefaults.AlertInnerSweepBandWidthMin.toFloat(),
                    FloatBallDefaults.AlertInnerSweepBandWidthMax.toFloat(),
                )
                else -> 0.5f
            }
            AlertLayerPlan(
                style = style,
                cycles = cycles,
                cycleMillis = cycle,
                brightness = brightness,
                amount = amount,
                spec = spec,
                palette = palette,
                appPackage = appPackage,
                presetId = presetId,
                layerId = layerId,
            )
        }
        AlertKind.Screen -> screen?.let { params ->
            val style = when (params.pattern) {
                ScreenPattern.Flash -> ScreenAlertStyle.Flash
                ScreenPattern.Vignette -> ScreenAlertStyle.Vignette
                ScreenPattern.ZoomPulse -> ScreenAlertStyle.ZoomPulse
                ScreenPattern.CenterBlackout -> ScreenAlertStyle.CenterBlackout
                ScreenPattern.CrtOff -> ScreenAlertStyle.CrtOff
                ScreenPattern.Shatter -> ScreenAlertStyle.Shatter
                ScreenPattern.Blinds -> ScreenAlertStyle.Blinds
                ScreenPattern.Iris -> ScreenAlertStyle.Iris
            }
            AlertLayerPlan(
                style = style,
                cycles = cycles,
                cycleMillis = cycle,
                brightness = (params.amplitude * intensity).coerceIn(0.08f, 1f),
                amount = 0.5f,
                spec = ScreenSpec(
                    pattern = style,
                    amplitude = params.amplitude,
                    zoomPercent = params.zoomPercent,
                    vignetteSoftness = params.vignetteSoftness,
                ),
                palette = palette,
                appPackage = appPackage,
                presetId = presetId,
                layerId = layerId,
            )
        }
        AlertKind.Reveal -> reveal?.let { params ->
            val title = content?.title.orEmpty().trim().take(RevealTitleMax)
            val body = content?.body.orEmpty().trim().take(RevealBodyMax)
            if (title.isEmpty() && body.isEmpty() && content?.iconPackage == null) {
                null
            } else {
                AlertLayerPlan(
                    style = if (params.pattern == RevealPattern.Icon) {
                        RevealAlertStyle.Icon
                    } else {
                        RevealAlertStyle.Text
                    },
                    cycles = cycles,
                    cycleMillis = cycle,
                    brightness = brightness,
                    amount = 0.5f,
                    spec = RevealSpec(
                        pattern = if (params.pattern == RevealPattern.Icon) {
                            RevealAlertStyle.Icon
                        } else {
                            RevealAlertStyle.Text
                        },
                        title = title,
                        body = body,
                        iconPackage = content?.iconPackage,
                        staggerPercent = params.staggerPercent,
                        scaleFrom = params.scaleFrom,
                        direction = params.maskDirection,
                    ),
                    palette = palette,
                    appPackage = appPackage,
                    presetId = presetId,
                    layerId = layerId,
                )
            }
        }
    }
}

/** 揭示文字上限：标题 24 / 正文 40（与通知路径口径一致）。 */
private const val RevealTitleMax = 24
private const val RevealBodyMax = 40

private fun paramEdgeFlow(
    params: hunoia.luno.config.model.EdgeParams,
): EdgeFlowSpec = EdgeFlowSpec(
    dualBand = params.dualBand,
    trailScale = params.trailScale.coerceIn(0f, 4f),
    dualColor = false,
    coreBand = 0.03f,
    hardness = params.hardness.coerceIn(1f, 4f),
    trailAlpha = params.trailAlpha.coerceIn(0f, 1f),
    cornerRadiusDp = params.cornerRadiusDp,
)

/** 粒子渲染样式（只决定种子与日志；参数全部来自发射器 / 运动 / 外观）。 */
private fun particleStyle(params: ParticleParams): ParticleAlertStyle = when (params.emitter.area) {
    ParticleArea.TopEdge -> ParticleAlertStyle.TopRain
    ParticleArea.BottomEdge -> ParticleAlertStyle.BottomFountain
    ParticleArea.ScreenRect ->
        if (params.motion.path != null) ParticleAlertStyle.Path else ParticleAlertStyle.Firefly
    ParticleArea.EdgePerimeter -> ParticleAlertStyle.Confetti
    ParticleArea.Point, ParticleArea.BallRing -> ParticleAlertStyle.Meteor
}

private fun norm(value: Float, min: Float, max: Float): Float {
    val span = max - min
    if (span <= 0f) return 0.5f
    return ((value - min) / span).coerceIn(0f, 1f)
}