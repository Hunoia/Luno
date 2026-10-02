package hunoia.luno.runtime.environment

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.VolumeChange
import hunoia.luno.runtime.condition.nowMinuteOfDay

class EnvironmentTracker(
    private val getCurrentPackageName: () -> String,
    private val nowInLauncher: () -> Boolean,
    private val findFocusedNode: () -> AccessibilityNodeInfo? = { null },
    private val nowMs: () -> Long = { android.os.SystemClock.uptimeMillis() },
    val onKeyboardStateChanged: (Boolean) -> Unit = {},
) {
    var orientation = Configuration.ORIENTATION_PORTRAIT
        private set
    var isNowInLockScreenPage = false
    var isKeyboardInputActive = false
    var currentPackageName: String = ""
        private set

    private var pendingAppChangedTo: String? = null
    private var pendingScreenEvent: ScreenEventType? = null
    private var pendingChargingChangedTo: Boolean? = null
    private var pendingVolumeChange: VolumeChange? = null

    private var lastFocusQueryMs = 0L
    private var lastFocusQueryResult = false

    fun onOrientationChanged(newOrientation: Int) {
        orientation = newOrientation
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { updateKeyboardInputState(it) }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null && packageName != currentPackageName) {
                currentPackageName = packageName
                pendingAppChangedTo = packageName
            }
        }
    }

    fun onScreenEvent(type: ScreenEventType) {
        pendingScreenEvent = type
    }

    fun onChargingChanged(charging: Boolean) {
        pendingChargingChangedTo = charging
    }

    fun onVolumeChanged(change: VolumeChange) {
        pendingVolumeChange = change
    }

    fun updateKeyboardActive(active: Boolean) {
        if (isKeyboardInputActive == active) return
        isKeyboardInputActive = active
        onKeyboardStateChanged(active)
    }

    fun buildRuntimeState(
        hiddenGestureButtons: Map<String, Long>,
        isCharging: Boolean = false,
        batteryLevel: Int = -1,
        networkType: NetworkType = NetworkType.NONE,
        headphonesConnected: Boolean = false,
        bluetoothAdapterOn: Boolean? = null,
        bluetoothAudioConnected: Boolean? = null,
        airplaneMode: Boolean = false,
        isRingerSilent: Boolean = false,
        volumePercent: Map<AudioStream, Int> = emptyMap(),
        consumePulses: Boolean = true,
    ): GestureRuntimeState {
        val appChangedTo = if (consumePulses) pendingAppChangedTo else null
        val screenEvent = if (consumePulses) pendingScreenEvent else null
        val chargingChangedTo = if (consumePulses) pendingChargingChangedTo else null
        val volumeChanged = if (consumePulses) pendingVolumeChange else null
        if (consumePulses) {
            pendingAppChangedTo = null
            pendingScreenEvent = null
            pendingChargingChangedTo = null
            pendingVolumeChange = null
        }
        return GestureRuntimeState(
            currentPackageName = getCurrentPackageName(),
            isNowInLockScreenPage = isNowInLockScreenPage,
            isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE,
            isInLauncher = nowInLauncher(),
            isKeyboardInputActive = isKeyboardInputActive,
            hiddenGestureButtons = hiddenGestureButtons,
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            minuteOfDay = nowMinuteOfDay(),
            networkType = networkType,
            headphonesConnected = headphonesConnected,
            bluetoothAdapterOn = bluetoothAdapterOn,
            bluetoothAudioConnected = bluetoothAudioConnected,
            airplaneMode = airplaneMode,
            isRingerSilent = isRingerSilent,
            volumePercent = volumePercent,
            appChangedTo = appChangedTo,
            screenEvent = screenEvent,
            chargingChangedTo = chargingChangedTo,
            volumeChanged = volumeChanged,
        )
    }

    private fun updateKeyboardInputState(event: AccessibilityEvent) {
        val active = when (event.eventType) {
            // event.source 由事件本身携带,查询它不产生 IPC,所以这三种不节流。
            // isEditableInput(source) 命中时也不会走到 findFocus。
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> isEditableInput(event.source) || hasEditableInputFocus()

            // 键盘弹出/收起必然带一次窗口变化(IME 窗口出现或消失),这条是检测延迟敏感的主信号,
            // 保持每次真实查询。
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> hasEditableInputFocus()

            // 全项目最高频的事件:任何应用滚动列表都是每秒几十次,与应用是否含输入框无关。
            // 而键盘状态在这条上永远只是兜底 —— 弹出与收起都由上面两种事件先覆盖到,
            // 所以节流这条不会让 isKeyboardInputActive 变慢,只省掉每秒几十次
            // rootInActiveWindow + findFocus 的跨进程 Binder 与 AccessibilityNodeInfo 分配。
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> hasEditableInputFocusThrottled()

            else -> return
        }
        updateKeyboardActive(active)
    }

    private fun hasEditableInputFocusThrottled(): Boolean {
        val now = nowMs()
        if (now - lastFocusQueryMs < FOCUS_QUERY_THROTTLE_MS) return lastFocusQueryResult
        val result = hasEditableInputFocus()
        lastFocusQueryMs = now
        lastFocusQueryResult = result
        return result
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
        /**
         * TYPE_WINDOW_CONTENT_CHANGED 上 findFocus 的最小查询间隔。
         * 150ms 远小于人能感知的键盘状态延迟,而这把每秒几十次 Binder 压到每秒不到 7 次。
         */
        const val FOCUS_QUERY_THROTTLE_MS = 150L
    }
}
