package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotateViewTransformTest {

    private val fit = ContainFit.of(
        imageWidth = 1000f,
        imageHeight = 2000f,
        viewWidth = 1000f,
        viewHeight = 1000f,
    )

    @Test
    fun `zoom is clamped and reset clears pan`() {
        val tooBig = AnnotateViewTransform(zoom = 99f, pan = Pt(3f, 4f)).sanitized()
        assertEquals(AnnotateOps.MAX_VIEW_ZOOM, tooBig.zoom)
        assertEquals(Pt(3f, 4f), tooBig.pan)

        val tooSmall = AnnotateViewTransform(zoom = 0f).sanitized()
        assertEquals(AnnotateOps.MIN_VIEW_ZOOM, tooSmall.zoom)

        val reset = AnnotateViewTransform(2f, Pt(10f, 20f)).reset()
        assertEquals(1f, reset.zoom)
        assertEquals(Pt(0f, 0f), reset.pan)
    }

    @Test
    fun `toggle switches between safe zoom and full fit`() {
        val safe = 0.75f
        val fromFull = AnnotateViewTransform().toggled(safe)
        assertEquals(safe, fromFull.zoom, 0.001f)
        assertEquals(Pt(0f, 0f), fromFull.pan)

        val backToFull = fromFull.toggled(safe)
        assertEquals(1f, backToFull.zoom)
        assertEquals(Pt(0f, 0f), backToFull.pan)
    }

    @Test
    fun `applyTo matches graphicsLayer zoom-around-center plus pan`() {
        val transform = AnnotateViewTransform(zoom = 1.7f, pan = Pt(42f, -18f))
        val transformed = transform.applyTo(fit)

        // graphicsLayer 的语义：绕视图中心缩放，再平移。
        val centerX = fit.viewWidth / 2f
        val centerY = fit.viewHeight / 2f
        fun layer(view: Pt): Pt = Pt(
            x = centerX + (view.x - centerX) * transform.zoom + transform.pan.x,
            y = centerY + (view.y - centerY) * transform.zoom + transform.pan.y,
        )

        for (point in listOf(Pt(0f, 0f), Pt(500f, 1000f), Pt(1000f, 2000f))) {
            val expected = layer(fit.imageToView(point))
            val actual = transformed.imageToView(point)
            assertEquals(expected.x, actual.x, 0.01f)
            assertEquals(expected.y, actual.y, 0.01f)
        }
    }

    @Test
    fun `round trip view to image is stable under transform`() {
        val transformed = AnnotateViewTransform(zoom = 2.3f, pan = Pt(-30f, 75f)).applyTo(fit)
        val image = Pt(321f, 456f)
        val back = transformed.viewToImage(transformed.imageToView(image))
        assertEquals(image.x, back.x, 0.01f)
        assertEquals(image.y, back.y, 0.01f)
        assertTrue(transformed.scale > fit.scale)
    }
}
