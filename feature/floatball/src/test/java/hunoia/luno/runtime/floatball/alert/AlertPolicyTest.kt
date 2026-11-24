package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.AlertStyleId
import hunoia.luno.config.model.AnchorAlertSettings
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.CapsuleAlertSettings
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.EdgeAlertSettings
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleAlertSettings
import hunoia.luno.config.model.ParticleAlertStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到达提示动效的决策口径：场景抑制全部通过才播；配置到分层播放计划的夹取与归一化
 * （含速度倍率对样式基准时长的折算、每类一个家族、各样式专属 spec 的透传）。
 *
 * 触发语义（需求确认）：仅新通知到达时播一次；熄屏 / 锁屏 / 勿扰 / 动效黑名单命中一律不播。
 * 胶囊可见与播放中合并不在此判（分别由服务的转变判定与窗口侧 isShowing 承担）。
 */
class AlertPolicyTest {

    private val enabled = NotificationAlertSettings(
        edge = EdgeAlertSettings(enabled = true),
        particle = ParticleAlertSettings(enabled = true),
        capsule = CapsuleAlertSettings(enabled = true),
        anchor = AnchorAlertSettings(enabled = true),
    )

    private val standard: (AlertStyleId) -> Float = { 1f }

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
    fun `no master switch and all categories default off`() {
        // 四类默认全关：没有总开关，四类开关是唯一入口；空计划 = 不播。
        val plan = buildPlan(NotificationAlertSettings(), standard)
        assertTrue(plan.isEmpty)
        assertEquals(0, plan.durationMillis)
    }

    @Test
    fun `plays only when every suppression passes`() {
        assertTrue(shouldPlay(enabled, env()))
        assertFalse(shouldPlay(enabled, env(screenOn = false)))
        assertFalse(shouldPlay(enabled, env(locked = true)))
        assertFalse(shouldPlay(enabled, env(dnd = true)))
        // 黑名单只按来源包命中：未配置时同一包照播。
        assertTrue(shouldPlay(enabled, env(), packageName = "com.blocked"))
        val blocked = enabled.copy(appBlocklist = listOf("com.blocked"))
        assertFalse(shouldPlay(blocked, env(), packageName = "com.blocked"))
        assertTrue(shouldPlay(blocked, env(), packageName = "com.ok"))
    }

