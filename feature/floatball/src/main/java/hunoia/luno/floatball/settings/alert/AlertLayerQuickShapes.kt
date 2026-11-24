package hunoia.luno.floatball.settings.alert

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleCluster
import hunoia.luno.config.model.ParticleDirection
import hunoia.luno.config.model.ParticleEmission
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleFreeMotion
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoDropdownRow

/**
 * 粒子快速形状：下拉选中即应用的一套参数。
 *
 * 不是枚举、不落盘、不新增绘制代码——点一下把 [ParticleParams] 换成一套能直接看的
 * 参数组合；用户随后可以继续调任何滑杆。它没有「当前值」：应用完参数就变成了普通
 * 参数，所以标题固定显示「选择配方」，不会假装自己是个持久状态。
 */
@Composable
internal fun ParticleQuickShapeRow(
    shape: Shape,
    onPick: (ParticleParams, QuickTiming) -> Unit,
) {
    val presets = listOf(
        QuickParticlePreset(
            R.string.float_ball_layer_quick_rain,
            rainPreset(),
            QuickTiming(cycles = 3),
        ),
        QuickParticlePreset(R.string.float_ball_layer_quick_fountain, fountainPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_meteor, meteorPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_confetti, confettiPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_firefly, fireflyPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_edge_firework, edgeFireworkPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_sky_firework, skyFireworkPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_ball_burst, ballBurstPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_orbit, orbitPreset()),
    )
    val placeholder = stringResource(R.string.float_ball_layer_quick_pick)
    val labels = listOf(placeholder) + presets.map { stringResource(it.labelRes) }
    LunoDropdownRow(
        title = stringResource(R.string.float_ball_layer_quick_shapes_action),
        options = labels,
        // 固定停在占位项：选中即应用，然后回到占位，不假装记得上次配方。
        selectedIndex = 0,
        shape = shape,
        onSelect = { index ->
            if (index > 0) {
                val preset = presets[index - 1]
                onPick(preset.params, preset.timing)
            }
        },
    )
}

/**
 * 配方附带的时序覆盖：只有雨这种「必须播够一段才像」的配方才用得到。
 * 其余配方保持层当前的时长 / 循环数不变。
 */
internal data class QuickTiming(val baseMillis: Int? = null, val cycles: Int? = null)

private class QuickParticlePreset(
    val labelRes: Int,
    val params: ParticleParams,
    val timing: QuickTiming = QuickTiming(),
)

/**
 * 雨：**连续**发射，每颗只活一小段，前后接续才是雨。
 *
 * 爆发模式会把整批粒子挤在「循环减寿命」的窗口里，70 颗几乎同刻下落——那是一片
 * 同时掉下来的点，读起来和雨无关。这里用 Stream + 0.32 寿命，让整段时长一直有雨丝；
 * 初速 2.6 屏高/秒 保证单颗在寿命内走完大半屏，而不是在半空淡出。
 */
private fun rainPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.TopEdge,
        count = 110,
        emission = ParticleEmission.Stream,
        direction = ParticleDirection.Fixed,
        directionDeg = 90f,
        spreadDeg = 6f,
        // 雨丝要在一段寿命内走完大半屏：低初速 + 重力只能走 10% 屏高，读起来是「悬停」。
        speed = 2.6f,
        speedJitter = 0.3f,
        delaySpread = 1f,
        lifeShare = 0.32f,
    ),
    motion = ParticleMotion(
        free = ParticleFreeMotion(gravity = 0.3f, drag = 0f, wind = 0.03f, sway = 0f),
    ),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 2.2f,
        sizeJitter = 0.45f,
        alpha = 0.7f,
    ),
)

private fun fountainPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.BottomEdge,
        count = 50,
        direction = ParticleDirection.Fixed,
        directionDeg = 270f,
        spreadDeg = 26f,
        speed = 1.5f,
        speedJitter = 0.25f,
        delaySpread = 0.35f,
        lifeShare = 0.7f,
    ),
    motion = ParticleMotion(free = ParticleFreeMotion(gravity = 1f, drag = 0.15f)),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 3f,
        sizeJitter = 0.3f,
        alpha = 0.85f,
        trailDp = 4f,
    ),
)

private fun meteorPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.TopEdge,
        count = 18,
        direction = ParticleDirection.Fixed,
        directionDeg = 45f,
        spreadDeg = 0f,
        speed = 0.9f,
        speedJitter = 0.15f,
        delaySpread = 0.6f,
        lifeShare = 0.8f,
    ),
    motion = ParticleMotion(free = ParticleFreeMotion(gravity = 0f, drag = 0.1f)),
    look = ParticleLook(
        shape = AlertParticleShape.Line,
        sizeDp = 2f,
        alpha = 0.9f,
        trailDp = 30f,
        trailAlpha = 0.6f,
        blendAdd = true,
    ),
)

