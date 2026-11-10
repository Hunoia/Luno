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

    @Test
    fun fullCircleSpreadsItemsEvenlyWithoutOverlap() {
        val sector = ActionPanelSector(sweepDegrees = 360f, centerDegrees = 0f)
        val parent = Size(2000f, 2000f)
        val origin = Offset(1000f, 1000f)
        val itemSize = 132f
        val gap = 16.5f
        for (maxAngle in listOf(360f, 355f)) {
            val offsets = (0 until 8).map { index ->
                actionPanelItemOffset(
                    parent, origin, sector, actionCount = 8, index = index,
                    itemSizePx = itemSize, spreadSpacing = 1.12f, itemGapPx = gap,
                    maxAngleDegrees = maxAngle,
                )
            }
            val minDistance = offsets.indices.flatMap { i ->
                (i + 1 until offsets.size).map { j -> (offsets[i] - offsets[j]).getDistance() }
            }.min()
            assertTrue(
                "maxAngle=$maxAngle 最小中心距 $minDistance 小于 ${itemSize + gap}",
                minDistance >= itemSize + gap - 0.001f,
            )
        }
    }

    @Test
    fun capacityNeverExceedsWhatTheArcFits() {
        // 半径 200、中心距 148.5 时一步约 43.7°：11° 的弧只够 1 个位置（旧实现会算成 2）。
        assertEquals(1, arcLayerCapacity(radius = 200f, centerDistance = 148.5f, arcLength = 11f))
        assertEquals(0, arcLayerCapacity(radius = 200f, centerDistance = 148.5f, arcLength = 0f))
        assertEquals(4, arcLayerCapacity(radius = 200f, centerDistance = 148.5f, arcLength = 148f))
    }

    @Test
    fun marginsNearScreenEdgeDoNotCollapseItems() {
        // 触发点在左边缘、手势朝下（90°），加边距后中心方向在半径处已经出界：
        // 旧实现会把弧算成 180° 再把图标夹到屏边，造成重叠。
        val parent = Size(1080f, 2400f)
        val inset = 12f * 2.75f
        val insets = ActionPanelInsets(inset, inset, inset, inset)
        val itemSize = 132f
        val gap = 16.5f
        val origin = actionPanelOrigin(parent, Offset(0f, 300f), itemSize, insets)
        val sector = ActionPanelSector(180f, 90f)
        val offsets = (0 until 3).map { index ->
            actionPanelItemOffset(
                parent, origin, sector, actionCount = 3, index = index,
                itemSizePx = itemSize, spreadSpacing = 1.12f, itemGapPx = gap,
                maxAngleDegrees = 90f, insets = insets,
            )
        }
        assertNoOverlap(offsets, itemSize + gap)
    }

    @Test
    fun outwardFacingFanShiftsInwardInsteadOfPilingUp() {
        // 朝屏幕上方展开 20 个动作：外层中心方向放不下时逐层平移到最近可用方向，不能叠。
        val parent = Size(1080f, 2400f)
        val inset = 12f * 2.75f
        val insets = ActionPanelInsets(inset, inset, inset, inset)
        val itemSize = 132f
        val gap = 16.5f
        val origin = actionPanelOrigin(parent, Offset(300f, 700f), itemSize, insets)
        val sector = ActionPanelSector(180f, 270f)
        val offsets = (0 until 20).map { index ->
            actionPanelItemOffset(
                parent, origin, sector, actionCount = 20, index = index,
                itemSizePx = itemSize, spreadSpacing = 1.12f, itemGapPx = gap,
                maxAngleDegrees = 90f, insets = insets,
            )
        }
        assertNoOverlap(offsets, itemSize + gap)
    }

    private fun assertNoOverlap(offsets: List<Offset>, minDistance: Float) {
        offsets.indices.forEach { i ->
            (i + 1 until offsets.size).forEach { j ->
                val distance = (offsets[i] - offsets[j]).getDistance()
                assertTrue(
                    "第 $i 与第 $j 项中心距 $distance 小于 $minDistance",
                    distance >= minDistance - 0.001f,
                )
            }
        }
    }
}
