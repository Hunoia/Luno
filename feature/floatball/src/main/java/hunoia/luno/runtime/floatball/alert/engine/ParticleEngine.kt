package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleDirection
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.ParticleMotionMode
import hunoia.luno.config.model.ParticlePath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 粒子运动引擎（纯 JVM，可单测）。
 *
 * 坐标系：屏幕高度归一化——`y ∈ 0..1`、`x ∈ 0..aspect`（aspect = 宽 / 高），
 * 速度单位是「屏高 / 秒」，加速度是「屏高 / 秒²」。渲染层只乘屏幕高度换算像素。
 *
 * 设计口径：
 * - 发射器负责初始位置 / 方向 / 初速；
 * - 六族方程组合成加速度与位置修正（弹道 / 风湍 / 轨道 / 摆动 / 路径 / 吸引反弹）；
 * - 路径族是**解析式**（进度 → 位置），不参与积分；其余族逐帧积分；
 * - 所有随机量来自显式 `Random`（种子由播放计划给出），保证同种子可复现。
 */
internal data class NormPoint(val x: Float, val y: Float)

internal data class ParticleState(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val age: Float,
    val life: Float,
    val delay: Float,
    val spinDeg: Float,
    val phase: Float,
    val seed: Int,
    /**
     * 是否曾经进入过屏幕可视区。
     *
     * 底部 / 顶部 / 屏缘的出生点在可视区外（[EdgeMargin]），反弹只对入过屏的粒子生效，
     * 否则第一帧就被夹回边界并反复反向，速度衰减到 0（底部喷泉曾经因此贴在底边一动不动）。
     */
    val entered: Boolean = false,
) {
    /** 寿命进度 0..1（延迟期内为负，渲染端自行跳过）。 */
    val progress: Float get() = if (life <= 0f) 1f else (age / life).coerceIn(0f, 1f)
}

/** 引擎的屏幕快照：渲染端按 [aspect] 换算，测试直接给固定值。 */
internal data class ParticleFrame(val aspect: Float)

// 归一口径的物理系数（调校集中在这里，不要散落到各调用点）。
private const val GravityAccel = 1.6f
private const val DragRate = 3f
private const val WindAccel = 0.8f
private const val TurbulenceAccel = 1.2f
private const val BrownianAccel = 1.4f
private const val OrbitRate = 0.6f
private const val SpiralRate = 0.5f
private const val AttractAccel = 1.2f
private const val SwayAccel = 0.9f
private const val SpinDegPerSecond = 720f
private const val VarianceAmplitude = 0.4f
private const val EdgeMargin = 0.06f

/**
 * 生成一个出生点：位置按 [ParticleEmitter.area] 取；[anchor] 只对 Point / BallRing 有意义。
 *
 * 烟花用「每次波次取一个出生点」把一簇粒子聚在同一锚点上，所以渲染层直接调用它，
 * 再把结果作为 [spawnParticle] 的 `origin` 传回去。
 */
