package hunoia.luno.config.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

internal object DirectionAngleDefaults {
    val Boundaries = listOf(
        0.0625f, 0.1875f, 0.3125f, 0.4375f,
        0.5625f, 0.6875f, 0.8125f, 0.9375f,
    )
}

internal fun validateDirectionAngleBoundaries(boundaries: List<Float>) {
    require(boundaries.size == 8)
    require(boundaries.all { it >= 0f && it < 1f })
}

internal fun <T> directionOf(boundaries: List<Float>, directions: List<T>, offset: Offset): T {
    val norm = normalizedAngle(offset)
    for (i in boundaries.indices) {
        val start = boundaries[i]
        val end = boundaries[(i + 1) % 8]
        val inSector = if (start <= end) norm >= start && norm < end else norm >= start || norm < end
        if (inSector) return directions[i]
    }
    val nearest = boundaries.indices.minByOrNull { i ->
        var d = norm - boundaries[i]
        if (d < 0f) d += 1f
        d
    } ?: 0
    return directions[nearest]
}

internal fun normalizedAngle(offset: Offset): Float {
    val angle = atan2(-offset.y.toDouble(), offset.x.toDouble())
    var norm = (angle / (2 * PI)).toFloat()
    if (norm < 0f) norm += 1f
    return norm
}

internal fun sectorWidth(boundaries: List<Float>, index: Int): Float {
    val start = boundaries[index]
    val end = boundaries[(index + 1) % 8]
    return if (end >= start) (end - start) * 360f else (end + 1f - start) * 360f
}

internal fun copyDirectionAngleBoundary(boundaries: List<Float>, index: Int, newBoundary: Float): List<Float> {
    val boundary = ((newBoundary % 1f) + 1f) % 1f
    val previous = boundaries[(index + 7) % 8]
    val next = boundaries[(index + 1) % 8]
    val distToPrevious = minOf(abs(boundary - previous), abs(boundary + 1f - previous), abs(boundary - 1f - previous))
    val distToNext = minOf(abs(boundary - next), abs(boundary + 1f - next), abs(boundary - 1f - next))
    val inRange = if (previous < next) boundary in previous..next else boundary >= previous || boundary <= next
    val clamped = if (inRange) boundary else if (distToPrevious < distToNext) previous else next
    return boundaries.toMutableList().apply { set(index, clamped) }
}
