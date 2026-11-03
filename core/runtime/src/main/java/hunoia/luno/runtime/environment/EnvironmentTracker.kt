package hunoia.luno.runtime.environment

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.runtime.GestureRuntimeState

/**
 * 环境状态源：前台应用、屏幕、键盘等。
 * Main-thread only：所有写入方（a11y 事件回调、BroadcastReceiver）都发生在主线程；
 * 若新增从工作线程写入的调用方，必须先切主线程。
 */
class EnvironmentTracker(
    private val getCurrentPackageName: () -> String,
    private val nowInLauncher: () -> Boolean,
    private val findFocusedNode: () -> AccessibilityNodeInfo? = { null },
    /**
     * 真实 IME 窗口是否在屏（a11y windows 里的 TYPE_INPUT_METHOD）。
     * 返回 null 表示该 ROM/此刻拿不到窗口列表，退回输入焦点兜底。
     */
    private val imeWindowVisible: () -> Boolean? = { null },
    private val nowMs: () -> Long = { android.os.SystemClock.uptimeMillis() },
    val onKeyboardStateChanged: (Boolean) -> Unit = {},
) {
    var orientation = Configuration.ORIENTATION_PORTRAIT
        private set
    var isNowInLockScreenPage = false
    var isKeyboardInputActive = false
    var currentPackageName: String = ""
        private set

    private var lastKeyboardQueryMs = 0L

    fun onOrientationChanged(newOrientation: Int) {
        orientation = newOrientation
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { updateKeyboardInputState(it) }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null && packageName != currentPackageName) {
                currentPackageName = packageName
            }
        }
    }

    fun updateKeyboardActive(active: Boolean) {
        if (isKeyboardInputActive == active) return
        isKeyboardInputActive = active
        onKeyboardStateChanged(active)
    }

    /**
     * 启动播种键盘状态（与 orientation/锁屏播种同源的问题）：默认 false，重绑时键盘若已弹出
     * 可见性刷新会漏掉一次。用焦点节点可编辑性探测一次；root 未就绪时探不到、保持 false，
     * 下一次输入类事件自会纠正。
     */
    fun seedKeyboardInputActive() {
        isKeyboardInputActive = queryKeyboardVisible()
    }

    /**
     * 采样一次键盘可见性（主线程）：给「键盘在屏期间」的短轮询用 —— IME 可能被返回键/
     * 系统手势收起而不产生任何 a11y 事件，只靠事件会永远停在「弹出」。
     */
    fun pollKeyboardVisibility() {
        updateKeyboardActive(queryKeyboardVisible())
    }

    fun buildRuntimeState(
        hiddenGestureButtons: Map<String, Long>,
    ): GestureRuntimeState = GestureRuntimeState(
        currentPackageName = getCurrentPackageName(),
        isNowInLockScreenPage = isNowInLockScreenPage,
        isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE,
        isInLauncher = nowInLauncher(),
        isKeyboardInputActive = isKeyboardInputActive,
        hiddenGestureButtons = hiddenGestureButtons,
    )

    /**
     * 键盘状态以**真实 IME 窗口**为准（`TYPE_INPUT_METHOD` 是否在 windows 里），
     * 不再由「是否有 EditText 获得焦点」推断：输入框保留焦点但键盘被收起是常态，
     * 旧实现会把「键盘已收起」永远判成弹出。焦点判断仅作为拿不到窗口列表时的兜底。
     */
    private fun updateKeyboardInputState(event: AccessibilityEvent) {
        when (event.eventType) {
            // IME 窗口的出现/消失会带窗口状态/窗口列表变化：检测延迟敏感，不节流。
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                updateKeyboardActive(queryKeyboardVisible())

            // 打字时这类事件很密：节流窗口内复用上次采样，避免每次按键都查 windows/findFocus。
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val now = nowMs()
                if (now - lastKeyboardQueryMs < KEYBOARD_QUERY_THROTTLE_MS) return
                lastKeyboardQueryMs = now
                updateKeyboardActive(queryKeyboardVisible())
            }

            else -> return
        }
    }

    /** 真实 IME 窗口优先；窗口列表不可用时退回「可编辑节点获得焦点」。 */
    private fun queryKeyboardVisible(): Boolean {
        val fromWindows = imeWindowVisible()
        if (fromWindows != null) return fromWindows
        return hasEditableInputFocus()
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

    private companion object {
        /** 高频 a11y 事件上的键盘采样间隔：150ms 远小于可感知延迟，把每键一次的 Binder 查询压下去。 */
        const val KEYBOARD_QUERY_THROTTLE_MS = 150L
    }
}
