package hunoia.luno.gridpanel

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelSettings
import hunoia.luno.config.model.PanelMode
import hunoia.luno.gridpanel.internal.InternalWidgetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 快速启动动作的入口收口：只认单功能模式 + quickLaunch 功能，不改旧配置。 */
class QuickLaunchPanelRoutingTest {

    private fun settings(vararg panels: GridPanel) = ActionSettings(
        actionGridPanels = GridPanelSettings(panels.toList()),
    )

    @Test
    fun `没有快速启动单功能面板时不收口`() {
        assertNull(QuickLaunchPanelRouting.findConfiguredPanel(settings()))
        assertNull(
            QuickLaunchPanelRouting.findConfiguredPanel(
                settings(GridPanel.createSingle().copy(singleWidgetId = InternalWidgetIds.CLIPBOARD)),
            ),
        )
    }

    @Test
    fun `网格模式里放了 quickLaunch 也不收口`() {
        val gridWithQuickLaunch = GridPanel.create().copy(
            mode = PanelMode.Grid,
            singleWidgetId = InternalWidgetIds.QUICK_LAUNCH,
        )
        assertNull(QuickLaunchPanelRouting.findConfiguredPanel(settings(gridWithQuickLaunch)))
    }

    @Test
    fun `取第一个快速启动单功能面板`() {
        val first = GridPanel.createSingle(name = "第一").copy(
            singleWidgetId = InternalWidgetIds.QUICK_LAUNCH,
        )
        val second = GridPanel.createSingle(name = "第二").copy(
            singleWidgetId = InternalWidgetIds.QUICK_LAUNCH,
        )
        val found = QuickLaunchPanelRouting.findConfiguredPanel(settings(first, second))
        assertEquals(first.id, found?.id)
    }
}
