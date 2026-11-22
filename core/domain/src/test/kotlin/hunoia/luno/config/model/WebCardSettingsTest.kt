package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WebCardSettingsTest {

    @Test
    fun perOrientationAccessors() {
        val settings = WebCardSettings(
            portraitWidthDp = 360f,
            portraitHeightDp = 640f,
            landscapeWidthDp = 640f,
            landscapeHeightDp = 360f,
        )
        assertEquals(360f, settings.widthDp(landscape = false), 0f)
        assertEquals(640f, settings.heightDp(landscape = false), 0f)
        assertEquals(640f, settings.widthDp(landscape = true), 0f)
        assertEquals(360f, settings.heightDp(landscape = true), 0f)
    }

    @Test
    fun withSize_writesOnlyRequestedOrientation() {
        val base = WebCardSettings()
        val portrait = base.withSize(landscape = false, widthDp = 400f, heightDp = 700f)
        assertEquals(400f, portrait.portraitWidthDp, 0f)
        assertEquals(700f, portrait.portraitHeightDp, 0f)
        assertEquals(base.landscapeWidthDp, portrait.landscapeWidthDp, 0f)
        assertEquals(base.landscapeHeightDp, portrait.landscapeHeightDp, 0f)

        val landscape = base.withSize(landscape = true, widthDp = 800f, heightDp = 500f)
        assertEquals(base.portraitWidthDp, landscape.portraitWidthDp, 0f)
        assertEquals(base.portraitHeightDp, landscape.portraitHeightDp, 0f)
        assertEquals(800f, landscape.landscapeWidthDp, 0f)
        assertEquals(500f, landscape.landscapeHeightDp, 0f)
    }

    @Test
    fun coerced_clampsOutOfRangeSizes() {
        val coerced = WebCardSettings(
            portraitWidthDp = 10f,
            portraitHeightDp = 99999f,
            landscapeWidthDp = -5f,
            landscapeHeightDp = 0f,
        ).coerced()
        assertEquals(WebCardWidthRange.start, coerced.portraitWidthDp, 0f)
        assertEquals(WebCardHeightRange.endInclusive, coerced.portraitHeightDp, 0f)
        assertEquals(WebCardWidthRange.start, coerced.landscapeWidthDp, 0f)
        assertEquals(WebCardHeightRange.start, coerced.landscapeHeightDp, 0f)
    }

    @Test
    fun coerced_keepsValidValuesUntouched() {
        val valid = WebCardSettings(
            portraitWidthDp = 360f,
            portraitHeightDp = 640f,
            landscapeWidthDp = 640f,
            landscapeHeightDp = 360f,
        )
        assertEquals(valid, valid.coerced())
    }
}
