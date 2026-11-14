package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelAppearanceTest {

    @Test
    fun `panel follows global appearance when override is null`() {
        val global = PanelAppearance(cardAlpha = 0.4f, blurRadiusDp = 0f)
        val panel = GridPanel()

        assertEquals(global, panel.effectiveAppearance(global))
    }

    @Test
    fun `panel override wins over global appearance`() {
        val global = PanelAppearance(cardAlpha = 0.4f)
        val own = PanelAppearance(cardAlpha = 0.9f, blurRadiusDp = 12f)
        val panel = GridPanel(appearance = own)

        assertEquals(own, panel.effectiveAppearance(global))
    }

    @Test
    fun `placement is stored per orientation`() {
        val portrait = SurfacePlacement(sideMarginDp = 4f)
        val landscape = SurfacePlacement(sideMarginDp = 24f)
        val appearance = PanelAppearance(
            portraitPlacement = portrait,
            landscapePlacement = landscape,
        )

        assertEquals(portrait, appearance.placement(landscape = false))
        assertEquals(landscape, appearance.placement(landscape = true))
        assertEquals(
            SurfacePlacement(sideMarginDp = 8f),
            appearance.withPlacement(landscape = false, SurfacePlacement(sideMarginDp = 8f)).portraitPlacement,
        )
    }
}
