package hunoia.luno.service

import android.view.Gravity
import android.view.WindowManager
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonBounds
import hunoia.luno.runtime.overlay.WindowLayoutFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutFactoryTest {

    @Test
    fun updateGestureButton_bounds_matchesExpectedLayout() {
        val layout = WindowManager.LayoutParams()
        WindowLayoutFactory.updateGestureButton(
            lp = layout,
            button = GestureButton(
                id = "button",
                bounds = GestureButtonBounds(
                    x = 0.25f,
                    y = 0.1f,
                    width = 0.5f,
                    height = 0.75f,
                ),
            ),
            rootWidth = 1080,
            rootHeight = 2400,
        )
        assertEquals(540, layout.width)
        assertEquals(1800, layout.height)
        assertEquals(270, layout.x)
        assertEquals(240, layout.y)
        assertEquals(Gravity.LEFT or Gravity.TOP, layout.gravity)
    }

    @Test
    fun updateGestureButton_bottomEdge_usesRoundedEndBoundary() {
        val layout = WindowManager.LayoutParams()
        WindowLayoutFactory.updateGestureButton(
            lp = layout,
            button = GestureButton(
                id = "button",
                bounds = GestureButtonBounds(
                    x = 0.1f,
                    y = 0.8f,
                    width = 0.2f,
                    height = 0.2f,
                ),
            ),
            rootWidth = 100,
            rootHeight = 100,
        )
        assertEquals(20, layout.width)
        assertEquals(20, layout.height)
        assertEquals(10, layout.x)
        assertEquals(80, layout.y)
    }
}
