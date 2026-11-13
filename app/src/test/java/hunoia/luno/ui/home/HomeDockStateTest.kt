package hunoia.luno.ui.home

import hunoia.luno.ui.component.DefaultHomeDockSide
import hunoia.luno.ui.component.HomeDockSide
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 三球停靠栏的选页规则（ui-guidelines §4.2）：
 * 收起球显示哪一页，点它就切到哪一页；左球在组外固定回主页，中球在组外回到上次记录页。
 */
class HomeDockStateTest {

    @Test
    fun `首次进入默认展开主导航球`() {
        assertEquals(HomeDockSide.NAV, DefaultHomeDockSide)
    }

    @Test
    fun `六个页面分成两组`() {
        assertEquals(HomeDockSide.NAV, MainTab.Home.group)
        assertEquals(HomeDockSide.NAV, MainTab.ActionLibrary.group)
        assertEquals(HomeDockSide.NAV, MainTab.Settings.group)
        assertEquals(HomeDockSide.SECONDARY, MainTab.Habits.group)
        assertEquals(HomeDockSide.SECONDARY, MainTab.Todos.group)
        assertEquals(HomeDockSide.SECONDARY, MainTab.Trends.group)
    }

    @Test
    fun `组内点球只展开，不改页`() {
        assertEquals(
            MainTab.ActionLibrary,
            collapsedBallTab(MainTab.ActionLibrary, HomeDockSide.NAV, MainTab.Habits),
        )
        assertEquals(
            MainTab.Todos,
            collapsedBallTab(MainTab.Todos, HomeDockSide.SECONDARY, MainTab.Todos),
        )
    }

    @Test
    fun `组外点左球固定回主页`() {
        assertEquals(
            MainTab.Home,
            collapsedBallTab(MainTab.Habits, HomeDockSide.NAV, MainTab.Habits),
        )
        assertEquals(
            MainTab.Home,
            collapsedBallTab(MainTab.Trends, HomeDockSide.NAV, MainTab.Habits),
        )
    }

    @Test
    fun `组外点中球回到上次记录页`() {
        assertEquals(
            MainTab.Trends,
            collapsedBallTab(MainTab.Home, HomeDockSide.SECONDARY, MainTab.Trends),
        )
        assertEquals(
            MainTab.Todos,
            collapsedBallTab(MainTab.Settings, HomeDockSide.SECONDARY, MainTab.Todos),
        )
        assertEquals(
            MainTab.Habits,
            collapsedBallTab(MainTab.ActionLibrary, HomeDockSide.SECONDARY, MainTab.Habits),
        )
    }

    @Test
    fun `上次记录页非法时回落到习惯`() {
        assertEquals(
            MainTab.Habits,
            collapsedBallTab(MainTab.Home, HomeDockSide.SECONDARY, MainTab.Settings),
        )
    }
}
