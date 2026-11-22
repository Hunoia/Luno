package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertEffect
import hunoia.luno.config.model.NotificationAlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到达提示动效的决策口径：场景抑制全部通过才播；配置到播放计划的夹取与归一化
 * （含速度倍率对家族基准时长的折算与各效果专属 spec 的透传）。
 *
 * 触发语义（需求确认）：仅新通知到达时播一次；熄屏 / 锁屏 / 勿扰一律不播（全屏沉浸
 * 判定按用户决策不做），胶囊可见与播放中合并不在此判（分别由服务的转变判定与窗口侧
 * isShowing 承担）。
 */
class AlertPolicyTest {

    private val enabled = NotificationAlertSettings(enabled = true)

    private fun env(
        screenOn: Boolean = true,
        locked: Boolean = false,
        dnd: Boolean = false,
    ) = AlertEnvironment(
        screenOn = screenOn,
        keyguardLocked = locked,
        dndActive = dnd,
    )

    @Test
    fun `disabled alert never plays`() {
        assertFalse(shouldPlay(NotificationAlertSettings(enabled = false), env()))
    }

    @Test
    fun `plays only when every suppression passes`() {
        assertTrue(shouldPlay(enabled, env()))
        assertFalse(shouldPlay(enabled, env(screenOn = false)))
        assertFalse(shouldPlay(enabled, env(locked = true)))
        assertFalse(shouldPlay(enabled, env(dnd = true)))
    }

    @Test
    fun `plan resolves per effect knobs and normalizes ranges`() {
        val plan = buildPlan(
            enabled.copy(
                effect = NotificationAlertEffect.EdgeGlow,
                edgeGlow = enabled.edgeGlow.copy(bandWidthDp = 48, brightness = 60, cycles = 2),
            ),
            cycleMillis = { 1400 },
        )

        assertEquals(NotificationAlertEffect.EdgeGlow, plan.effect)
        assertEquals(2, plan.cycles)
        assertEquals(1400, plan.cycleMillis)
        assertEquals(2800, plan.totalMillis)
        // 亮度 60 → 0.6；带宽 48 在 1–96 区间内线性归一化。
        assertEquals(0.6f, plan.brightness, 0.001f)
        assertEquals((48f - 1f) / (96f - 1f), plan.amount, 0.001f)
        // 专属 spec：默认圆角与硬度原样透传。
        val spec = plan.spec as EdgeGlowSpec
        assertEquals(FloatBallDefaults.AlertEdgeGlowCornerDp, spec.cornerRadiusDp)
        assertEquals(1f, spec.hardness, 0.001f)
    }

    @Test
    fun `legacy band width above new max clamps to full amount`() {
        // 旧存量 110–240 超出新区间上限：夹到 96，量归一化到 1。
        val plan = buildPlan(
            enabled.copy(
                effect = NotificationAlertEffect.EdgeGlow,
                edgeGlow = enabled.edgeGlow.copy(bandWidthDp = 110),
            ),
            cycleMillis = { 1400 },
        )
        assertEquals(1f, plan.amount, 0.001f)
    }

    @Test
    fun `speed knob scales cycle duration against family base`() {
        fun planWithSpeed(speed: Int, base: Int = 1400) = buildPlan(
            enabled.copy(
                effect = NotificationAlertEffect.EdgeGlow,
                edgeGlow = enabled.edgeGlow.copy(speed = speed),
            ),
            cycleMillis = { base },
        )

        // 200% = 两倍速（时长减半），50% = 半速（时长翻倍）。
        assertEquals(700, planWithSpeed(200).cycleMillis)
        assertEquals(2800, planWithSpeed(50).cycleMillis)
        assertEquals(1400, planWithSpeed(100).cycleMillis)
        // 越界夹取：0 → 50%（最慢），999 → 200%（最快）。
        assertEquals(2000, planWithSpeed(0, base = 1000).cycleMillis)
        assertEquals(500, planWithSpeed(999, base = 1000).cycleMillis)
    }

