package hunoia.luno.runtime.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SurfaceControllerTest {

    private class FakeSurface(
        override val surfaceId: String,
        override val surfaceKind: SurfaceKind,
    ) : SurfaceHandle {
        var softHidden = false
        var restored = false
        var detached = false

        override fun softHide() {
            softHidden = true
        }

        override fun restore() {
            restored = true
        }

        override fun detach() {
            detached = true
            SurfaceController.unregister(surfaceId)
        }
    }

    @Test
    fun `bulk operations are scoped by kind`() {
        val panel = FakeSurface("panel", SurfaceKind.PANEL)
        val ball = FakeSurface("ball", SurfaceKind.FLOAT_BALL)
        SurfaceController.register(panel)
        SurfaceController.register(ball)
        try {
            SurfaceController.softHideAll(SurfaceKind.PANEL)
            assertTrue(panel.softHidden)
            assertTrue(!ball.softHidden)

            SurfaceController.restoreAll(SurfaceKind.PANEL)
            assertTrue(panel.restored)

            SurfaceController.detachAll(SurfaceKind.PANEL)
            assertTrue(panel.detached)
            assertTrue(!ball.detached)
            assertEquals(setOf("ball"), SurfaceController.activeIds())
        } finally {
            SurfaceController.unregister("panel")
            SurfaceController.unregister("ball")
        }
    }

    @Test
    fun `duplicate surface id fails without replacing existing`() {
        val first = FakeSurface("dup", SurfaceKind.PANEL)
        val second = FakeSurface("dup", SurfaceKind.TRANSIENT)
        SurfaceController.register(first)
        try {
            try {
                SurfaceController.register(second)
                fail("重复 surface id 应直接失败")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("duplicate"))
            }
            SurfaceController.softHideAll()
            assertTrue(first.softHidden)
            assertTrue(!second.softHidden)
        } finally {
            SurfaceController.unregister("dup")
        }
    }
}