internal fun spawnPoint(
    area: ParticleArea,
    frame: ParticleFrame,
    anchor: NormPoint?,
    random: Random,
): NormPoint {
    val center = anchor ?: NormPoint(frame.aspect / 2f, 0.5f)
    return when (area) {
        ParticleArea.TopEdge -> NormPoint(random.nextFloat() * frame.aspect, -EdgeMargin)
        ParticleArea.BottomEdge -> NormPoint(random.nextFloat() * frame.aspect, 1f + EdgeMargin)
        ParticleArea.ScreenRect ->
            NormPoint(random.nextFloat() * frame.aspect, random.nextFloat())
        ParticleArea.EdgePerimeter -> perimeterPoint(frame, random)
        ParticleArea.Point -> center
        ParticleArea.BallRing -> {
            val angle = random.nextFloat() * TwoPi
            val radius = 0.05f + random.nextFloat() * 0.06f
            NormPoint(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        }
    }
}

/**
 * 生成一颗粒子：位置按 [ParticleEmitter.area]（或显式 [origin]）取，
 * 方向按 [ParticleEmitter.direction] + 散开角，初速按 [ParticleEmitter.speed]。
 *
 * 烟花先升空再爆时，渲染层传 `origin = 顶点`、`center = 屏幕中心`，
 * 并把 `delay` 设成爆发时刻；引擎本身不需要知道「升空」这件事。
 */
internal fun spawnParticle(
    emitter: ParticleEmitter,
    frame: ParticleFrame,
    anchor: NormPoint?,
    random: Random,
    origin: NormPoint? = null,
    center: NormPoint? = null,
): ParticleState {
    val seed = random.nextInt()
    val screenCenter = NormPoint(frame.aspect / 2f, 0.5f)
    val focus = center ?: anchor ?: screenCenter
    val point = origin ?: spawnPoint(emitter.area, frame, anchor, random)
    val base = when (emitter.direction) {
        ParticleDirection.Fixed -> Math.toRadians(emitter.directionDeg.toDouble())
        ParticleDirection.Inward -> Math.atan2(
            (focus.y - point.y).toDouble(),
            (focus.x - point.x).toDouble(),
        )
        ParticleDirection.Outward -> Math.atan2(
            (point.y - focus.y).toDouble(),
            (point.x - focus.x).toDouble(),
        )
        ParticleDirection.Radial -> Double.NaN
    }
    val angle = if (base.isNaN()) {
        random.nextFloat() * TwoPi
    } else {
        (base + (random.nextFloat() - 0.5f) * Math.toRadians(emitter.spreadDeg.toDouble())).toFloat()
    }
    val speed = emitter.speed * (1f + (random.nextFloat() * 2f - 1f) * emitter.speedJitter)
    return ParticleState(
        x = point.x,
        y = point.y,
        vx = (cos(angle) * speed).toFloat(),
        vy = (sin(angle) * speed).toFloat(),
        age = 0f,
        life = 1f,
        // 延迟由渲染层按「发射方式 + 循环」统一算，这里不掺一份（旧实现会被整段覆盖）。
        delay = 0f,
        spinDeg = 0f,
        phase = random.nextFloat() * TwoPi,
        seed = seed,
        entered = false,
    )
}

/**
 * 推进一帧。
 *
 * [lifeSeconds] 用真实秒（由单循环时长换算），[progress] 用于摆动 / 自转等与寿命绑定的曲线。
 * 路径族会覆盖位置；其余族按加速度积分。
 */
internal fun advanceParticle(
    state: ParticleState,
    dt: Float,
    motion: ParticleMotion,
    frame: ParticleFrame,
    anchor: NormPoint?,
): ParticleState {
    if (dt <= 0f) return state
    val age = state.age + dt
    val progress = if (state.life <= 0f) 1f else (age / state.life).coerceIn(0f, 1f)
    // tilt 单独打开时也要让 spinDeg 走起来，否则 3D 翻面没有相位。
    val spinRate = maxOf(motion.spin, motion.tilt)

    if (motion.mode == ParticleMotionMode.Path) {
        // 路径族的行程由「初速 × 寿命 × 行程缩放」决定：速度旋钮对路径也有意义。
        val travel = (hypot(state.vx, state.vy) * state.life * motion.path.travelScale)
            .coerceIn(0.05f, 3.5f)
        val pos = pathPosition(
            path = motion.path.shape,
            progress = progress,
            start = NormPoint(state.x, state.y),
            direction = directionOf(state),
            travel = travel,
            aspect = frame.aspect,
        )
        return state.copy(
            x = pos.x,
            y = pos.y,
            age = age,
            spinDeg = state.spinDeg + spinRate * SpinDegPerSecond * dt,
            entered = state.entered || isInside(state.x, state.y, frame.aspect) ||
                isInside(pos.x, pos.y, frame.aspect),
        )
    }

    val free = motion.free
    val center = anchor ?: NormPoint(frame.aspect / 2f, 0.5f)
    var x = state.x
    var y = state.y
    var vx = state.vx
    var vy = state.vy

    // 轨道：绕锚点旋转（同时旋转位置与速度，半径保持不变）。
    if (free.orbit > 0f) {
        val dx = x - center.x
        val dy = y - center.y
        val omega = free.orbit * OrbitRate * TwoPi * dt
        val cs = cos(omega)
        val sn = sin(omega)
        x = center.x + dx * cs - dy * sn
        y = center.y + dx * sn + dy * cs
        val nvx = vx * cs - vy * sn
        val nvy = vx * sn + vy * cs
        vx = nvx
        vy = nvy
    }

    // 螺旋：向锚点收拢（与轨道叠加时形成卷吸）。
    if (free.spiral > 0f) {
        val dx = center.x - x
        val dy = center.y - y
        val dist = max(hypot(dx, dy), 1e-4f)
        val step = free.spiral * SpiralRate * dt
        x += dx / dist * step
        y += dy / dist * step
    }

    // 吸引：朝锚点加速。
    if (free.attract > 0f) {
        val dx = center.x - x
        val dy = center.y - y
        val dist = max(hypot(dx, dy), 1e-4f)
        vx += dx / dist * free.attract * AttractAccel * dt
        vy += dy / dist * free.attract * AttractAccel * dt
    }

    // 弹道：重力 + 阻力（终端速度）。
    vy += free.gravity * GravityAccel * dt
    if (free.drag > 0f) {
        val damp = exp(-free.drag * DragRate * dt)
        vx *= damp
        vy *= damp
    }

    // 风湍：水平风 + 湍流 / 布朗（哈希噪声，可复现）。
    vx += free.wind * WindAccel * dt
    if (free.turbulence > 0f) {
        vx += (noise01(state.seed, age * 2.1f) - 0.5f) * free.turbulence * TurbulenceAccel * dt
        vy += (noise01(state.seed xor 0x5F37, age * 1.7f) - 0.5f) *
            free.turbulence * TurbulenceAccel * dt
    }
    if (free.brownian > 0f) {
        vx += (noise01(state.seed xor 0x2C1B, age * 7.3f) - 0.5f) *
            free.brownian * BrownianAccel * dt
        vy += (noise01(state.seed xor 0x71A9, age * 6.1f) - 0.5f) *
            free.brownian * BrownianAccel * dt
    }

    // 摆动：与寿命绑定的正弦加速度（幅度按屏高归一化）。
    if (free.sway > 0f) {
        vx += cos(TwoPi * free.swayCycles * progress + state.phase) *
            free.sway * SwayAccel * dt
    }

    x += vx * dt
    y += vy * dt

    // 边界反弹：只有显式开启才拦截，否则允许飞出屏幕自然消失。
    // 出生点在屏外的粒子先入屏再参与反弹（否则会被夹在边界上反复反向、速度归零）。
    // 出生点可能在屏外，也可能一帧就跨过边界（大 dt）：前后两处都要认。
    val entered = state.entered || isInside(state.x, state.y, frame.aspect) ||
        isInside(x, y, frame.aspect)
    if (free.bounce > 0f && entered) {
        if (x < 0f) {
            x = 0f
            vx = -vx * free.bounce
        } else if (x > frame.aspect) {
            x = frame.aspect
            vx = -vx * free.bounce
        }
        if (y < 0f) {
            y = 0f
            vy = -vy * free.bounce
        } else if (y > 1f) {
            y = 1f
            vy = -vy * free.bounce
        }
    }

    return state.copy(
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        age = age,
        spinDeg = state.spinDeg + spinRate * SpinDegPerSecond * dt,
        entered = entered,
    )
}

/**
 * 路径族：进度 → 位置（解析式，独立于速度积分）。
 *
 * [travel] 是整条路径的归一化行程（屏高比例），由初速 × 寿命给出，所以速度旋钮
 * 对路径同样生效。Sine / ZigZag 只做横向摆动，纵向跟随 [direction] 的符号。
 */
internal fun pathPosition(
    path: ParticlePath,
    progress: Float,
    start: NormPoint,
    direction: NormPoint,
    travel: Float,
    aspect: Float,
): NormPoint {
    val u = progress.coerceIn(0f, 1f)
    val lateral = 0.08f * aspect / 2f
    val dirSign = if (direction.y < -0.01f) -1f else 1f
    return when (path) {
        ParticlePath.Sine -> NormPoint(
            x = start.x + sin(TwoPi * u * 2f) * lateral,
            y = start.y + dirSign * u * travel,
        )
        ParticlePath.ZigZag -> {
            // 三角波：从 0 起、峰值 +lateral、回 0、再 -lateral，端点在中心线上。
            val raw = u * 3f
            val seg = floor(raw)
            val t = raw - seg
            val tri = if (t < 0.5f) t * 2f else 2f - t * 2f
            val sign = if (seg.toInt() % 2 == 0) 1f else -1f
            NormPoint(
                x = start.x + sign * tri * lateral * 0.8f,
                y = start.y + dirSign * u * travel,
            )
        }
        ParticlePath.Arc -> NormPoint(
            x = start.x + direction.x * u * travel,
            y = start.y + direction.y * u * travel - 4f * 0.35f * u * (1f - u),
        )
        ParticlePath.Bezier -> {
            // 二次贝塞尔：起点 → 侧向控制点 → 终点（终点按方向与行程给出）。
            val end = NormPoint(
                x = start.x + direction.x * travel,
                y = start.y + direction.y * travel,
            )
            val ctrl = NormPoint(
                x = (start.x + end.x) / 2f - direction.y * 0.35f,
                y = (start.y + end.y) / 2f + direction.x * 0.35f,
            )
            val a = (1f - u) * (1f - u)
            val b = 2f * (1f - u) * u
            val c = u * u
            NormPoint(
                x = a * start.x + b * ctrl.x + c * end.x,
                y = a * start.y + b * ctrl.y + c * end.y,
            )
        }
    }
}

/** 弹道下的终端速度（供测试与调校参考）：v_t = g * G / (drag * D)。 */
internal fun terminalVelocity(gravity: Float, drag: Float): Float =
    if (drag <= 0f) Float.POSITIVE_INFINITY
    else gravity * GravityAccel / (drag * DragRate)

/** 3D 翻面的横向缩放：|cos(自转角)|，下限避免完全消失。 */
internal fun tiltScale(spinDeg: Float, minScale: Float = 0.15f): Float =
    abs(cos(Math.toRadians(spinDeg.toDouble()))).toFloat().coerceAtLeast(minScale)

private const val TwoPi = (Math.PI * 2.0).toFloat()

/** 是否在可视区（x 归一化到 0..aspect，y 0..1）。 */
private fun isInside(x: Float, y: Float, aspect: Float): Boolean =
    x >= 0f && x <= aspect && y >= 0f && y <= 1f

private fun directionOf(state: ParticleState): NormPoint {
    val len = max(hypot(state.vx, state.vy), 1e-4f)
    return NormPoint(state.vx / len, state.vy / len)
}

private fun perimeterPoint(frame: ParticleFrame, random: Random): NormPoint {
    val side = random.nextInt(4)
    return when (side) {
        0 -> NormPoint(random.nextFloat() * frame.aspect, -EdgeMargin)
        1 -> NormPoint(random.nextFloat() * frame.aspect, 1f + EdgeMargin)
        2 -> NormPoint(-EdgeMargin, random.nextFloat())
        else -> NormPoint(frame.aspect + EdgeMargin, random.nextFloat())
    }
}

/**
 * 哈希噪声：同一 (seed, t) 恒返回同一 0..1 值，跨帧 / 跨设备一致。
 * 用 sin 散列而不是 Random，保证「同种子可复现」且不推进全局随机状态。
 */
internal fun noise01(seed: Int, t: Float): Float {
    val a = sin(t * 12.9898f + seed * 0.0013f) * 43758.5453f
    return a - floor(a)
}
/**
 * 粒度方差：单颗粒子的尺寸 / alpha 随机系数（1 ± amp），[random01] 传 0..1。
 * 与旧 `ParticlePhysics.varianceScale` 同口径，收进引擎后由粒子层直接复用。
 */
internal fun varianceScale(variance: Float, random01: Float): Float {
    val amp = variance.coerceIn(0f, 1f) * VarianceAmplitude
    return 1f + (random01 * 2f - 1f) * amp
}
