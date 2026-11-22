package hunoia.luno.runtime.button

import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.runtime.GestureRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 显隐场景触发边缘语义（architecture §5.3 / §5.4）：
 * 首刷基线、进入 / 退出边缘、冷却窗口、全局间隔推迟、配置变化与总开关重置。
 */
class VisibilityTriggerRuntimeTest {

    private var now = 10_000L

    private data class Fire(val profileId: String, val enter: Boolean, val typeId: String)

    private val fires = mutableListOf<Fire>()

    private fun runtime() = VisibilityTriggerRuntime(
        nowMs = { now },
        onFire = { profile, enter, action -> fires.add(Fire(profile.id, enter, action.typeId)) },
    )

    private fun state(packageName: String = "com.example") = GestureRuntimeState(
        currentPackageName = packageName,
        isNowInLockScreenPage = false,
        isLandscape = false,
        isInLauncher = false,
        isKeyboardInputActive = false,
        hiddenGestureButtons = emptyMap(),
    )

    private fun profile(
        id: String = "p",
        packageName: String = "com.example",
        enter: List<StoredAction> = emptyList(),
        exit: List<StoredAction> = emptyList(),
        cooldownSeconds: Int = 0,
        enabled: Boolean = true,
    ) = VisibilityProfile(
        id = id,
        packageNames = setOf(packageName),
        enterActions = enter,
        exitActions = exit,
        cooldownSeconds = cooldownSeconds,
        enabled = enabled,
    )

    @Test
    fun firstRefresh_baselineDoesNotFire() {
        val rt = runtime()
        rt.onRefresh(true, listOf(profile(enter = listOf(StoredAction.of("a")))), state())
        assertEquals(emptyList<Fire>(), fires)
    }

    @Test
    fun enterEdge_firesEnterActionsInOrder() {
        val rt = runtime()
        val p = profile(enter = listOf(StoredAction.of("a"), StoredAction.of("b")))
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(p), state())
        assertEquals(listOf(Fire("p", true, "a"), Fire("p", true, "b")), fires)
    }

    @Test
    fun exitEdge_firesExitActions() {
        val rt = runtime()
        val p = profile(exit = listOf(StoredAction.of("x")))
        rt.onRefresh(true, listOf(p), state())
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        assertEquals(listOf(Fire("p", false, "x")), fires)
    }

    @Test
    fun sameStateRepeated_doesNotRefire() {
        val rt = runtime()
        val p = profile(enter = listOf(StoredAction.of("a")))
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(p), state())
        rt.onRefresh(true, listOf(p), state())
        rt.onRefresh(true, listOf(p), state())
        assertEquals(1, fires.size)
    }

    @Test
    fun profilesFireIndependently_lastHitWinsDoesNotApply() {
        val rt = runtime()
        val a = profile(id = "a", enter = listOf(StoredAction.of("a")))
        val b = profile(id = "b", enter = listOf(StoredAction.of("b")))
        rt.onRefresh(true, listOf(a, b), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(a, b), state())
        assertEquals(listOf(Fire("a", true, "a"), Fire("b", true, "b")), fires)
    }

    @Test
    fun cooldown_suppressesWithinWindow_thenAllowsAfterExpiry() {
        now = 11_000L
        val rt = runtime()
        val p = profile(enter = listOf(StoredAction.of("a")), cooldownSeconds = 10)
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(p), state()) // 进入：触发
        now = 12_000L
        rt.onRefresh(true, listOf(p), state(packageName = "com.other")) // 退出：未配退出动作
        now = 13_000L
        rt.onRefresh(true, listOf(p), state()) // 重进：冷却窗内 → 丢弃
        assertEquals(1, fires.size)
        now = 25_000L
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        now = 26_000L
        rt.onRefresh(true, listOf(p), state()) // 冷却过期 → 再次触发
        assertEquals(listOf(Fire("p", true, "a"), Fire("p", true, "a")), fires)
    }

    @Test
    fun globalFloor_defersEdgesUntilIntervalPasses() {
        now = 100_000L
        val rt = runtime()
        val p = profile(enter = listOf(StoredAction.of("a")), exit = listOf(StoredAction.of("x")))
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        now = 100_500L
        rt.onRefresh(true, listOf(p), state()) // 进入触发；全局地板自此起算
        now = 101_000L
        rt.onRefresh(true, listOf(p), state(packageName = "com.other")) // 退出边缘：距上次触发不足 1s → 推迟
        assertEquals(listOf(Fire("p", true, "a")), fires)
        now = 102_000L
        rt.onRefresh(true, listOf(p), state(packageName = "com.other")) // 地板已过 → 按最新状态重算退出
        assertEquals(listOf(Fire("p", true, "a"), Fire("p", false, "x")), fires)
    }

    @Test
    fun configChange_rebaselinesNoFire() {
        now = 1_000L
        val rt = runtime()
        val p = profile(enter = listOf(StoredAction.of("a")), exit = listOf(StoredAction.of("x")))
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(p), state()) // 进入触发
        now = 2_000L
        // 配置变化（改名）且场景仍命中 → 只重置基线，不触发
        rt.onRefresh(true, listOf(p.copy(name = "renamed")), state())
        rt.onRefresh(true, listOf(p.copy(name = "renamed")), state())
        assertEquals(1, fires.size)
        now = 3_000L
        rt.onRefresh(true, listOf(p.copy(name = "renamed")), state(packageName = "com.other")) // 基线后的真实退出
        assertEquals(listOf(Fire("p", true, "a"), Fire("p", false, "x")), fires)
    }

    @Test
    fun gestureEnabledOff_resetsBaselineNoBurstOnReenable() {
        val rt = runtime()
        val p = profile(enter = listOf(StoredAction.of("a")))
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(p), state()) // 进入触发
        rt.onRefresh(false, listOf(p), state()) // 总开关关闭：重置基线
        rt.onRefresh(true, listOf(p), state()) // 重开后首刷 = 基线，不触发
        assertEquals(1, fires.size)
    }

    @Test
    fun disabledProfile_neverFires() {
        val rt = runtime()
        val p = profile(enabled = false, enter = listOf(StoredAction.of("a")), exit = listOf(StoredAction.of("x")))
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        rt.onRefresh(true, listOf(p), state())
        rt.onRefresh(true, listOf(p), state(packageName = "com.other"))
        assertEquals(emptyList<Fire>(), fires)
    }
}
