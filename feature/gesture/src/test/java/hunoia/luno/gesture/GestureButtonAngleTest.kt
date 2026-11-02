package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.copyDirectionAngleBoundary
import hunoia.luno.config.model.sectorWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方向角度基线：默认四扇区、0°/360° 回绕、零宽扇区与边界拖动夹取。
 *
 * 这些数学是识别与展示共用的地基，1–8 方向改造会继续往这里加用例。
 */
class GestureButtonAngleTest {

    private val angle = GestureButtonAngle()

    @Test
    fun `default sectors map to screen compass`() {
        // 0 = 正右、逆时针：Up[45°,135°) Left[135°,225°) Down[225°,315°) Right[315°,45°)
        assertEquals(GestureDirection.Right, angle.directionOf(Offset(10f, 0f)))
        assertEquals(GestureDirection.Up, angle.directionOf(Offset(0f, -10f)))
        assertEquals(GestureDirection.Left, angle.directionOf(Offset(-10f, 0f)))
        assertEquals(GestureDirection.Down, angle.directionOf(Offset(0f, 10f)))
    }

    @Test
    fun `sector boundaries wrap around zero`() {
        // 右侧扇区跨 0°：315°..45°
        assertEquals(GestureDirection.Right, angle.directionOf(Offset(10f, -1f)))
        assertEquals(GestureDirection.Right, angle.directionOf(Offset(10f, 1f)))
    }

    @Test
    fun `sector widths sum to full circle`() {
        val total = angle.boundaries.indices.sumOf { angle.sectorWidth(it).toDouble() }
        assertEquals(360.0, total, 0.01)
    }

    @Test
    fun `zero width sector is never returned`() {
        // 把 Right 扇区压成 0°（315° 与 45° 的起点重合）。
        val collapsed = GestureButtonAngle(listOf(0.125f, 0.375f, 0.625f, 0.875f).toMutableList().apply {
            set(3, 0.125f)
        })
        assertEquals(setOf(GestureDirection.Right), collapsed.zeroWidthDirections())
        assertFalse(collapsed.directionOf(Offset(10f, 0f)) == GestureDirection.Right)
    }

    @Test
    fun `dragging a boundary is clamped between neighbours`() {
        // index 0 的扇区夹在 0.875 与 0.375 之间；拖到 0.45 应回落到下一个边界。
        val clamped = copyDirectionAngleBoundary(angle.boundaries, 0, 0.45f)
        assertEquals(0.375f, clamped[0], 0.0001f)

        // 0.1 落在跨 0° 的合法区间内，保持原值。
        val kept = copyDirectionAngleBoundary(angle.boundaries, 0, 0.1f)
        assertEquals(0.1f, kept[0], 0.0001f)
    }

    @Test
    fun `boundary input is normalised into unit range`() {
        val wrapped = copyDirectionAngleBoundary(angle.boundaries, 0, 1.1f)
        assertTrue(wrapped[0] in 0f..1f)
    }
}
