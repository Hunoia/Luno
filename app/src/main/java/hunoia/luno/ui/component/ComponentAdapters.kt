package hunoia.luno.ui.component

import android.graphics.Color as AndroidColor
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.core.domain.Capability
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.ui.component.actionTextCompose

/**
 * 手势按钮/子手势的标记色：`colorInt` 为透明时跟随主题主色。
 *
 * 与 `SideGestureContainer` 区分——那里需要 null（未设置），不是主题色。
 */
fun gestureButtonMarkColor(colorInt: Int, primary: Color, alpha: Float = 1f): Color =
    if (colorInt == AndroidColor.TRANSPARENT) {
        primary.copy(alpha = alpha)
    } else {
        // alpha 直用颜色选择页定的值，不被调用方覆盖。
        Color(colorInt)
    }

@Composable
fun gestureButtonMarkColor(colorInt: Int, alpha: Float = 1f): Color =
    gestureButtonMarkColor(colorInt, MaterialTheme.colorScheme.primary, alpha)

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

@get:StringRes
val Capability.displayNameRes: Int
    get() = when (this) {
        Capability.Accessibility -> R.string.action_capability_accessibility
        Capability.Shizuku -> R.string.action_capability_shizuku
        Capability.Notification -> R.string.action_capability_notification
        Capability.None -> R.string.action_none
    }

@Composable
fun GestureButton.buttonTextCompose(): String {
    if (name.isNotEmpty()) return name
    return stringResource(id = R.string.gesture_button)
}

@Composable
fun GestureButton.actionTextCompose(): String {
    var text = ""
    val slideActionText = slideActions.actionTextCompose()
    if (slideActionText.isNotEmpty()) {
        text += slideActionText
    }
    val longSlideActionText = longSlideActions.actionTextCompose()
    if (longSlideActionText.isNotEmpty()) {
        text += if (text.isEmpty()) {
            longSlideActionText
        } else {
            ",$longSlideActionText"
        }
    }
    return text
}

@get:StringRes
val GestureDirection.displayNameRes: Int
    get() = when (this) {
        GestureDirection.Up -> R.string.top
        GestureDirection.Down -> R.string.bottom
        GestureDirection.Left -> R.string.left
        GestureDirection.Right -> R.string.right
    }

@get:StringRes
val GestureDirection.slideToRes: Int
    get() = when (this) {
        GestureDirection.Left -> R.string.slide_to_left
        GestureDirection.Up -> R.string.slide_to_top
        GestureDirection.Right -> R.string.slide_to_right
        GestureDirection.Down -> R.string.slide_to_bottom
    }

@Composable
fun GestureDirection.slideToLabel(): String = stringResource(slideToRes)
