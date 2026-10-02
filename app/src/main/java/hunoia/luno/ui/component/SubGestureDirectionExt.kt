package hunoia.luno.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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

/** 「向左滑」这类方向文案，3 处 UI 各写了一遍，收到这里 */
@get:StringRes
val GestureDirection.slideToRes: Int
    get() = when (this) {
        GestureDirection.Left -> R.string.slide_to_left
        GestureDirection.UpLeft -> R.string.slide_to_top_left
        GestureDirection.Up -> R.string.slide_to_top
        GestureDirection.UpRight -> R.string.slide_to_top_right
        GestureDirection.Right -> R.string.slide_to_right
        GestureDirection.DownRight -> R.string.slide_to_bottom_right
        GestureDirection.Down -> R.string.slide_to_bottom
        GestureDirection.DownLeft -> R.string.slide_to_bottom_left
    }

@Composable
fun GestureDirection.slideToLabel(): String = stringResource(slideToRes)
