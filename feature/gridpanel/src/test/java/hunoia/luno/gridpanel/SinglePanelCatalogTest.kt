package hunoia.luno.gridpanel

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.SinglePanel
import hunoia.luno.config.model.SinglePanelIds
import hunoia.luno.config.model.SinglePanelSettings
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglePanelCatalogTest {

    @Test
    fun `fixed panels follow registry order`() {
        val all = SinglePanelCatalog.all(ActionSettings())
        assertEquals(PanelWidgetRegistry.singlePanelWidgets.map { it.id }, all.map { it.widgetId })
        assertTrue(all.any { it.widgetId == SinglePanelIds.QUICK_LAUNCH })
    }

    @Test
    fun `missing config falls back to defaults`() {
        val resolved = SinglePanelCatalog.resolve(ActionSettings(), SinglePanelIds.QUICK_LAUNCH)
        assertEquals(SinglePanelIds.QUICK_LAUNCH, resolved.widgetId)
        assertEquals(SinglePanel(widgetId = SinglePanelIds.QUICK_LAUNCH), resolved)
    }

    @Test
    fun `stored config overrides defaults`() {
        val stored = SinglePanel(widgetId = SinglePanelIds.CLIPBOARD, cardAlpha = 0.4f)
        val settings = ActionSettings(
            singlePanels = SinglePanelSettings(panels = listOf(stored)),
        )
        assertEquals(0.4f, SinglePanelCatalog.resolve(settings, SinglePanelIds.CLIPBOARD).cardAlpha)
    }
}
