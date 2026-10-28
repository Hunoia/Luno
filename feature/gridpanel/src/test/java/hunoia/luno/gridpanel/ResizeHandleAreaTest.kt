package hunoia.luno.gridpanel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import hunoia.luno.gridpanel.gesture.inResizeHandleArea
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeHandleAreaTest {

    private val size = IntSize(100, 100)

    @Test
    fun `bottom right corner hits`() {
        assertTrue(inResizeHandleArea(Offset(95f, 95f), size, hitPx = 32f))
        assertTrue(inResizeHandleArea(Offset(68f, 68f), size, hitPx = 32f))
    }

    @Test
    fun `center and single axis miss`() {
        assertFalse(inResizeHandleArea(Offset(50f, 50f), size, hitPx = 32f))
        assertFalse(inResizeHandleArea(Offset(95f, 50f), size, hitPx = 32f))
        assertFalse(inResizeHandleArea(Offset(50f, 95f), size, hitPx = 32f))
    }

    @Test
    fun `degenerate size never hits`() {
        assertFalse(inResizeHandleArea(Offset(0f, 0f), IntSize(0, 0), hitPx = 32f))
    }
}
