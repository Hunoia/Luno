package hunoia.luno.gridpanel

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelSettings
import hunoia.luno.config.model.PanelDockDefaults
import hunoia.luno.config.model.PanelDockSettings
import hunoia.luno.config.model.SinglePanelIds
import hunoia.luno.config.model.singlePanelRefId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏成员解析：把用户写入的原始成员表变成可渲染清单。
 *
 * 失效的成员必须**在这里**就掉掉，不能留到渲染层：底栏出现一个点了没反应的 tab，
 * 用户只会看到「面板不见了」。设置页与底栏共用这一份解析。
 */
class PanelDockCatalogTest {

    private fun settings(
        members: List<String>,
        gridIds: List<String> = listOf("g1", "g2"),
        enabled: Boolean = true,
    ) = ActionSettings(
        actionGridPanels = GridPanelSettings(panels = gridIds.map { GridPanel(id = it) }),
        panelDock = PanelDockSettings(enabled = enabled, members = members),
    )

    @Test
    fun `keeps written order`() {
        val resolved = PanelDockCatalog.memberIds(settings(listOf("g2", "g1")))
        assertEquals(listOf("g2", "g1"), resolved)
    }

    @Test
    fun `drops dangling references and duplicates`() {
        val resolved = PanelDockCatalog.memberIds(
            settings(
                listOf(
                    "g1",
                    "deleted-grid-panel",
                    "g1",
                    singlePanelRefId(SinglePanelIds.CLIPBOARD),
                    // 注册表里存在但不是整页面板：不能进底栏。
                    singlePanelRefId("internal.media"),
                    "single:no-such-widget",
                ),
            ),
        )
        assertEquals(listOf("g1", singlePanelRefId(SinglePanelIds.CLIPBOARD)), resolved)
    }

    @Test
    fun `truncates to the member cap`() {
        val ids = (1..PanelDockDefaults.MaxMembers + 3).map { "g$it" }
        val resolved = PanelDockCatalog.memberIds(settings(ids, gridIds = ids))
        assertEquals(PanelDockDefaults.MaxMembers, resolved.size)
        assertEquals(ids.take(PanelDockDefaults.MaxMembers), resolved)
    }

    @Test
    fun `single panels can be dock members without any stored record`() {
        // 单功能面板没有配置记录也存在（默认几何），因此底栏成员不需要配套配置。
        val resolved = PanelDockCatalog.memberIds(
            settings(listOf(singlePanelRefId(SinglePanelIds.APP_DRAWER))),
        )
        assertEquals(listOf(singlePanelRefId(SinglePanelIds.APP_DRAWER)), resolved)
    }

    @Test
    fun `visible only when enabled with at least two members`() {
        assertTrue(PanelDockCatalog.isVisible(settings(listOf("g1", "g2"))))
        // 只有一块：底栏只是白占高度。
        assertFalse(PanelDockCatalog.isVisible(settings(listOf("g1"))))
        // 两块但其中一块悬空：实际只剩一块，同样不该出现。
        assertFalse(PanelDockCatalog.isVisible(settings(listOf("g1", "gone"))))
        // 总开关关掉：成员再多也不渲染（也不扣高度）。
        assertFalse(PanelDockCatalog.isVisible(settings(listOf("g1", "g2"), enabled = false)))
    }
}
