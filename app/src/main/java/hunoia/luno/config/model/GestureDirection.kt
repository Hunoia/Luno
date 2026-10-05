package hunoia.luno.config.model

import androidx.annotation.Keep

@Keep
enum class GestureDirection {
    Left,
    Up,
    Right,
    Down;

    companion object {
        val entriesInClockwiseOrder = listOf(Right, Down, Left, Up)
    }
}
