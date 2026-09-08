package hunoia.luno.config.model

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import hunoia.luno.gesture.calcDirection
import hunoia.luno.gesture.mirrorHorizontal

class DirectionAngleTest {

    @Test
    fun gestureButtonAngle_defaultBoundaries_resolveEightDirections() {
        val angle = GestureButtonAngle()

        assertEquals(GestureDirection.Right, angle.directionOf(Offset(1f, 0f)))
        assertEquals(GestureDirection.UpRight, angle.directionOf(offsetAtDegrees(45f)))
        assertEquals(GestureDirection.Up, angle.directionOf(Offset(0f, -1f)))
        assertEquals(GestureDirection.UpLeft, angle.directionOf(offsetAtDegrees(135f)))
        assertEquals(GestureDirection.Left, angle.directionOf(Offset(-1f, 0f)))
        assertEquals(GestureDirection.DownLeft, angle.directionOf(offsetAtDegrees(225f)))
        assertEquals(GestureDirection.Down, angle.directionOf(Offset(0f, 1f)))
        assertEquals(GestureDirection.DownRight, angle.directionOf(offsetAtDegrees(315f)))
    }

    @Test
    fun gestureButtonAngle_customBoundary_changesDirection() {
        val angle = GestureButtonAngle().copyNewNoGap(0, 0.12f)

        assertEquals(GestureDirection.Right, angle.directionOf(offsetAtDegrees(30f)))
        assertEquals(GestureDirection.UpRight, angle.directionOf(offsetAtDegrees(50f)))
    }

    @Test
    fun subGestureAngle_usesSameDefaultBoundaries() {
        val angle = SubGestureAngle()

        assertEquals(GestureDirection.Right, angle.directionOf(Offset(1f, 0f)))
        assertEquals(GestureDirection.UpRight, angle.directionOf(offsetAtDegrees(45f)))
        assertEquals(GestureDirection.Up, angle.directionOf(Offset(0f, -1f)))
    }

    @Test
    fun gestureDirection_mirrorHorizontal_flipsHorizontalDirections() {
        assertEquals(GestureDirection.Right, GestureDirection.Left.mirrorHorizontal())
        assertEquals(GestureDirection.UpRight, GestureDirection.UpLeft.mirrorHorizontal())
        assertEquals(GestureDirection.Up, GestureDirection.Up.mirrorHorizontal())
        assertEquals(GestureDirection.DownLeft, GestureDirection.DownRight.mirrorHorizontal())
        assertEquals(GestureDirection.Down, GestureDirection.Down.mirrorHorizontal())
    }

    @Test
    fun calcDirection_withMirrorHorizontal_mapsPhysicalLeftToSourceRight() {
        val button = GestureButton(id = "test")

        assertEquals(
            GestureDirection.Right,
            calcDirection(button, Offset.Zero, Offset(-1f, 0f), mirrorHorizontal = true)
        )
    }

    private fun offsetAtDegrees(degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        return Offset(
            x = cos(radians).toFloat(),
            y = -sin(radians).toFloat(),
        )
    }
}
