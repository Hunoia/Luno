package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 面板内容模式：旧配置缺键必须回落网格，单功能字段只做加法。 */
class GridPanelModeTest {

    @Test
    fun `旧配置缺 mode 回落网格`() {
        val decoded = JsonSerializer.decodeFromString<GridPanel>(
            """{"id":"p","name":"n"}""",
        )
        assertEquals(PanelMode.Grid, decoded.mode)
        assertEquals(false, decoded.isSingle)
        assertNull(decoded.singleWidgetId)
        assertNull(decoded.singleParams)
    }

    @Test
    fun `单功能模式往返保留功能与参数`() {
        val panel = GridPanel.createSingle(name = "剪贴板").copy(
            singleWidgetId = "internal.clipboardPage",
            singleParams = """{"k":1}""",
        )
        val decoded = JsonSerializer.decodeFromString<GridPanel>(
            JsonSerializer.encodeToString(panel),
        )
        assertEquals(PanelMode.Single, decoded.mode)
        assertEquals("internal.clipboardPage", decoded.singleWidgetId)
        assertEquals("""{"k":1}""", decoded.singleParams)
    }

    @Test
    fun `单功能面板有效页数恒为一`() {
        val panel = GridPanel.createSingle().copy(
            portrait = GridPanelOrientation(
                items = listOf(GridPanelItem(pageIndex = 3)),
            ),
        )
        assertEquals(1, panel.pageCount(landscape = false))
        assertEquals(1, panel.pageCount(landscape = true))
    }

    @Test
    fun `网格面板有效页数仍按项派生`() {
        val panel = GridPanel.create().copy(
            portrait = GridPanelOrientation(
                items = listOf(GridPanelItem(pageIndex = 2)),
            ),
        )
        assertEquals(3, panel.pageCount(landscape = false))
        assertEquals(1, panel.pageCount(landscape = true))
    }

    @Test
    fun `单功能面板仍按朝向保留独立几何`() {
        val panel = GridPanel.createSingle().copy(
            portrait = GridPanelOrientation(
                geometry = GridPanelGeometry(columnCount = 4, cardWidthFraction = 0.8f),
            ),
            landscape = GridPanelOrientation(
                geometry = GridPanelGeometry(columnCount = 6, cardWidthFraction = 0.6f),
            ),
        )
        val decoded = JsonSerializer.decodeFromString<GridPanel>(
            JsonSerializer.encodeToString(panel),
        )
        assertEquals(0.8f, decoded.portrait.geometry.cardWidthFraction, 0.0001f)
        assertEquals(0.6f, decoded.landscape.geometry.cardWidthFraction, 0.0001f)
        assertEquals(6, decoded.landscape.geometry.columnCount)
    }
}
