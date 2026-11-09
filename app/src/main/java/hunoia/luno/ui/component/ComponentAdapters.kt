package hunoia.luno.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.actionTextCompose

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