private fun confettiPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.TopEdge,
        count = 80,
        direction = ParticleDirection.Fixed,
        directionDeg = 90f,
        spreadDeg = 40f,
        speed = 0.3f,
        speedJitter = 0.5f,
        delaySpread = 0.5f,
        lifeShare = 0.9f,
    ),
    motion = ParticleMotion(
        spin = 1.5f,
        tilt = 0.8f,
        free = ParticleFreeMotion(
            gravity = 0.8f,
            drag = 0.25f,
            wind = 0.1f,
        ),
    ),
    look = ParticleLook(
        shape = AlertParticleShape.Square,
        sizeDp = 6f,
        sizeJitter = 0.4f,
        alpha = 0.9f,
    ),
)

private fun fireflyPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.ScreenRect,
        count = 45,
        direction = ParticleDirection.Radial,
        speed = 0.06f,
        speedJitter = 0.8f,
        delaySpread = 0.8f,
        lifeShare = 0.9f,
    ),
    motion = ParticleMotion(
        free = ParticleFreeMotion(
            gravity = -0.05f,
            drag = 0.8f,
            turbulence = 0.5f,
            brownian = 0.2f,
        ),
    ),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 3f,
        sizeJitter = 0.3f,
        alpha = 0.9f,
        haloStyle = ParticleHaloStyle.Soft,
        haloAlpha = 0.4f,
        haloScale = 2.2f,
        blink = true,
        blinkCycles = 2.5f,
    ),
)

private fun edgeFireworkPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.EdgePerimeter,
        count = 14,
        direction = ParticleDirection.Inward,
        spreadDeg = 150f,
        speed = 0.7f,
        speedJitter = 0.4f,
        delaySpread = 0.2f,
        lifeShare = 0.55f,
    ),
    cluster = ParticleCluster(anchors = 1),
    motion = ParticleMotion(free = ParticleFreeMotion(gravity = 0.5f, drag = 0.5f)),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 3f,
        sizeJitter = 0.3f,
        alpha = 0.9f,
        trailDp = 8f,
        trailAlpha = 0.45f,
        sizeShrink = 0.5f,
        colorPerWave = true,
        blendAdd = true,
    ),
)

private fun skyFireworkPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.BottomEdge,
        count = 32,
        direction = ParticleDirection.Radial,
        speed = 0.65f,
        speedJitter = 0.45f,
        delaySpread = 0.15f,
        lifeShare = 0.5f,
    ),
    cluster = ParticleCluster(anchors = 1, launchHeight = 0.55f, launchShare = 0.45f),
    motion = ParticleMotion(free = ParticleFreeMotion(gravity = 1f, drag = 0.35f)),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 3f,
        sizeJitter = 0.3f,
        alpha = 0.9f,
        trailDp = 10f,
        trailAlpha = 0.5f,
        blink = true,
        blinkCycles = 2.5f,
        sizeShrink = 0.6f,
        colorPerWave = true,
        blendAdd = true,
    ),
)

private fun ballBurstPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.Point,
        count = 55,
        direction = ParticleDirection.Radial,
        speed = 0.7f,
        speedJitter = 0.5f,
        delaySpread = 0.1f,
        lifeShare = 0.8f,
    ),
    cluster = ParticleCluster(anchors = 1),
    motion = ParticleMotion(free = ParticleFreeMotion(gravity = 0f, drag = 0.6f)),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 3f,
        sizeJitter = 0.3f,
        alpha = 0.9f,
        trailDp = 5f,
        trailAlpha = 0.4f,
        sizeShrink = 0.4f,
        colorPerWave = true,
        blendAdd = true,
    ),
)

private fun orbitPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.BallRing,
        count = 40,
        direction = ParticleDirection.Radial,
        speed = 0.08f,
        speedJitter = 0.3f,
        delaySpread = 0.4f,
        lifeShare = 0.9f,
    ),
    motion = ParticleMotion(
        free = ParticleFreeMotion(
            gravity = 0f,
            drag = 0.2f,
            orbit = 0.6f,
            spiral = 0.15f,
        ),
    ),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 3f,
        sizeJitter = 0.25f,
        alpha = 0.85f,
        haloStyle = ParticleHaloStyle.Soft,
        haloAlpha = 0.35f,
        haloScale = 2f,
        trailDp = 6f,
    ),
)
