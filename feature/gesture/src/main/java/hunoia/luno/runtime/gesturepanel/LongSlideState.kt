package hunoia.luno.runtime.gesturepanel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * 「按住后拖动」类会话的公共形状：起点、当前手指位置、重置。
 * 动作面板与音量刮擦都按这套语义收尾（`onDragEnd` 即结算）。
 */
abstract class LongSlideState {

    var origin: Offset by mutableStateOf(Offset.Unspecified)
        protected set
    var finger: Offset by mutableStateOf(Offset.Unspecified)
        protected set

    open fun onDragStart(offset: Offset) {
        origin = offset
        finger = offset
    }

    open fun onDrag(dragAmount: Offset) {
        finger += dragAmount
    }

    open fun onDragEnd() {
        reset()
    }

    open fun onDragCancel() {
        reset()
    }

    protected open fun reset() {
        origin = Offset.Unspecified
        finger = Offset.Unspecified
    }
}
