package hunoia.luno.ui.component

import androidx.annotation.StringRes
import hunoia.luno.R
import hunoia.luno.action.definition.ActionCategory

@get:StringRes
val ActionCategory.displayNameRes: Int
    get() = when (this) {
        ActionCategory.NAVIGATION -> R.string.action_category_navigation
        ActionCategory.MEDIA -> R.string.action_category_media
        ActionCategory.SYSTEM -> R.string.action_category_system
        ActionCategory.WINDOW -> R.string.action_category_window
        ActionCategory.LAUNCHER -> R.string.action_category_launch
        ActionCategory.SUB_GESTURE -> R.string.sub_gesture
        ActionCategory.TOOL -> R.string.action_category_tool
        ActionCategory.NONE -> R.string.action_none
    }
