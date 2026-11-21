package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertEffect
import hunoia.luno.config.model.NotificationAlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到达提示动效的决策口径：场景抑制全部通过才播；配置到播放计划的夹取与派生。
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
    fun `plan derives normalized intensity and bounded cycles`() {
        val plan = buildPlan(enabled.copy(intensity = 60, pace = 2), cycleMillis = 1000)

        assertEquals(NotificationAlertEffect.EdgeGlow, plan.effect)
        assertEquals(0.6f, plan.intensity, 0.001f)
        assertEquals(2, plan.cycles)
        assertEquals(1000, plan.cycleMillis)
        assertEquals(2000, plan.totalMillis)
    }

    @Test
    fun `plan clamps sliders out of range`() {
        val plan = buildPlan(enabled.copy(intensity = 500, pace = 99), cycleMillis = 0)

        assertEquals(FloatBallDefaults.AlertIntensityMax / 100f, plan.intensity, 0.001f)
        assertEquals(FloatBallDefaults.AlertPaceMax, plan.cycles)
        assertEquals(1, plan.cycleMillis)
        assertEquals(FloatBallDefaults.AlertPaceMax, plan.totalMillis)
    }

    @Test
    fun `tuning helpers map intensity into per effect ranges`() {
        // 区间映射：0 → 下限，1 → 上限，中点线性。
        assertEquals(0.5f, 0f.lerpInRange(0.5f, 1f), 0.001f)
        assertEquals(1f, 1f.lerpInRange(0.5f, 1f), 0.001f)
        assertEquals(0.75f, 0.5f.lerpInRange(0.5f, 1f), 0.001f)
        // 缓出：k=1 退化为线性，k>1 收尾减速（终点仍为 1）。
        assertEquals(0.5f, 0.5f.easedOut(1f), 0.001f)
        assertEquals(1f, 1f.easedOut(2.2f), 0.001f)
        assertTrue(0.5f.easedOut(2.2f) > 0.5f)
        // 光环行程随强度单调不减，且始终为非负。
        val ballPx = 96
        assertTrue(ringTravelPx(ballPx, 0f) <= ringTravelPx(ballPx, 0.5f))
        assertTrue(ringTravelPx(ballPx, 0.5f) <= ringTravelPx(ballPx, 1f))
        assertEquals(0, ringTravelPx(0, 1f))
    }
}
