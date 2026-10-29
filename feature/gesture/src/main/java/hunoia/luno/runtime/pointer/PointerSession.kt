package hunoia.luno.runtime.pointer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureSettings
import kotlin.math.roundToInt

/**
 * 面板内指针会话的状态机（按住类触发时启动）。
 *
 * 与渲染分离：状态在这里，光标由 `PointerCursor` 按 [position]/[settings]/[clickPulseKey] 画。
 * 分离的原因输入源有两个——无障碍按钮窗与内核触摸流——两者都要能驱动同一个会话。
 */
class PointerSession(
    initialSettings: GestureSettings.Pointer = GestureSettings.Pointer(),
    private val onStart: (GestureSettings.Pointer) -> Boolean,
    private val onEnd: () -> Unit,
    private val onActionAt: (Int, Int) -> Unit,
) {

    var isActive: Boolean by mutableStateOf(false)
        private set
    var position: Offset by mutableStateOf(pointerInitialPosition(initialSettings))
        private set
    var settings: GestureSettings.Pointer by mutableStateOf(initialSettings)
        private set
    var clickPulseKey: Int by mutableIntStateOf(0)
        private set

    private var touchPosition: Offset = Offset.Unspecified

    fun onSettingsChanged(next: GestureSettings.Pointer) {
        settings = next
    }

    fun start(pointerSettings: GestureSettings.Pointer, fingerPos: Offset): Boolean {
        if (isActive) return false
        settings = pointerSettings
        if (!onStart(settings)) return false
        position = pointerInitialPosition(settings)
        touchPosition = fingerPos
        isActive = true
        return true
    }

    fun onDrag(dragAmount: Offset): Boolean {
        if (!isActive) return false
        touchPosition += dragAmount
        position = movePointerCursor(position, dragAmount, settings)
        return true
    }

    fun onDragEnd() = finish(click = true)

    fun onDragCancel() = finish(click = false)

    private fun finish(click: Boolean) {
        if (!isActive) return
        if (click) {
            val target = position
            isActive = false
            touchPosition = Offset.Unspecified
            clickPulseKey += 1
            onActionAt(target.x.roundToInt(), target.y.roundToInt())
        } else {
            isActive = false
            touchPosition = Offset.Unspecified
            onEnd()
        }
    }
}
