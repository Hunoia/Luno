package hunoia.luno.config.model

import androidx.annotation.Keep
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

/**
 * 触钮的方向角度布局：扇区数量 = [boundaries] 个数（1..8）。
 *
 * 改方向数时用 [withCount]（保留槽 0 起点、等分圆周）；逐边界拖动仍走
 * [copyDirectionAngleBoundary]，允许把某个扇区宽度拖成 0 来关闭这个方向。
 */
@Serializable
@Keep
data class GestureButtonAngle(
    val boundaries: List<Float> = DirectionLayoutDefaults.DefaultBoundaries,
) {
    init {
        validateDirectionBoundaries(boundaries)
    }

    val count: Int get() = boundaries.size

    fun directionOf(offset: Offset): Int = directionSlotOf(boundaries, offset)

    fun slotWidthDegrees(index: Int): Float = sectorWidthDegrees(boundaries, index)

    fun slotCenterDegrees(index: Int): Float = sectorCenterDegrees(boundaries, index)

    fun zeroWidthSlots(): Set<Int> = zeroWidthSlots(boundaries)

    /**
     * 改方向数：以槽 0 的**中心角**为旋转锚点等分圆周，原有方向尽量留在原位；
     * 4→8 时扇区中心正好落在八向罗盘上（上/右上/…）。数量不变时返回自身。
     */
    fun withCount(count: Int): GestureButtonAngle {
        if (count == boundaries.size) return this
        val anchor = sectorCenterAngle(boundaries, 0)
        return GestureButtonAngle(evenBoundaries(count, anchor - 0.5f / count))
    }
}

fun GestureButtonAngle.copyNewNoGap(index: Int, newBoundary: Float): GestureButtonAngle =
    copy(boundaries = copyDirectionAngleBoundary(boundaries, index, newBoundary))
