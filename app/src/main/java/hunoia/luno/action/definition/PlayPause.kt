package hunoia.luno.action.definition

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector



@Suppress("UnusedReceiverParameter")
val Icons.Filled.PlayPause: ImageVector
    get() {
        if (_playPause != null) {
            return _playPause ?: error("PlayPause icon is null")
        }
        _playPause = materialIcon(name = "Filled.PlayPause") {
            materialPath {
                moveTo(4.0f, 18.0f)
                lineToRelative(8.5f, -6.0f)
                lineTo(4.0f, 6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(14.0f, 6.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(18.0f, 6.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }
        return _playPause ?: error("PlayPause icon is null")
    }

private var _playPause: ImageVector? = null
