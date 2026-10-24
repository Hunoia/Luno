package hunoia.luno.config.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

internal object DirectionAngleDefaults {
    /** 四向扇区起点（归一化角度）：Right[-45°,45°)，Up[45°,135°)，Left[135°,225°)，Down[225°,315°）。 */
    val Boundaries = listOf(
        0.125f, 0.375f, 0.625f, 0.875f,
    )
}

internal val DirectionSectorDirections: List<GestureDirection> = listOf(
    GestureDirection.Up,
    GestureDirection.Left,
    GestureDirection.Down,
    GestureDirection.Right,
)

internal fun validateDirectionAngleBoundaries(boundaries: List<Float>) {
    require(boundaries.size == DirectionSectorDirections.size)
    require(boundaries.all { it >= 0f && it < 1f })
}

internal fun <T> directionOf(boundaries: List<Float>, directions: List<T>, offset: Offset): T {
    val norm = normalizedAngle(offset)
    val n = boundaries.size
    for (i in boundaries.indices) {
        val start = boundaries[i]
        val end = boundaries[(i + 1) % n]
        val inSector = if (start <= end) norm >= start && norm < end else norm >= start || norm < end
        if (inSector) return directions[i]
    }
    val candidates = boundaries.indices
        .filter { !isZeroWidthSector(boundaries, it) }
        .ifEmpty { boundaries.indices.toList() }
    val nearest = candidates.minByOrNull { i ->
        var d = norm - boundaries[i]
        if (d < 0f) d += 1f
        d
    } ?: 0
    return directions[nearest]
}

internal fun normalizedAngle(offset: Offset): Float = normalizedAngle(offset, Offset.Zero)

/** 归一化角度（[0,1)，0 = 正右、逆时针为正）：[center] 为旋转中心，缺省屏幕原点。 */
internal fun normalizedAngle(offset: Offset, center: Offset): Float {
    val angle = atan2(-(offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
    var norm = (angle / (2 * PI)).toFloat()
    if (norm < 0f) norm += 1f
    return norm
}

internal fun sectorWidth(boundaries: List<Float>, index: Int): Float {
    val start = boundaries[index]
    val end = boundaries[(index + 1) % boundaries.size]
    return if (end >= start) (end - start) * 360f else (end + 1f - start) * 360f
}

internal fun isZeroWidthSector(boundaries: List<Float>, index: Int): Boolean =
    sectorWidth(boundaries, index) == 0f

internal fun List<Float>.zeroWidthDirections(): Set<GestureDirection> =
    indices.filter { isZeroWidthSector(this, it) }.map { DirectionSectorDirections[it] }.toSet()

internal fun copyDirectionAngleBoundary(boundaries: List<Float>, index: Int, newBoundary: Float): List<Float> {
    val boundary = ((newBoundary % 1f) + 1f) % 1f
    val n = boundaries.size
    val previous = boundaries[(index + n - 1) % n]
    val next = boundaries[(index + 1) % n]
    val distToPrevious = minOf(abs(boundary - previous), abs(boundary + 1f - previous), abs(boundary - 1f - previous))
    val distToNext = minOf(abs(boundary - next), abs(boundary + 1f - next), abs(boundary - 1f - next))
    val inRange = if (previous < next) boundary in previous..next else boundary >= previous || boundary <= next
    val clamped = if (inRange) boundary else if (distToPrevious < distToNext) previous else next
    return boundaries.toMutableList().apply { set(index, clamped) }
}
