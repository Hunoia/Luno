package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleDirection
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.PressFilterChip

/**
 * 粒子快速形状：一排只写参数的按钮。
 *
 * 不是枚举、不落盘、不新增绘制代码——点一下把 [ParticleParams] 换成一套能直接看的
 * 参数组合；用户随后可以继续调任何滑杆。屏缘烟花与升空烟花是两条不同配方：
 * 前者原地一簇一爆，后者先升空再在顶点爆开。
 */
@Composable
internal fun ParticleQuickShapes(onPick: (ParticleParams) -> Unit) {
    val presets = listOf(
        QuickParticlePreset(R.string.float_ball_layer_quick_rain, rainPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_fountain, fountainPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_meteor, meteorPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_confetti, confettiPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_firefly, fireflyPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_edge_firework, edgeFireworkPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_sky_firework, skyFireworkPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_ball_burst, ballBurstPreset()),
        QuickParticlePreset(R.string.float_ball_layer_quick_orbit, orbitPreset()),
    )
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(presets.size) { index ->
            val preset = presets[index]
            PressFilterChip(
                selected = false,
                onClick = { onPick(preset.params) },
                label = { Text(stringResource(preset.labelRes)) },
            )
        }
    }
}

private class QuickParticlePreset(val labelRes: Int, val params: ParticleParams)

private fun rainPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.TopEdge,
        count = 70,
        directionMode = ParticleDirection.Fixed,
        directionDeg = 90f,
        spreadDeg = 16f,
        speed = 0.12f,
        speedJitter = 0.5f,
        delaySpread = 0.5f,
        lifeShare = 0.85f,
    ),
    motion = ParticleMotion(gravity = 1f, drag = 0.05f, wind = 0.05f),
    look = ParticleLook(
        shape = AlertParticleShape.Dot,
        sizeDp = 2.4f,
        sizeJitter = 0.4f,
        alpha = 0.75f,
    ),
)

private fun fountainPreset(): ParticleParams = ParticleParams(
    emitter = ParticleEmitter(
        area = ParticleArea.BottomEdge,
        count = 50,
        directionMode = ParticleDirection.Fixed,
        directionDeg = 270f,
        spreadDeg = 26f,
        speed = 1.5f,
        speedJitter = 0.25f,
        delaySpread = 0.35f,
        lifeShare = 0.7f,
    ),
    motion = ParticleMotion(gravity = 1f, drag = 0.15f),
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
        directionMode = ParticleDirection.Fixed,
        directionDeg = 45f,
        spreadDeg = 0f,
        speed = 0.9f,
        speedJitter = 0.15f,
        delaySpread = 0.6f,
        lifeShare = 0.8f,
    ),
    motion = ParticleMotion(gravity = 0f, drag = 0.1f, path = null),
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
        directionMode = ParticleDirection.Fixed,
        directionDeg = 90f,
        spreadDeg = 40f,
        speed = 0.3f,
        speedJitter = 0.5f,
        delaySpread = 0.5f,
        lifeShare = 0.9f,
    ),
    motion = ParticleMotion(gravity = 0.8f, drag = 0.25f, wind = 0.1f, spin = 1.5f, tilt = 0.8f),
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
        directionMode = ParticleDirection.Radial,
        speed = 0.06f,
        speedJitter = 0.8f,
        delaySpread = 0.8f,
        lifeShare = 0.9f,
    ),
    motion = ParticleMotion(
        gravity = -0.05f,
        drag = 0.8f,
        turbulence = 0.5f,
        brownian = 0.2f,
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
        waves = 4,
        anchorPerWave = true,
        directionMode = ParticleDirection.Inward,
        spreadDeg = 150f,
        speed = 0.7f,
        speedJitter = 0.4f,
        delaySpread = 0.2f,
        lifeShare = 0.55f,
    ),
    motion = ParticleMotion(gravity = 0.5f, drag = 0.5f),
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
        waves = 3,
        anchorPerWave = true,
        directionMode = ParticleDirection.Radial,
        launchHeight = 0.55f,
        launchShare = 0.45f,
        speed = 0.65f,
        speedJitter = 0.45f,
        delaySpread = 0.15f,
        lifeShare = 0.5f,
    ),
    motion = ParticleMotion(gravity = 1f, drag = 0.35f),
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
        directionMode = ParticleDirection.Radial,
        anchorPerWave = true,
        speed = 0.7f,
        speedJitter = 0.5f,
        delaySpread = 0.1f,
        lifeShare = 0.8f,
    ),
    motion = ParticleMotion(gravity = 0f, drag = 0.6f),
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
        directionMode = ParticleDirection.Radial,
        speed = 0.08f,
        speedJitter = 0.3f,
        delaySpread = 0.4f,
        lifeShare = 0.9f,
    ),
    motion = ParticleMotion(
        gravity = 0f,
        drag = 0.2f,
        orbit = 0.6f,
        spiral = 0.15f,
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
