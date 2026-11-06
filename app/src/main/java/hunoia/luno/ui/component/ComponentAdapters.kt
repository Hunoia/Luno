package hunoia.luno.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.core.domain.Capability
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.actionTextCompose

@get:StringRes
val Capability.displayNameRes: Int
    get() = when (this) {
        Capability.Accessibility -> R.string.action_capability_accessibility
        Capability.Shizuku -> R.string.action_capability_shizuku
        Capability.None -> hunoia.luno.actions.R.string.action_none
    }

@Composable
fun GestureButton.buttonTextCompose(): String {
    if (name.isNotEmpty()) return name
    return stringResource(id = hunoia.luno.core.data.R.string.gesture_button)
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


