package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SinglePanelTest {

    @Test
    fun `ref id round trips to widget id`() {
        val ref = singlePanelRefId(SinglePanelIds.QUICK_LAUNCH)
        assertEquals("single:internal.quickLaunch", ref)
        assertEquals(SinglePanelIds.QUICK_LAUNCH, singlePanelWidgetId(ref))
        assertNull(singlePanelWidgetId("8a4b"))
        assertNull(singlePanelWidgetId("single:"))
    }

    @Test
    fun `settings keep one panel per widget`() {
        val first = SinglePanel(widgetId = SinglePanelIds.CLIPBOARD, cardAlpha = 0.5f)
        val replacement = first.copy(cardAlpha = 0.8f)
        val settings = SinglePanelSettings()
            .withPanel(first)
            .withPanel(SinglePanel(widgetId = SinglePanelIds.APP_DRAWER))
            .withPanel(replacement)

        assertEquals(2, settings.panels.size)
        assertEquals(0.8f, settings.panelByWidgetId(SinglePanelIds.CLIPBOARD)?.cardAlpha)
        assertEquals(
            first.refId,
            settings.panelByRefId(singlePanelRefId(SinglePanelIds.CLIPBOARD))?.refId,
        )
    }
}
