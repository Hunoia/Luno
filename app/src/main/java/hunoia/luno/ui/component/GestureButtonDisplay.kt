package hunoia.luno.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.config.model.GestureButton

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
