package hunoia.luno.gridpanel.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PanelWidgetRegistryTest {

    @Test
    fun `built-in widget ids are unique`() {
        val ids = PanelWidgetRegistry.all.map { it.id }
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `min span never exceeds default span`() {
        PanelWidgetRegistry.all.forEach { widget ->
            assertTrue(
                "${widget.id} 的最小占位超过默认占位",
                widget.minSpanX <= widget.defaultSpanX && widget.minSpanY <= widget.defaultSpanY,
            )
        }
    }

    @Test
    fun `single panel whitelist is clipboard app drawer and quick launch`() {
        assertEquals(
            setOf(
                InternalWidgetIds.CLIPBOARD,
                InternalWidgetIds.APP_DRAWER,
                InternalWidgetIds.QUICK_LAUNCH,
            ),
            PanelWidgetRegistry.singlePanelWidgets.map { it.id }.toSet(),
        )
        // 白名单必须是注册表的子集，避免出现「选择页列了但渲染不到」的悬空 id。
        assertTrue(PanelWidgetRegistry.ids.containsAll(PanelWidgetRegistry.singlePanelWidgets.map { it.id }))
    }

    @Test
    fun `quick launch is single-panel only`() {
        val quickLaunch = PanelWidgetRegistry.byId(InternalWidgetIds.QUICK_LAUNCH)
        assertTrue(quickLaunch?.supportsSinglePanel == true)
        assertTrue(quickLaunch?.availableAsGridItem == false)
        assertTrue(InternalWidgetDefs.none { it.id == InternalWidgetIds.QUICK_LAUNCH })
    }

    @Test
    fun `duplicate widget id fails fast`() {
        val existing = PanelWidgetRegistry.all.first()
        try {
            PanelWidgetRegistry.register(existing)
            fail("重复 widget 应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
