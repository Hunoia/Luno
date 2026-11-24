package hunoia.luno.runtime.floatball.alert.preset

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.BallParams
import hunoia.luno.config.model.BallPattern
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.CapsulePattern
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.EdgePattern
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.ParticlePath
import hunoia.luno.config.model.RevealParams
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.config.model.ScreenParams
import hunoia.luno.config.model.ScreenPattern
import hunoia.luno.config.model.matchesKind

/**
 * 内置预设：**全部单层素材**（代码常量、不进备份，数量不设硬上限）。
 *
 * 组合由用户在预设库里自己拼：内置只提供「一层」的可信观感，每个名字必须
 * 对应真实绘制形态（不能只改参数就叫新名字）。
 *
 * 名称：内置预设的 [AlertPreset.name] 只是兜底展示；设置页按 id 取本地化文案
 * （`float_ball_preset_*`），用户预设才用自身名称。
 */
internal object BuiltinAlertPresets {

    val all: List<AlertPreset> = listOf(
        // ---------------------------------------------------------------- 屏缘
        preset(
            id = "builtin_edge_glow",
            name = "屏幕边缘呼吸光",
            layer = edge(
                pattern = EdgePattern.Glow,
                bandWidthDp = 12f,
                hardness = 2f,
                timing = AlertTiming(baseMillis = 1400),
            ),
        ),
        preset(
            id = "builtin_edge_flow",
            name = "屏幕边缘流光",
            layer = edge(
                pattern = EdgePattern.Flow,
                bandWidthDp = 18f,
                hardness = 2.2f,
                dualBand = false,
                trailScale = 2.5f,
                trailAlpha = 0.45f,
                timing = AlertTiming(baseMillis = 1600),
            ),
        ),
        preset(
            id = "builtin_edge_ripple",
            name = "边缘波纹",
            layer = edge(
                pattern = EdgePattern.Ripple,
                bandWidthDp = 6f,
                crests = 3,
                timing = AlertTiming(baseMillis = 1300),
            ),
        ),
        preset(
            id = "builtin_edge_comet",
            name = "屏缘彗星",
            layer = edge(
                pattern = EdgePattern.Comet,
                bandWidthDp = 6f,
                trailScale = 3f,
                trailAlpha = 0.55f,
                timing = AlertTiming(baseMillis = 900),
            ),
        ),

        // ---------------------------------------------------------------- 粒子
        preset(
            id = "builtin_particle_rain",
            name = "顶部粒子掉落",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.TopEdge,
                    count = 60,
                    directionDeg = 90f,
                    spreadDeg = 24f,
                    speed = 0.6f,
                    speedJitter = 0.25f,
                ),
                motion = ParticleMotion(gravity = 1f, drag = 0.3f, sway = 0.2f),
                look = ParticleLook(shape = AlertParticleShape.Dot),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1000),
            ),
        ),
        preset(
            id = "builtin_particle_fountain",
            name = "底部喷泉",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.BottomEdge,
                    count = 46,
                    directionDeg = 270f,
                    spreadDeg = 22f,
                    speed = 0.85f,
                    speedJitter = 0.35f,
                ),
                motion = ParticleMotion(gravity = 2f, drag = 0.2f, bounce = 0.2f),
                look = ParticleLook(shape = AlertParticleShape.Dot),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1100),
            ),
        ),
        preset(
            id = "builtin_particle_meteor",
            name = "流星",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.TopEdge,
                    count = 18,
                    directionDeg = 135f,
                    spreadDeg = 8f,
                    speed = 1.3f,
                    speedJitter = 0.2f,
                ),
                motion = ParticleMotion(gravity = 0.2f, drag = 0.1f, path = ParticlePath.Sine),
                look = ParticleLook(
                    shape = AlertParticleShape.Line,
                    trailDp = 48f,
                    trailAlpha = 0.5f,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 900),
            ),
        ),
        preset(
            id = "builtin_particle_confetti",
            name = "彩带",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.TopEdge,
                    count = 52,
                    directionDeg = 90f,
                    spreadDeg = 40f,
                    speed = 0.35f,
                    speedJitter = 0.4f,
                ),
                motion = ParticleMotion(
                    gravity = 0.7f,
                    drag = 0.35f,
                    sway = 0.4f,
                    spin = 0.8f,
                    tilt = 0.8f,
                ),
                look = ParticleLook(
                    shape = AlertParticleShape.Square,
                    sizeDp = 8f,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1200),
            ),
        ),
        preset(
            id = "builtin_particle_firefly",
            name = "萤火",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.ScreenRect,
                    count = 42,
                    directionDeg = 90f,
                    spreadDeg = 360f,
                    speed = 0.05f,
                    speedJitter = 1f,
                ),
                motion = ParticleMotion(
                    gravity = 0f,
                    drag = 0.6f,
                    brownian = 0.6f,
                    turbulence = 0.4f,
                    sway = 0.5f,
                ),
                look = ParticleLook(
                    alpha = 0.9f,
                    haloStyle = ParticleHaloStyle.Soft,
                    haloAlpha = 0.5f,
                    haloScale = 2.4f,
                    blink = true,
                    blinkCycles = 2.5f,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1500),
            ),
        ),

        // ---------------------------------------------------------------- 球体
        preset(
            id = "builtin_ball_ring",
            name = "球光环",
            layer = ball(
                pattern = BallPattern.Ring,
                travelPercent = 95,
                strokeWidthDp = 3f,
                haloScale = 1f,
                timing = AlertTiming(baseMillis = 1000),
            ),
        ),
        preset(
            id = "builtin_ball_ripple",
            name = "全屏涟漪",
            layer = ball(
                pattern = BallPattern.Ripple,
                travelPercent = 140,
                strokeWidthDp = 2f,
                centerAtBall = true,
                timing = AlertTiming(baseMillis = 1000),
            ),
        ),
        preset(
            id = "builtin_ball_burst",
            name = "球心爆发",
            layer = ball(
                pattern = BallPattern.Burst,
                travelPercent = 70,
                particleCount = 20,
                particleSizeDp = 4f,
                timing = AlertTiming(baseMillis = 1000),
            ),
        ),
        preset(
            id = "builtin_ball_shockwave",
            name = "冲击波",
            layer = ball(
                pattern = BallPattern.Shockwave,
                travelPercent = 160,
                strokeWidthDp = 4f,
                haloScale = 1.4f,
                timing = AlertTiming(baseMillis = 900),
            ),
        ),
        preset(
            id = "builtin_capsule_pulse",
            name = "胶囊脉冲",
            layer = capsule(
                pattern = CapsulePattern.Pulse,
                strokeWidthDp = 1.5f,
                timing = AlertTiming(baseMillis = 1000),
            ),
        ),

        // ---------------------------------------------------------------- 全屏
        preset(
            id = "builtin_screen_flash",
            name = "全屏闪光",
            layer = screen(
                pattern = ScreenPattern.Flash,
                amplitude = 0.55f,
                timing = AlertTiming(baseMillis = 600),
            ),
        ),
        preset(
            id = "builtin_screen_vignette",
            name = "暗角呼吸",
            layer = screen(
                pattern = ScreenPattern.Vignette,
                amplitude = 0.45f,
                vignetteSoftness = 0.6f,
                timing = AlertTiming(baseMillis = 900),
            ),
        ),
        preset(
            id = "builtin_screen_zoom",
            name = "缩放脉冲",
            layer = screen(
                pattern = ScreenPattern.ZoomPulse,
                amplitude = 0.5f,
                zoomPercent = 8,
                timing = AlertTiming(baseMillis = 700),
            ),
        ),

        // ---------------------------------------------------------------- 路径
        preset(
            id = "builtin_path_comet",
            name = "穿越彗星",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.ScreenRect,
                    count = 10,
                    directionDeg = 0f,
                    spreadDeg = 360f,
                    speed = 1.2f,
                    speedJitter = 0.3f,
                    delaySpread = 0.5f,
                ),
                motion = ParticleMotion(
                    gravity = 0f,
                    drag = 0f,
                    path = ParticlePath.Bezier,
                ),
                look = ParticleLook(
                    shape = AlertParticleShape.Line,
                    trailDp = 64f,
                    trailAlpha = 0.6f,
                    blendAdd = true,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1100),
            ),
        ),
        preset(
            id = "builtin_path_orbit",
            name = "轨道环绕",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.Point,
                    count = 26,
                    directionDeg = 0f,
                    spreadDeg = 360f,
                    speed = 0.2f,
                    speedJitter = 0.1f,
                ),
                motion = ParticleMotion(
                    gravity = 0f,
                    drag = 0f,
                    orbit = 1f,
                    spin = 1f,
                ),
                look = ParticleLook(
                    shape = AlertParticleShape.Triangle,
                    sizeDp = 6f,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1400),
            ),
        ),
        preset(
            id = "builtin_path_spiral",
            name = "螺旋上升",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.Point,
                    count = 30,
                    directionDeg = 270f,
                    spreadDeg = 60f,
                    speed = 0.5f,
                    speedJitter = 0.2f,
                ),
                motion = ParticleMotion(
                    gravity = 0.2f,
                    drag = 0.3f,
                    orbit = 0.6f,
                    spiral = 1f,
                ),
                look = ParticleLook(
                    shape = AlertParticleShape.Star,
                    sizeDp = 5f,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1300),
            ),
        ),

        // ---------------------------------------------------------------- Step 2 新形态

        preset(
            id = "builtin_edge_ribbon",
            name = "屏缘缎带",
            layer = edge(
                pattern = EdgePattern.Ribbon,
                bandWidthDp = 22f,
                trailScale = 3f,
                trailAlpha = 0.55f,
                timing = AlertTiming(baseMillis = 1500),
            ),
        ),
        preset(
            id = "builtin_edge_lightning",
            name = "屏缘闪电",
            layer = edge(
                pattern = EdgePattern.Lightning,
                bandWidthDp = 8f,
                strikeCount = 2,
                timing = AlertTiming(baseMillis = 800),
            ),
        ),
        preset(
            id = "builtin_ball_spiral",
            name = "螺旋卷吸",
            layer = ball(
                pattern = BallPattern.Spiral,
                travelPercent = 180,
                particleCount = 36,
                particleSizeDp = 3f,
                ringsPerCycle = 2,
                timing = AlertTiming(baseMillis = 1200),
            ),
        ),
        preset(
            id = "builtin_ball_orbit",
            name = "3D 环绕",
            layer = ball(
                pattern = BallPattern.Orbit3D,
                travelPercent = 150,
                particleCount = 28,
                particleSizeDp = 3.5f,
                timing = AlertTiming(baseMillis = 1400),
            ),
        ),
        preset(
            id = "builtin_capsule_flip",
            name = "胶囊 3D 翻转",
            layer = capsule(
                pattern = CapsulePattern.Flip3D,
                strokeWidthDp = 1.5f,
                flipDegrees = 180,
                timing = AlertTiming(baseMillis = 1100),
            ),
        ),
        preset(
            id = "builtin_capsule_chase",
            name = "双色追逐",
            layer = capsule(
                pattern = CapsulePattern.DualChase,
                strokeWidthDp = 1.5f,
                bandWidthPercent = 22,
                trailScale = 2f,
                trailAlpha = 0.4f,
                timing = AlertTiming(baseMillis = 1200),
            ),
        ),
        preset(
            id = "builtin_particle_petals",
            name = "花瓣飘落",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.TopEdge,
                    count = 40,
                    directionDeg = 90f,
                    spreadDeg = 50f,
                    speed = 0.35f,
                    speedJitter = 0.45f,
                    delaySpread = 0.8f,
                ),
                motion = ParticleMotion(
                    gravity = 0.5f,
                    drag = 0.45f,
                    wind = 0.15f,
                    sway = 0.5f,
                    swayCycles = 2f,
                    spin = 0.6f,
                ),
                look = ParticleLook(
                    shape = AlertParticleShape.Petal,
                    sizeDp = 12f,
                    sizeJitter = 0.35f,
                    alpha = 0.85f,
                    rotate = true,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1500),
            ),
        ),
        preset(
            id = "builtin_particle_snow",
            name = "雪花",
            layer = particle(
                emitter = ParticleEmitter(
                    area = ParticleArea.TopEdge,
                    count = 44,
                    directionDeg = 90f,
                    spreadDeg = 40f,
                    speed = 0.3f,
                    speedJitter = 0.35f,
                    delaySpread = 0.9f,
                ),
                motion = ParticleMotion(
                    gravity = 0.35f,
                    drag = 0.5f,
                    wind = -0.1f,
                    sway = 0.35f,
                    swayCycles = 1.5f,
                    spin = 0.35f,
                ),
                look = ParticleLook(
                    shape = AlertParticleShape.Snowflake,
                    sizeDp = 10f,
                    sizeJitter = 0.4f,
                    alpha = 0.9f,
                ),
                color = AlertColorRef(),
                timing = AlertTiming(baseMillis = 1600),
            ),
        ),
        preset(
            id = "builtin_reveal_text",
            name = "文字揭示",
            layer = reveal(
                pattern = RevealPattern.Text,
                timing = AlertTiming(baseMillis = 900),
            ),
        ),
        preset(
            id = "builtin_reveal_icon",
            name = "图标揭示",
            layer = reveal(
                pattern = RevealPattern.Icon,
                timing = AlertTiming(baseMillis = 800),
            ),
        ),
    )

    /** 内置总数（门禁测试用）。 */
    const val Count: Int = 30

    fun find(id: String): AlertPreset? = all.firstOrNull { it.id == id }

    // ------------------------------------------------------------ 构造助手

    private fun preset(
        id: String,
        name: String,
        layer: AlertLayer,
    ): AlertPreset = AlertPreset(
        id = id,
        name = name,
        builtin = true,
        layers = listOf(
            layer.copy(
                id = "${id}_l0",
                name = name,
                sourcePresetId = id,
            ),
        ),
    ).also { check(layer.matchesKind()) { "preset $id layer kind mismatch" } }

    private fun edge(
        pattern: EdgePattern,
        bandWidthDp: Float,
        hardness: Float = 2f,
        dualBand: Boolean = false,
        trailScale: Float = 2f,
        trailAlpha: Float = 0.4f,
        crests: Int = 3,
        strikeCount: Int = 1,
        timing: AlertTiming,
    ): AlertLayer = AlertLayer(
        id = "",
        kind = AlertKind.Edge,
        timing = timing,
        color = AlertColorRef(),
        edge = EdgeParams(
            pattern = pattern,
            bandWidthDp = bandWidthDp,
            hardness = hardness,
            dualBand = dualBand,
            trailScale = trailScale,
            trailAlpha = trailAlpha,
            crests = crests,
            strikeCount = strikeCount,
        ),
    )

    private fun particle(
        emitter: ParticleEmitter,
        motion: ParticleMotion,
        look: ParticleLook,
        color: AlertColorRef,
        timing: AlertTiming,
    ): AlertLayer = AlertLayer(
        id = "",
        kind = AlertKind.Particle,
        timing = timing,
        color = color,
        particle = ParticleParams(emitter = emitter, motion = motion, look = look),
    )

    private fun ball(
        pattern: BallPattern,
        travelPercent: Int,
        strokeWidthDp: Float = 3f,
        haloScale: Float = 1f,
        centerAtBall: Boolean = true,
        particleCount: Int = 18,
        particleSizeDp: Float = 4f,
        ringsPerCycle: Int = 1,
        timing: AlertTiming,
    ): AlertLayer = AlertLayer(
        id = "",
        kind = AlertKind.Ball,
        timing = timing,
        color = AlertColorRef(),
        ball = BallParams(
            pattern = pattern,
            travelPercent = travelPercent,
            strokeWidthDp = strokeWidthDp,
            haloScale = haloScale,
            centerAtBall = centerAtBall,
            particleCount = particleCount,
            particleSizeDp = particleSizeDp,
            ringsPerCycle = ringsPerCycle,
        ),
    )

    private fun capsule(
        pattern: CapsulePattern,
        strokeWidthDp: Float,
        bandWidthPercent: Int = 25,
        trailScale: Float = 2f,
        trailAlpha: Float = 0.4f,
        flipDegrees: Int = 180,
        timing: AlertTiming,
    ): AlertLayer = AlertLayer(
        id = "",
        kind = AlertKind.Capsule,
        timing = timing,
        color = AlertColorRef(),
        capsule = CapsuleParams(
            pattern = pattern,
            strokeWidthDp = strokeWidthDp,
            bandWidthPercent = bandWidthPercent,
            trailScale = trailScale,
            trailAlpha = trailAlpha,
            flipDegrees = flipDegrees,
        ),
    )

    private fun reveal(
        pattern: RevealPattern,
        timing: AlertTiming,
    ): AlertLayer = AlertLayer(
        id = "",
        kind = AlertKind.Reveal,
        timing = timing,
        color = AlertColorRef(),
        reveal = RevealParams(pattern = pattern),
    )

    private fun screen(
        pattern: ScreenPattern,
        amplitude: Float,
        vignetteSoftness: Float = 0.5f,
        zoomPercent: Int = 8,
        timing: AlertTiming,
    ): AlertLayer = AlertLayer(
        id = "",
        kind = AlertKind.Screen,
        timing = timing,
        color = AlertColorRef(),
        screen = ScreenParams(
            pattern = pattern,
            amplitude = amplitude,
            zoomPercent = zoomPercent,
            vignetteSoftness = vignetteSoftness,
        ),
    )
}