    @Test
    fun `plan clamps out of range knobs per effect`() {
        val plan = buildPlan(
            enabled.copy(
                effect = NotificationAlertEffect.TopParticles,
                particles = enabled.particles.copy(count = 999, brightness = -5, waves = 99),
            ),
            cycleMillis = { 0 },
        )

        // 粒子数钳到上限后量旋钮归一化到 1；亮度负值钳到 0。
        assertEquals(1f, plan.amount, 0.001f)
        assertEquals(FloatBallDefaults.AlertBrightnessMin / 100f, plan.brightness, 0.001f)
        assertEquals(FloatBallDefaults.AlertCyclesMax, plan.cycles)
        assertEquals(1, plan.cycleMillis)
        // 专属 spec：默认形状参数（大小 / 摆幅缩放 100%，真重力，行程 200dp）。
        val spec = plan.spec as ParticlesSpec
        assertEquals(1f, spec.sizeScale, 0.001f)
        assertEquals(1f, spec.swayScale, 0.001f)
        assertEquals(2f, spec.gravityExp, 0.001f)
        assertEquals(200, spec.travelDp)
    }

    @Test
    fun `edge flow plan carries band mode knobs`() {
        val plan = buildPlan(
            enabled.copy(
                effect = NotificationAlertEffect.EdgeFlow,
                edgeFlow = enabled.edgeFlow.copy(
                    bandWidthDp = 24,
                    dualBand = true,
                    trailScale = 150,
                    dualColor = true,
                ),
            ),
            cycleMillis = { 1600 },
        )

        assertEquals((24f - 1f) / (96f - 1f), plan.amount, 0.001f)
        val spec = plan.spec as EdgeFlowSpec
        assertTrue(spec.dualBand)
        assertEquals(1.5f, spec.trailScale, 0.001f)
        assertTrue(spec.dualColor)
    }

    @Test
    fun `full ripple plan carries anchor and stroke knobs`() {
        val plan = buildPlan(
            enabled.copy(
                effect = NotificationAlertEffect.FullRipple,
                fullRipple = enabled.fullRipple.copy(strokeWidthDp = 5, centerAtBall = false),
            ),
            cycleMillis = { 1100 },
        )

        // 涟漪无量旋钮。
        assertEquals(0f, plan.amount, 0.001f)
        val spec = plan.spec as RippleSpec
        assertEquals(5, spec.strokeWidthDp)
        assertFalse(spec.centerAtBall)
    }

    @Test
    fun `pulse has no amount knob`() {
        val plan = buildPlan(
            enabled.copy(effect = NotificationAlertEffect.CapsulePulse),
            cycleMillis = { 1000 },
        )

        assertEquals(0f, plan.amount, 0.001f)
        // 专属 spec：默认辉光宽度 1.5dp、光晕不缩放。
        val spec = plan.spec as PulseSpec
        assertEquals(1.5f, spec.glowWidthDp, 0.001f)
        assertEquals(1f, spec.haloScale, 0.001f)
    }

    @Test
    fun `tuning helpers map amount into per effect ranges`() {
        // 区间映射：0 → 下限，1 → 上限，中点线性。
        assertEquals(0.5f, 0f.lerpInRange(0.5f, 1f), 0.001f)
        assertEquals(1f, 1f.lerpInRange(0.5f, 1f), 0.001f)
        assertEquals(0.75f, 0.5f.lerpInRange(0.5f, 1f), 0.001f)
        // 缓出：k=1 退化为线性，k>1 收尾减速（终点仍为 1）。
        assertEquals(0.5f, 0.5f.easedOut(1f), 0.001f)
        assertEquals(1f, 1f.easedOut(2.2f), 0.001f)
        assertTrue(0.5f.easedOut(2.2f) > 0.5f)
        // 光环行程随量旋钮单调不减，且始终为非负。
        val ballPx = 96
        assertTrue(ringTravelPx(ballPx, 0f) <= ringTravelPx(ballPx, 0.5f))
        assertTrue(ringTravelPx(ballPx, 0.5f) <= ringTravelPx(ballPx, 1f))
        assertEquals(0, ringTravelPx(0, 1f))
    }
}
