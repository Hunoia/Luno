package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.DirectionLayoutDefaults
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.copyDirectionAngleBoundary
import hunoia.luno.config.model.evenBoundaries
import hunoia.luno.config.model.mirrorDirectionBoundaries
import hunoia.luno.config.model.sectorCenterDegrees
import hunoia.luno.config.model.sectorWidthDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方向角度布局：四象限、0°/360° 回绕、N=1..8、零宽扇区、边界拖动夹取与水平镜像。
 *
 * 槽位顺序 = 边界列表顺序；默认四扇区为 [Up, Left, Down, Right]。
 */
class GestureButtonAngleTest {

    private val angle = GestureButtonAngle()

    @Test
    fun `default sectors map to screen compass`() {
        // 0 = 正右、逆时针：槽 0 Up[45°,135°) 1 Left 2 Down 3 Right[315°,45°)
        assertEquals(0, angle.directionOf(Offset(0f, -10f)))
        assertEquals(1, angle.directionOf(Offset(-10f, 0f)))
        assertEquals(2, angle.directionOf(Offset(0f, 10f)))
        assertEquals(3, angle.directionOf(Offset(10f, 0f)))
    }

    @Test
    fun `sector boundaries wrap around zero`() {
        assertEquals(3, angle.directionOf(Offset(10f, -1f)))
        assertEquals(3, angle.directionOf(Offset(10f, 1f)))
    }

    @Test
    fun `sector widths sum to full circle`() {
        val total = angle.boundaries.indices.sumOf { angle.slotWidthDegrees(it).toDouble() }
        assertEquals(360.0, total, 0.01)
    }

    @Test
    fun `single direction ignores angle`() {
        val single = GestureButtonAngle(evenBoundaries(1))
        assertEquals(1, single.count)
        assertEquals(360f, single.slotWidthDegrees(0), 0.01f)
        assertEquals(0, single.directionOf(Offset(10f, 0f)))
        assertEquals(0, single.directionOf(Offset(-7f, 9f)))
    }

    @Test
    fun `count change keeps slot zero center and divides evenly`() {
        val eight = angle.withCount(8)
        assertEquals(8, eight.count)
        assertEquals(angle.slotCenterDegrees(0), eight.slotCenterDegrees(0), 0.01f)
        eight.boundaries.indices.forEach {
            assertEquals(45f, eight.slotWidthDegrees(it), 0.01f)
        }
        // 8 等分后每个扇区仍能命中一个槽，且不越界。
        repeat(16) { step ->
            val offset = Offset(
                x = kotlin.math.cos(step * Math.PI / 8).toFloat() * 10f,
                y = -kotlin.math.sin(step * Math.PI / 8).toFloat() * 10f,
            )
            assertTrue(eight.directionOf(offset) in 0..7)
        }
    }

    @Test
    fun `default layout by count stays compass aligned`() {
        val eight = GestureButtonAngle(DirectionLayoutDefaults.defaultBoundaries(8))
        assertEquals(90f, eight.slotCenterDegrees(0), 0.01f)
        eight.boundaries.indices.forEach { slot ->
            // 从正上方开始每 45° 一个中心（逆时针）。
            val expected = (90f + slot * 45f) % 360f
            assertEquals(expected, sectorCenterDegrees(eight.boundaries, slot), 0.01f)
        }
        // 4 向默认与历史默认完全一致。
        assertEquals(GestureButtonAngle().boundaries, DirectionLayoutDefaults.defaultBoundaries(4))
    }

    @Test
    fun `zero width sector is never returned`() {
        // 把槽 3（Right）压成 0°：起点与槽 0 起点重合。
        val collapsed = GestureButtonAngle(
            listOf(0.125f, 0.375f, 0.625f, 0.875f).toMutableList().apply { set(3, 0.125f) },
        )
        assertEquals(setOf(3), collapsed.zeroWidthSlots())
        assertNotEquals(3, collapsed.directionOf(Offset(10f, 0f)))
    }

    @Test
    fun `dragging a boundary is clamped between neighbours`() {
        val clamped = copyDirectionAngleBoundary(angle.boundaries, 0, 0.45f)
        assertEquals(0.375f, clamped[0], 0.0001f)

        val kept = copyDirectionAngleBoundary(angle.boundaries, 0, 0.1f)
        assertEquals(0.1f, kept[0], 0.0001f)

        val wrapped = copyDirectionAngleBoundary(angle.boundaries, 0, 1.1f)
        assertTrue(wrapped[0] in 0f..1f)
    }

    @Test
    fun `two sectors can be re-split freely`() {
        val two = evenBoundaries(2)
        val moved = copyDirectionAngleBoundary(two, 0, 0.6f)
        assertEquals(0.6f, moved[0], 0.0001f)
        assertEquals(two[1], moved[1], 0.0001f)
    }

    @Test
    fun `mirror flips sectors horizontally and reports slot mapping`() {
        val (mirrored, newSlotOfOld) = mirrorDirectionBoundaries(angle.boundaries)
        assertEquals(4, mirrored.size)
        angle.boundaries.indices.forEach { oldSlot ->
            val mirroredCenter = (180f - sectorCenterDegrees(angle.boundaries, oldSlot) + 360f) % 360f
            val actual = sectorCenterDegrees(mirrored, newSlotOfOld[oldSlot])
            assertEquals(mirroredCenter, actual, 0.01f)
        }
        // 默认布局镜像后：旧 Left(1) → 新槽 0（Right），旧 Right(3) → 新槽 2（Left），Up/Down 原地。
        assertEquals(0, newSlotOfOld[1])
        assertEquals(1, newSlotOfOld[0])
        assertEquals(2, newSlotOfOld[3])
        assertEquals(3, newSlotOfOld[2])
    }

    @Test
    fun `mirror keeps unequal sector widths`() {
        val uneven = GestureButtonAngle(listOf(0.0f, 0.1f, 0.4f, 0.75f))
        val (mirrored, newSlotOfOld) = mirrorDirectionBoundaries(uneven.boundaries)
        uneven.boundaries.indices.forEach { oldSlot ->
            val newSlot = newSlotOfOld[oldSlot]
            assertEquals(
                uneven.slotWidthDegrees(oldSlot),
                sectorWidthDegrees(mirrored, newSlot),
                0.01f,
            )
            val mirroredCenter = (180f - sectorCenterDegrees(uneven.boundaries, oldSlot) + 360f) % 360f
            assertEquals(mirroredCenter, sectorCenterDegrees(mirrored, newSlot), 0.01f)
        }
    }
}
