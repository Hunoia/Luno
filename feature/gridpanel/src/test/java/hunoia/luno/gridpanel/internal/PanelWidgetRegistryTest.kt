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
