package hunoia.luno.actions.library

import androidx.annotation.StringRes
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.actions.R

/** 动作分类的展示名：分类定义在动作库，字符串也随之落在本模块。 */
@get:StringRes
val ActionCategory.displayNameRes: Int
    get() = when (this) {
        ActionCategory.NONE -> R.string.action_none
        ActionCategory.APP -> R.string.action_category_app
        ActionCategory.LINK_SHARE -> R.string.action_category_link_share
        ActionCategory.SYSTEM -> R.string.action_category_system
        ActionCategory.MEDIA -> R.string.action_category_media
        ActionCategory.CLIPBOARD -> R.string.action_category_clipboard
        ActionCategory.NAVIGATION -> R.string.action_category_navigation
        ActionCategory.OPERATION -> R.string.action_category_operation
        ActionCategory.UI -> R.string.action_category_ui
        ActionCategory.POWER -> R.string.action_category_power
        ActionCategory.VISIBILITY -> R.string.action_category_visibility
        ActionCategory.ENTRY -> R.string.action_category_entry
        ActionCategory.GESTURE_TOOL -> R.string.action_category_gesture_tool
        ActionCategory.ADVANCED -> R.string.action_category_advanced
        ActionCategory.ACTION_PANEL -> R.string.action_category_action_panel
        ActionCategory.GRID_PANEL -> R.string.action_category_grid_panel
        ActionCategory.SUB_GESTURE -> R.string.action_category_sub_gesture
    }
