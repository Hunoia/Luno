package hunoia.luno.ui.component.input

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange

/**
 * 受控字符串 → [TextFieldState] 的桥。
 *
 * foundation 1.13.0 起 `BasicTextField(value, onValueChange)` 弃用，state 是唯一事实源：
 * 内部编辑经 [snapshotFlow] 上报给 [onValueChange]，外部写入（吸管取色、代码改值）在下一帧回写
 * state。文本已等于目标值时不重复写入，避免和 IME 合成态打架。
 *
 * 外部值若做了归一化（过滤 / 大写），等长时保住光标位置，长度变化（过滤掉字符）才放到末尾。
 */
@Composable
fun rememberControlledTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(value)
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != latestValue) latestOnValueChange(text)
        }
    }
    LaunchedEffect(state, value) {
        val current = state.text.toString()
        if (current == value) return@LaunchedEffect
        // 只做大小写 / 等长归一化时保住光标位置，否则（过滤掉字符）放到末尾，与旧的
        // value 重载口径一致：文本被外部改写时光标不会停在中间。
        val keepCaret = current.length == value.length
        val caret = state.selection.start
        state.setTextAndPlaceCursorAtEnd(value)
        if (keepCaret) {
            state.edit { selection = TextRange(caret.coerceIn(0, value.length)) }
        }
    }
    return state
}