    @Test
    fun `plan collects only enabled categories`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
            ),
            standard,
        )
        assertEquals(1, plan.layers.size)
        assertEquals(AnchorAlertStyle.Ring, plan.layers.single().style)
        assertTrue(plan.capsuleLayers.isEmpty())
        assertEquals(1, plan.stageLayers.size)
    }

    @Test
    fun `all categories produce four layers and duration is the longest`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(edgeGlow = enabled.edge.edgeGlow.copy(cycles = 4)),
                particle = enabled.particle.copy(particles = enabled.particle.particles.copy(waves = 1)),
                capsule = enabled.capsule.copy(pulse = enabled.capsule.pulse.copy(cycles = 2)),
                anchor = enabled.anchor.copy(ring = enabled.anchor.ring.copy(cycles = 3)),
            ),
            standard,
        )
        assertEquals(4, plan.layers.size)
        assertEquals(4 * 1400, plan.durationMillis)
        assertEquals(1, plan.capsuleLayers.size)
        assertEquals(3, plan.stageLayers.size)
    }

    @Test
    fun `plan resolves per style knobs and normalizes ranges`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(
                    edgeGlow = enabled.edge.edgeGlow.copy(
                        bandWidthDp = 48,
                        brightness = 60,
                        cycles = 2,
                        speed = 100,
                    ),
                ),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        val layer = plan.layers.single()
        assertEquals(EdgeAlertStyle.EdgeGlow, layer.style)
        assertEquals(2, layer.cycles)
        assertEquals(1400, layer.cycleMillis)
        assertEquals(2800, layer.totalMillis)
        // 亮度 60 → 0.6；带宽 48 在 1–96 区间内线性归一化。
        assertEquals(0.6f, layer.brightness, 0.001f)
        assertEquals((48f - 1f) / (96f - 1f), layer.amount, 0.001f)
        // 专属 spec：默认圆角与硬度原样透传。
        val spec = layer.spec as EdgeGlowSpec
        assertEquals(FloatBallDefaults.AlertEdgeGlowCornerDp, spec.cornerRadiusDp)
        assertEquals(1f, spec.hardness, 0.001f)
    }

    @Test
    fun `legacy band width above new max clamps to full amount`() {
        // 旧存量 110–240 超出新区间上限：夹到 96，量归一化到 1。
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(edgeGlow = enabled.edge.edgeGlow.copy(bandWidthDp = 110)),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        assertEquals(1f, plan.layers.single().amount, 0.001f)
    }

    @Test
    fun `speed knob and tier factor scale style base duration`() {
        fun planWith(speed: Int, tier: Float = 1f) = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(edgeGlow = enabled.edge.edgeGlow.copy(speed = speed)),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            { tier },
        ).layers.single().cycleMillis

        // 200% = 两倍速（时长减半），50% = 半速（时长翻倍）。
        assertEquals(700, planWith(speed = 200))
        assertEquals(2800, planWith(speed = 50))
        assertEquals(1400, planWith(speed = 100))
        // 越界夹取：0 → 50%（最慢），999 → 200%（最快）。
        assertEquals(2800, planWith(speed = 0))
        assertEquals(700, planWith(speed = 999))
        // 档位倍率只乘一次（1.5× 慢档 → 基准 × 1.5）。
        assertEquals(2100, planWith(speed = 100, tier = 1.5f))
    }

    @Test
    fun `plan clamps out of range knobs per style`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(
                    particles = enabled.particle.particles.copy(
                        count = 999,
                        brightness = -5,
                        waves = 99,
                    ),
                ),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        val layer = plan.layers.single()
        // 粒子数钳到上限后量旋钮归一化到 1；亮度负值钳到 0。
        assertEquals(1f, layer.amount, 0.001f)
        assertEquals(FloatBallDefaults.AlertBrightnessMin / 100f, layer.brightness, 0.001f)
        assertEquals(FloatBallDefaults.AlertCyclesMax, layer.cycles)
        assertEquals(alertStyleBaseMillis(ParticleAlertStyle.TopRain), layer.cycleMillis)
        // 专属 spec：默认形状参数（大小 / 摆幅缩放 100%，真重力，行程 100% 屏高；风 0、阻力 30%、方差 30%）。
        val spec = layer.spec as ParticlesSpec
        assertEquals(1f, spec.sizeScale, 0.001f)
        assertEquals(1f, spec.swayScale, 0.001f)
        assertEquals(2f, spec.gravityExp, 0.001f)
        assertEquals(1f, spec.spreadScale, 0.001f)
        assertEquals(FloatBallDefaults.AlertParticlesTailLengthDp, spec.tailLengthDp)
        assertEquals(1f, spec.travelPercent, 0.001f)
        assertEquals(0f, spec.wind, 0.001f)
        assertEquals(FloatBallDefaults.AlertParticlesDrag / 100f, spec.drag, 0.001f)
        assertEquals(FloatBallDefaults.AlertParticlesVariance / 100f, spec.variance, 0.001f)
    }

    @Test
    fun `edge flow plan carries the blade knobs`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(
                    style = EdgeAlertStyle.EdgeFlow,
                    edgeFlow = enabled.edge.edgeFlow.copy(
                        bandWidthDp = 24,
                        dualBand = true,
                        trailScale = 150,
                        dualColor = true,
                        coreBandPermille = 40,
                        hardness = 3,
                        trailAlpha = 55,
                        cornerRadiusDp = 32,
                    ),
                ),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        val layer = plan.layers.single()
        assertEquals((24f - 1f) / (96f - 1f), layer.amount, 0.001f)
        assertEquals(alertStyleBaseMillis(EdgeAlertStyle.EdgeFlow), layer.cycleMillis)
        val spec = layer.spec as EdgeFlowSpec
        assertTrue(spec.dualBand)
        assertEquals(1.5f, spec.trailScale, 0.001f)
        assertTrue(spec.dualColor)
        assertEquals(0.04f, spec.coreBand, 0.0001f)
        assertEquals(3f, spec.hardness, 0.001f)
        assertEquals(0.55f, spec.trailAlpha, 0.001f)
        assertEquals(32, spec.cornerRadiusDp)
    }

    @Test
    fun `full ripple plan carries anchor and stroke knobs`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(
                    style = AnchorAlertStyle.Ripple,
                    fullRipple = enabled.anchor.fullRipple.copy(
                        strokeWidthDp = 5,
                        haloScale = 150,
                        centerAtBall = false,
                    ),
                ),
            ),
            standard,
        )
        val layer = plan.layers.single()
        // 涟漪无量旋钮。
        assertEquals(0f, layer.amount, 0.001f)
        val spec = layer.spec as RippleSpec
        assertEquals(5, spec.strokeWidthDp)
        assertEquals(1.5f, spec.haloScale, 0.001f)
        assertFalse(spec.centerAtBall)
        assertEquals(alertStyleBaseMillis(AnchorAlertStyle.Ripple), layer.cycleMillis)
    }

    @Test
    fun `pulse has no amount knob`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(style = CapsuleAlertStyle.Pulse),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        val layer = plan.layers.single()
        assertEquals(0f, layer.amount, 0.001f)
        // 专属 spec：默认辉光宽度 1.5dp、光晕不缩放。
        val spec = layer.spec as PulseSpec
        assertEquals(1.5f, spec.glowWidthDp, 0.001f)
        assertEquals(1f, spec.haloScale, 0.001f)
        assertEquals(alertStyleBaseMillis(CapsuleAlertStyle.Pulse), layer.cycleMillis)
    }

    @Test
    fun `onlyCategory filters to a single category`() {
        val plan = buildPlan(enabled, standard)
        assertEquals(4, plan.layers.size)
        val edge = plan.onlyCategory(AlertCategory.Edge)
        assertEquals(1, edge.layers.size)
        assertEquals(EdgeAlertStyle.EdgeGlow, edge.layers.single().style)
        assertEquals(4, plan.onlyCategory(null).layers.size)
    }

    @Test
    fun `needsCapsule only for capsule layers and ball anchored anchor layers`() {
        fun planOf(anchorStyle: AnchorAlertStyle, centerAtBall: Boolean = true) = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(
                    style = anchorStyle,
                    fullRipple = enabled.anchor.fullRipple.copy(centerAtBall = centerAtBall),
                ),
            ),
            standard,
        )

        assertTrue(needsCapsule(planOf(AnchorAlertStyle.Ring)))
        assertTrue(needsCapsule(planOf(AnchorAlertStyle.Ripple, centerAtBall = true)))
        assertFalse(needsCapsule(planOf(AnchorAlertStyle.Ripple, centerAtBall = false)))
        // 胶囊层依赖胶囊窗自己的 Compose 层。
        val capsuleOnly = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        assertTrue(needsCapsule(capsuleOnly))
        // 纯边缘 / 粒子计划不依赖胶囊。
        val stageOnly = buildPlan(
            enabled.copy(
                capsule = enabled.capsule.copy(enabled = false),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        assertFalse(needsCapsule(stageOnly))
    }

    @Test
    fun `every style has a base duration`() {
        val styles = EdgeAlertStyle.entries +
            ParticleAlertStyle.entries +
            CapsuleAlertStyle.entries +
            AnchorAlertStyle.entries
        for (style in styles) {
            assertTrue("$style 未登记基准时长", alertStyleBaseMillis(style) > 0)
        }
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

    @Test
    fun `plan carries the per play seed through onlyCategory`() {
        val plan = buildPlan(enabled, standard, seed = 0x1234)
        assertEquals(0x1234, plan.seed)
        assertEquals(0x1234, plan.onlyCategory(AlertCategory.Edge).seed)
    }

    @Test
    fun `shimmer plan carries stroke width knob`() {
        val plan = buildPlan(
            enabled.copy(
                edge = enabled.edge.copy(enabled = false),
                particle = enabled.particle.copy(enabled = false),
                capsule = enabled.capsule.copy(
                    style = CapsuleAlertStyle.Shimmer,
                    shimmer = enabled.capsule.shimmer.copy(strokeWidthTenths = 25),
                ),
                anchor = enabled.anchor.copy(enabled = false),
            ),
            standard,
        )
        val spec = plan.layers.single().spec as ShimmerSpec
        assertEquals(2.5f, spec.strokeWidthDp, 0.001f)
    }

    @Test
    fun `plan carries each category palette spec`() {
        val custom = hunoia.luno.config.model.AlertPalette(
            id = "custom_test",
            name = "Test",
            colors = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
        )
        val rainbow = hunoia.luno.config.model.AlertPalette(
            id = "builtin_rainbow",
            name = "彩虹",
            rainbow = true,
        )
        val settings = enabled.copy(
            customPalettes = listOf(custom),
            edge = enabled.edge.copy(paletteId = custom.id),
            particle = enabled.particle.copy(paletteId = rainbow.id),
            capsule = enabled.capsule.copy(paletteId = custom.id),
            anchor = enabled.anchor.copy(paletteId = null),
        )
        val plan = buildPlan(settings, standard)
        assertEquals(4, plan.layers.size)
        val byStyle = plan.layers.associateBy { it.style }
        // 自定义：3 色原样透传，非彩虹。
        val edgeSpec = byStyle[EdgeAlertStyle.EdgeGlow]!!.palette!!
        assertFalse(edgeSpec.rainbow)
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()), edgeSpec.colors)
        // 内置彩虹：透传 rainbow 标志。
        val particleSpec = byStyle[ParticleAlertStyle.TopRain]!!.palette!!
        assertTrue(particleSpec.rainbow)
        assertTrue(particleSpec.colors.isEmpty())
        // 胶囊类同 paletteId 也解析为同一份 spec。
        val capsuleSpec = byStyle[CapsuleAlertStyle.Pulse]!!.palette!!
        assertEquals(edgeSpec, capsuleSpec)
        // 未选（null）→ 该层不携带调色板（渲染端回退单色）。
        assertNull(byStyle[AnchorAlertStyle.Ring]!!.palette)
    }
}
