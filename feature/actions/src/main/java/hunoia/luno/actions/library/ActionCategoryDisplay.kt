package hunoia.luno.actions.library

import androidx.annotation.StringRes
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.actions.R

/** 选择页分类的展示名。 */
@get:StringRes
val ActionCategory.displayNameRes: Int
    get() = when (this) {
        ActionCategory.NONE -> R.string.action_none
        ActionCategory.APP -> R.string.action_category_app
        ActionCategory.TEXT -> R.string.action_category_text
        ActionCategory.SYSTEM -> R.string.action_category_system
        ActionCategory.MEDIA -> R.string.action_category_media
        ActionCategory.RECOGNITION -> R.string.action_category_recognition
        ActionCategory.NAVIGATION -> R.string.action_category_navigation
        ActionCategory.UI -> R.string.action_category_ui
        ActionCategory.VISIBILITY -> R.string.action_category_visibility
        ActionCategory.GESTURE_TOOL -> R.string.action_category_gesture_tool
        ActionCategory.ANNOTATION -> R.string.action_category_annotation
        ActionCategory.PANEL -> R.string.action_category_panel
        ActionCategory.SUB_GESTURE -> R.string.action_category_sub_gesture
    }
