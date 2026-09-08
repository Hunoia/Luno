package hunoia.luno.runtime.button

import android.os.SystemClock
import hunoia.luno.config.model.GestureButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ButtonHideRuntime(
    private val scope: CoroutineScope,
    private val onStateChanged: () -> Unit,
) {
    private val hiddenButtons = mutableMapOf<String, Long>()

    fun hideTemporarily(button: GestureButton, delayMs: Long) {
        val key = button.id
        hiddenButtons[key] = SystemClock.uptimeMillis() + delayMs
        onStateChanged()
        scope.launch {
            delay(delayMs)
            if ((hiddenButtons[key] ?: 0L) <= SystemClock.uptimeMillis()) {
                hiddenButtons.remove(key)
                onStateChanged()
            }
        }
    }

    fun getSnapshot(): Map<String, Long> = hiddenButtons.toMap()
}
