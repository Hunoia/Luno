package hunoia.luno.gesture.touchsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * 接管落点选择：第二段注入必须落在「应用主窗口内、且没有被更高层窗口盖住」的点。
 *
 * 这几条错了的表现是**静默失败**：注入被系统接受、日志写着「已接管」，可源应用那条真实
 * 流根本没收到 CANCEL（底下照样滚动 / 照样弹菜单），事后完全查不出来。所以用固定几何钉住，
 * 全屏尺寸取自真机（1200x2670，联系人长按菜单实测几乎占满）。
 */
class TakeoverPlanTest {

    private val screenWidth = 1200
    private val screenHeight = 2670

    private fun app(
        id: Int = 10,
        layer: Int = 1,
        left: Int = 0,
        top: Int = 0,
        right: Int = screenWidth,
        bottom: Int = screenHeight,
    ) = WindowBox(id, layer, WindowBox.TYPE_APPLICATION, left, top, right, bottom)

    private fun overlay(
        id: Int,
        layer: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        type: Int = 2,
    ) = WindowBox(id, layer, type, left, top, right, bottom)

    @Test
    fun `press directly on the app window needs no second stage`() {
        val plan = planOf(600, 1300, listOf(app()))
        // 按下点本身就是主窗口：那一次注入就命中真实流，不必再换点（省掉一段注入的延迟）。
        assertFalse(plan.pressCovered)
        assertNull(plan.safe)
    }

    @Test
    fun `small press-anchored menu moves the second stage outside it`() {
        // 典型的微信长按菜单：贴在右下角、只有几百 px。
        val menu = overlay(id = 20, layer = 5, left = 600, top = 1500, right = 1150, bottom = 2100)
        val plan = planOf(800, 1600, listOf(app(), menu))
        assertTrue(plan.pressCovered)
        val safe = assertNotNullPoint(plan)
        // 落点必须在菜单之外，并且在主窗口内。
        assertFalse("safe=$safe 落在菜单内", menu.contains(safe.first, safe.second))
        assertTrue(plan.appWindow.contains(safe.first, safe.second))
    }

    @Test
    fun `full-screen press menu still yields a blind second-stage point`() {
        // 实测反例：联系人的长按菜单 [16,82]-[1174,2658]，几乎占满全屏。
        // 清晰点算不出来，但第二段不能就此放弃：菜单常常在阶段 1 之后才开始退出动画，
        // 这时按快照看永远是「被盖住」。盲打一个离按下点最远的点，命中就能取消真实流，
        // 没命中也不比单段更差。
        val menu = overlay(id = 20, layer = 5, left = 16, top = 82, right = 1174, bottom = 2658)
        val plan = planOf(650, 1577, listOf(app(), menu))
        assertTrue(plan.pressCovered)
        assertNull(plan.safe)
        val blind = assertNotNullPoint(
            takeoverFallbackPoint(650, 1577, plan.appWindow),
            "盲打落点",
        )
        // 至少在应用主窗口内，且离按下点足够远（不能落在菜单中心附近）。
        assertTrue(plan.appWindow.contains(blind.first, blind.second))
        val dist = kotlin.math.hypot((blind.first - 650).toDouble(), (blind.second - 1577).toDouble())
        assertTrue("blind=$blind 离按下点太近 ($dist)", dist > 400)
    }

    @Test
    fun `menu that covers the whole screen even outside the touch region`() {
        // 触控区域是全屏时（WATCH_OUTSIDE_TOUCH 的 Panel 常见），缩进也救不回来。
        val menu = overlay(id = 20, layer = 5, left = 0, top = 0, right = screenWidth, bottom = screenHeight)
        assertNull(planOf(600, 1300, listOf(app(), menu)).safe)    }

    @Test
    fun `second stage stays inside the app window and away from the edges`() {
        val menu = overlay(id = 20, layer = 5, left = 0, top = 1200, right = screenWidth, bottom = 2100)
        val safe = assertNotNullPoint(planOf(600, 1300, listOf(app(), menu)))
        // 缩进 10%：不能贴边（边缘是系统手势条 / 导航条，不属于任何无障碍窗口）。
        assertTrue(safe.first >= screenWidth / 10)
        assertTrue(safe.first <= screenWidth - screenWidth / 10)
        assertTrue(safe.second >= screenHeight / 10)
        assertTrue(safe.second <= screenHeight - screenHeight / 10)
    }

    @Test
    fun `second stage never lands where a higher window covers it`() {
        // 上半屏有输入法、中心有浮层：可用点只剩下面那条缝，落点必须避开这两块。
        val menu = overlay(id = 20, layer = 5, left = 0, top = 1200, right = screenWidth, bottom = 2100)
        val inputMethod = overlay(id = 30, layer = 6, left = 0, top = 0, right = screenWidth, bottom = 900)
        val bubble = overlay(id = 40, layer = 7, left = 400, top = 400, right = 800, bottom = 1100)
        val safe = assertNotNullPoint(planOf(600, 1300, listOf(app(), menu, inputMethod, bubble)))
        assertFalse(inputMethod.contains(safe.first, safe.second))
        assertFalse(bubble.contains(safe.first, safe.second))
        assertFalse(menu.contains(safe.first, safe.second))
    }

    @Test
    fun `no application window under the press means no plan`() {
        // 分屏下应用只占下半屏，按下点在上半屏（只有自家覆盖层 / 系统窗口）：
        // 没有「承载这条真实流的应用窗口」，取消无从谈起，必须返回 null。
        val splitApp = app(id = 10, top = 1300)
        val own = overlay(id = 20, layer = 5, left = 0, top = 0, right = 1200, bottom = 300)
        assertNull(planTakeover(600, 150, listOf(splitApp, own)))
    }

    @Test
    fun `empty window list degrades to no plan`() {
        assertNull(planTakeover(600, 1300, emptyList()))
    }

    @Test
    fun `nested app windows pick the lowest layer for the real stream`() {
        // 应用内有分屏 / 画中画：真实流属于声明那个 Activity 的主窗口，即 layer 最低者。
        val pip = app(id = 11, layer = 3, left = 700, top = 1800, right = 1180, bottom = 2280)
        val main = app(id = 10, layer = 1)
        val inputMethod = overlay(id = 30, layer = 6, left = 0, top = 1900, right = screenWidth, bottom = 2670)
        // 按下点落在画中画窗口上（不被输入法盖住），但真实流属于它背后的主窗口。
        val plan = planOf(800, 1850, listOf(main, pip, inputMethod))
        assertEquals(11, plan.pressWindow.id)
        assertEquals(10, plan.appWindow.id)
        val safe = assertNotNullPoint(plan)
        assertFalse("落点被输入法盖住就没意义", inputMethod.contains(safe.first, safe.second))
    }

    private fun planOf(x: Int, y: Int, windows: List<WindowBox>): TakeoverPlan =
        planTakeover(x, y, windows) ?: throw AssertionError("expected a takeover plan")

    private fun assertNotNullPoint(point: Pair<Int, Int>?, what: String = "第二段落点"): Pair<Int, Int> =
        point ?: throw AssertionError("expected $what, got null")

    private fun assertNotNullPoint(plan: TakeoverPlan): Pair<Int, Int> =
        plan.safe ?: throw AssertionError("expected a clear second-stage point, got null")
}
