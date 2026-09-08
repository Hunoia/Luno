package hunoia.luno.config.model

import androidx.annotation.Keep

@Keep
enum class GestureDirection {
    Left,
    UpLeft,
    Up,
    UpRight,
    Right,
    DownRight,
    Down,
    DownLeft;

    companion object {
        val entriesInClockwiseOrder = listOf(Right, DownRight, Down, DownLeft, Left, UpLeft, Up, UpRight)
    }
}
