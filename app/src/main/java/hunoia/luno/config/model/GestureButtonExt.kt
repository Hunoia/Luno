package hunoia.luno.config.model

import hunoia.luno.R
import hunoia.luno.core.AppContext

internal fun GestureButton.resolveDisplayName(): String {
    if (name.isNotEmpty()) return name
    return AppContext.get().getString(R.string.gesture_button)
}
