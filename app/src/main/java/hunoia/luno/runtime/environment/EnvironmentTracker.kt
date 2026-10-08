package hunoia.luno.runtime.environment

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.ConnectionDirection
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.VolumeChange
import hunoia.luno.runtime.condition.NotificationPulse
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

    /**
     * 待处理脉冲：每种类型一个槽位。100ms 防抖窗口内同域多次脉冲只保留最后一个
     *（同域合并成一次评估，防抖优先的设计取舍）；不同域脉冲各有槽位，键会并集，
     * 互不覆盖。队列化需配套"N 次脉冲 = N 次评估"，不在当前防抖模型内。
     */
    private var pendingAppChangedTo: String? = null
    private var pendingScreenEvent: ScreenEventType? = null
    private var pendingChargingChangedTo: Boolean? = null
    private var pendingVolumeChange: VolumeChange? = null
    // v2 新增脉冲（P2 事件源接入）
    private var pendingNotification: NotificationPulse? = null
    private var pendingBluetoothChanged: ConnectionDirection? = null
    private var pendingHeadphonesChanged: ConnectionDirection? = null
    private var pendingScheduledTimeHit: Boolean = false
    private var pendingGesturePerformedId: String? = null

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

    /**
     * 丢弃全部待处理脉冲（含总闸关闭期间堆积的过期事件）。
     * 与 buildRuntimeState(consumePulses=true) 的清理共用 [clearPendingPulses]。
     */
    fun discardPendingPulses() = clearPendingPulses()

    fun onChargingChanged(charging: Boolean) {
        pendingChargingChangedTo = charging
    }

    fun onVolumeChanged(change: VolumeChange) {
        pendingVolumeChange = change
    }

    fun onNotificationPosted(pulse: NotificationPulse) {
        pendingNotification = pulse
    }

    fun onBluetoothChanged(direction: ConnectionDirection) {
        pendingBluetoothChanged = direction
    }

    fun onHeadphonesChanged(direction: ConnectionDirection) {
        pendingHeadphonesChanged = direction
    }

    fun onScheduledTimeHit() {
        pendingScheduledTimeHit = true
    }

    fun onGesturePerformed(buttonId: String) {
        pendingGesturePerformedId = buttonId
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
        /**
         * 只读不消费：把待处理脉冲暴露给取值方（参数模板需要看到 ${trigger.app} 等），
         * 但 pending 保持原样，消费权仍归自动化 —— 边沿命中语义依赖"谁评估谁消费"。
         */
        peekPulses: Boolean = false,
    ): GestureRuntimeState {
        val expose = consumePulses || peekPulses
        val appChangedTo = if (expose) pendingAppChangedTo else null
        val screenEvent = if (expose) pendingScreenEvent else null
        val chargingChangedTo = if (expose) pendingChargingChangedTo else null
        val volumeChanged = if (expose) pendingVolumeChange else null
        val notificationPosted = if (expose) pendingNotification else null
        val bluetoothChanged = if (expose) pendingBluetoothChanged else null
        val headphonesChanged = if (expose) pendingHeadphonesChanged else null
        val scheduledTimeHit = expose && pendingScheduledTimeHit
        val gesturePerformedId = if (expose) pendingGesturePerformedId else null
        if (consumePulses) {
            clearPendingPulses()
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
            notificationPosted = notificationPosted,
            bluetoothChanged = bluetoothChanged,
            headphonesChanged = headphonesChanged,
            scheduledTimeHit = scheduledTimeHit,
            gesturePerformedId = gesturePerformedId,
        )
    }

    private fun clearPendingPulses() {
        pendingAppChangedTo = null
        pendingScreenEvent = null
        pendingChargingChangedTo = null
        pendingVolumeChange = null
        pendingNotification = null
        pendingBluetoothChanged = null
        pendingHeadphonesChanged = null
        pendingScheduledTimeHit = false
        pendingGesturePerformedId = null
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
