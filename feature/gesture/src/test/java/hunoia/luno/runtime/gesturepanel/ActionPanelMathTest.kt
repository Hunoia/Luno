package hunoia.luno.runtime.gesturepanel

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPanelMathTest {
    @Test
    fun horizontalSidesKeepConfiguredOrderAndMirrorGeometry() {
        val right = ActionPanelSector(sweepDegrees = 180f, centerDegrees = 0f)
        val left = ActionPanelSector(sweepDegrees = 180f, centerDegrees = 180f)

        assertEquals(0, actionPanelDisplayIndex(right, actionCount = 3, index = 0))
        assertEquals(0, actionPanelDisplayIndex(left, actionCount = 3, index = 0))

        val origin = androidx.compose.ui.geometry.Offset(300f, 400f)
        val parent = Size(800f, 800f)
        val rightOffset = actionPanelItemOffset(parent, origin, right, 3, 0, 48f, 1f, 6f)
        val leftOffset = actionPanelItemOffset(parent, origin, left, 3, 0, 48f, 1f, 6f)
        assertEquals(-rightOffset.x, leftOffset.x, 0.001f)
        assertEquals(rightOffset.y, leftOffset.y, 0.001f)
    }

    @Test
    fun verticalSidesRetainTheirExistingReversedOrder() {
        val top = ActionPanelSector(sweepDegrees = 180f, centerDegrees = 90f)
        val bottom = ActionPanelSector(sweepDegrees = 180f, centerDegrees = 270f)

        assertEquals(2, actionPanelDisplayIndex(top, actionCount = 3, index = 0))
        assertEquals(2, actionPanelDisplayIndex(bottom, actionCount = 3, index = 0))
    }

    @Test
    fun nearTouchFanKeepsFourToSixOnOneLayer() {
        val sector = ActionPanelSector(sweepDegrees = 180f, centerDegrees = 0f)
        val parent = Size(1000f, 1000f)
        val origin = Offset(500f, 500f)
        val offsets = (0 until 6).map { index ->
            actionPanelItemOffset(
                parent, origin, sector, actionCount = 6, index = index,
                itemSizePx = 48f, spreadSpacing = 1f, itemGapPx = 6f,
                maxAngleDegrees = 360f,
            )
        }
        val distances = offsets.map { hypot(it.x, it.y) }
        assertEquals(distances.first(), distances.last(), 0.001f)
    }

    @Test
    fun adaptiveFanFillsCornerLayerBeforeOpeningNext() {
        val sector = ActionPanelSector(sweepDegrees = 90f, centerDegrees = 45f)
        val parent = Size(1000f, 1000f)
        val origin = Offset(30f, 30f)
        val inner = actionPanelItemOffset(
            parent, origin, sector, actionCount = 7, index = 0,
            itemSizePx = 48f, spreadSpacing = 1f, itemGapPx = 6f,
        )
        val outer = actionPanelItemOffset(
            parent, origin, sector, actionCount = 7, index = 3,
            itemSizePx = 48f, spreadSpacing = 1f, itemGapPx = 6f,
        )
        assertTrue(hypot(outer.x, outer.y) > hypot(inner.x, inner.y))
    }

    @Test
    fun maxAngleLimitsAvailableArc() {
        val sector = ActionPanelSector(sweepDegrees = 180f, centerDegrees = 0f)
        val parent = Size(1000f, 1000f)
        val origin = Offset(500f, 500f)
        val first = actionPanelItemOffset(
            parent, origin, sector, actionCount = 2, index = 0,
            itemSizePx = 48f, spreadSpacing = 1f, itemGapPx = 6f,
            maxAngleDegrees = 90f,
        )
        val second = actionPanelItemOffset(
            parent, origin, sector, actionCount = 2, index = 1,
            itemSizePx = 48f, spreadSpacing = 1f, itemGapPx = 6f,
            maxAngleDegrees = 90f,
        )
        val firstAngle = Math.toDegrees(kotlin.math.atan2(first.y.toDouble(), first.x.toDouble()))
        val secondAngle = Math.toDegrees(kotlin.math.atan2(second.y.toDouble(), second.x.toDouble()))
        assertEquals(90.0, kotlin.math.abs(secondAngle - firstAngle), 0.5)
    }
}
