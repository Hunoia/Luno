package hunoia.luno.runtime.environment

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.runtime.GestureRuntimeState

class EnvironmentTracker(
    private val getCurrentPackageName: () -> String,
    private val nowInLauncher: () -> Boolean,
    private val findFocusedNode: () -> AccessibilityNodeInfo? = { null },
    val onKeyboardStateChanged: (Boolean) -> Unit = {},
) {
    var orientation = Configuration.ORIENTATION_PORTRAIT
        private set
    var isNowInLockScreenPage = false
    var isKeyboardInputActive = false
    var currentPackageName: String = ""
        private set
    var isMouseMode = false

    fun onOrientationChanged(newOrientation: Int) {
        orientation = newOrientation
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { updateKeyboardInputState(it) }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackageName = event.packageName?.toString() ?: currentPackageName
        }
    }

    fun updateKeyboardActive(active: Boolean) {
        if (isKeyboardInputActive == active) return
        isKeyboardInputActive = active
        onKeyboardStateChanged(active)
    }

    fun buildRuntimeState(
        hiddenGestureButtons: Map<String, Long>,
    ): GestureRuntimeState {
        return GestureRuntimeState(
            currentPackageName = getCurrentPackageName(),
            isNowInLockScreenPage = isNowInLockScreenPage,
            isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE,
            isInLauncher = nowInLauncher(),
            isKeyboardInputActive = isKeyboardInputActive,
            hiddenGestureButtons = hiddenGestureButtons,
            isMouseMode = isMouseMode,
        )
    }

    private fun updateKeyboardInputState(event: AccessibilityEvent) {
        val active = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> isEditableInput(event.source) || hasEditableInputFocus()
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> hasEditableInputFocus()
            else -> return
        }
        updateKeyboardActive(active)
    }

    private fun hasEditableInputFocus(): Boolean {
        val focused = findFocusedNode() ?: return false
        return isEditableInput(focused)
    }

    private fun isEditableInput(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val className = node.className?.toString().orEmpty()
        return node.isEditable ||
            className.endsWith("EditText") ||
            node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id }
    }
}
