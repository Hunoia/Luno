package hunoia.luno.ui.component

import androidx.annotation.StringRes
import hunoia.luno.R
import hunoia.luno.config.model.GestureDirection

@get:StringRes
val GestureDirection.displayNameRes: Int
    get() = when (this) {
        GestureDirection.Up -> R.string.top
        GestureDirection.Down -> R.string.bottom
        GestureDirection.Left -> R.string.left
        GestureDirection.Right -> R.string.right
        GestureDirection.UpLeft -> R.string.direction_up_left
        GestureDirection.UpRight -> R.string.direction_up_right
        GestureDirection.DownLeft -> R.string.direction_down_left
        GestureDirection.DownRight -> R.string.direction_down_right
    }
