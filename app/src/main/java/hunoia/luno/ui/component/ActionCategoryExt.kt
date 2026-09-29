package hunoia.luno.ui.component

import androidx.annotation.StringRes
import hunoia.luno.R
import hunoia.luno.action.definition.ActionCategory

@get:StringRes
val ActionCategory.displayNameRes: Int
    get() = when (this) {
        ActionCategory.NAVIGATION -> R.string.action_category_navigation
        ActionCategory.SYSTEM -> R.string.action_category_system
        ActionCategory.SUB_GESTURE -> R.string.sub_gesture
        ActionCategory.TOOL -> R.string.action_category_tool
        ActionCategory.NONE -> R.string.action_none
        ActionCategory.APP -> R.string.action_category_app
        ActionCategory.INTENT -> R.string.action_category_intent
        ActionCategory.ACCESSIBILITY -> R.string.action_category_accessibility
        ActionCategory.PACKAGE -> R.string.action_category_package
        ActionCategory.SETTINGS -> R.string.action_category_settings
        ActionCategory.SYSTEMCMD -> R.string.action_category_system_cmd
        ActionCategory.SHELL -> R.string.action_category_shell
        ActionCategory.INTERNAL -> R.string.action_category_internal
    }
