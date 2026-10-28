package hunoia.luno.config.model

import androidx.annotation.Keep
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class SubGestureAngle(
    val boundaries: List<Float> = DirectionAngleDefaults.Boundaries
) {
    init {
        validateDirectionAngleBoundaries(boundaries)
    }

    fun directionOf(offset: Offset): GestureDirection = directionOf(boundaries, SECTOR_DIRECTIONS, offset)

    fun sectorWidth(index: Int): Float = sectorWidth(boundaries, index)

    fun zeroWidthDirections(): Set<GestureDirection> = boundaries.zeroWidthDirections()

    companion object {
        val SECTOR_DIRECTIONS: List<GestureDirection> = DirectionSectorDirections
    }
}

fun SubGestureAngle.copyNewNoGap(index: Int, newP: Float): SubGestureAngle {
    return SubGestureAngle(boundaries = copyDirectionAngleBoundary(boundaries, index, newP))
}
