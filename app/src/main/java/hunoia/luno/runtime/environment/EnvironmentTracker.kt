package hunoia.luno.runtime.environment

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.ConnectionDirection
import hunoia.luno.config.model.KeyboardEventType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.PlaybackDirection
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.VolumeChange
import hunoia.luno.runtime.condition.ClipboardPulse
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.condition.PackageChangePulse
import hunoia.luno.runtime.condition.SharePulse
import hunoia.luno.runtime.condition.nowDayOfWeek
import hunoia.luno.runtime.condition.nowMinuteOfDay

/**
 * 环境状态源：前台应用、屏幕事件、充电、音量、键盘等。
 * Main-thread only：所有写入方（a11y 事件回调、BroadcastReceiver、AudioDeviceCallback handler、
 * NotificationListener 回调）都发生在主线程，pending 槽位因此无需同步；
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

    /**
     * 待处理脉冲：每种类型一个槽位。100ms 防抖窗口内同域多次脉冲只保留最后一个（同域合并成一次评估）；
     * 不同域各有槽位，键会并集，互不覆盖。队列化需配套「N 次脉冲 = N 次评估」，不在当前防抖模型内。
     */
    private var pendingAppChangedTo: String? = null
    private var pendingScreenEvent: ScreenEventType? = null
    private var pendingChargingChangedTo: Boolean? = null
    private var pendingVolumeChange: VolumeChange? = null
    private var pendingNotification: NotificationPulse? = null
    private var pendingNotificationRemoved: NotificationPulse? = null
    private var pendingPackageChange: PackageChangePulse? = null
    private var pendingShare: SharePulse? = null
    private var pendingClipboard: ClipboardPulse? = null
    private var pendingIntervalDueKeys: Set<String> = emptySet()
    private var pendingBluetoothChanged: ConnectionDirection? = null
    private var pendingHeadphonesChanged: ConnectionDirection? = null
    private var pendingScheduledTimeHit: Boolean = false
    private var pendingGesturePerformedId: String? = null
    private var pendingGesturePerformedName: String? = null
    private var pendingKeyboardEvent: KeyboardEventType? = null
    private var pendingMusicChanged: PlaybackDirection? = null
    private var pendingTorchChangedTo: Boolean? = null
    private var pendingDndChangedTo: Boolean? = null

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
                pendingAppChangedTo = packageName
            }
        }
    }

    fun onScreenEvent(type: ScreenEventType) {
        pendingScreenEvent = type
    }

    /** 丢弃全部待处理脉冲（含总闸关闭期间堆积的过期事件），与 buildRuntimeState(consumePulses=true) 共用 clearPendingPulses。 */
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

    fun onNotificationRemoved(pulse: NotificationPulse) {
        pendingNotificationRemoved = pulse
    }

    fun onPackageChanged(pulse: PackageChangePulse) {
        pendingPackageChange = pulse
    }

    fun onShareReceived(pulse: SharePulse) {
        pendingShare = pulse
    }

    fun onClipboardChanged(pulse: ClipboardPulse) {
        pendingClipboard = pulse
    }

    /** ticker 命中：键集合在本轮求值后消费；多个间隔可同轮触发。 */
    fun onIntervalDue(keys: Set<String>) {
        if (keys.isEmpty()) return
        pendingIntervalDueKeys = pendingIntervalDueKeys + keys
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

    fun onGesturePerformed(buttonId: String, buttonName: String = "") {
        pendingGesturePerformedId = buttonId
        pendingGesturePerformedName = buttonName.takeIf { it.isNotBlank() }
    }

    fun onMusicChanged(direction: PlaybackDirection) {
        pendingMusicChanged = direction
    }

    fun onTorchChanged(on: Boolean) {
        pendingTorchChangedTo = on
    }

    fun onDndChanged(on: Boolean) {
        pendingDndChangedTo = on
    }

    fun updateKeyboardActive(active: Boolean) {
        if (isKeyboardInputActive == active) return
        isKeyboardInputActive = active
        pendingKeyboardEvent = if (active) KeyboardEventType.SHOWN else KeyboardEventType.HIDDEN
        onKeyboardStateChanged(active)
    }

    /**
     * 启动播种键盘状态（与 orientation/锁屏播种同源的问题）：默认 false，重绑时键盘若已弹出，
     * 「键盘隐藏」类启用规则的首评边沿会被吞（且 `updateKeyboardActive` 的同值早退连纠正
     * 事件都收不到）。用焦点节点可编辑性探测一次；只设值不发脉冲，root 未就绪时探不到、
     * 保持 false，下一次输入类事件自会纠正。
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
        isCharging: Boolean = false,
        batteryLevel: Int = -1,
        networkType: NetworkType = NetworkType.NONE,
        wifiSsid: String? = null,
        headphonesConnected: Boolean = false,
        bluetoothAdapterOn: Boolean? = null,
        bluetoothAudioConnected: Boolean? = null,
        bluetoothDeviceName: String? = null,
        bluetoothDeviceAddress: String? = null,
        activeNotifications: List<NotificationPulse> = emptyList(),
        airplaneMode: Boolean = false,
        ringerMode: RingerMode = RingerMode.NORMAL,
        volumePercent: Map<AudioStream, Int> = emptyMap(),
        isMusicPlaying: Boolean = false,
        isTorchOn: Boolean = false,
        isDndOn: Boolean = false,
        consumePulses: Boolean = true,
        /** 只读不消费：把待处理脉冲暴露给取值方，但 pending 保持原样，消费权仍归自动化（边沿命中语义依赖「谁评估谁消费」）。 */
        peekPulses: Boolean = false,
    ): GestureRuntimeState {
        val expose = consumePulses || peekPulses
        val appChangedTo = if (expose) pendingAppChangedTo else null
        val screenEvent = if (expose) pendingScreenEvent else null
        val chargingChangedTo = if (expose) pendingChargingChangedTo else null
        val volumeChanged = if (expose) pendingVolumeChange else null
        val notificationPosted = if (expose) pendingNotification else null
        val notificationRemoved = if (expose) pendingNotificationRemoved else null
        val packageChanged = if (expose) pendingPackageChange else null
        val shareReceived = if (expose) pendingShare else null
        val clipboardChanged = if (expose) pendingClipboard else null
        val intervalDueKeys = if (expose) pendingIntervalDueKeys else emptySet()
        val bluetoothChanged = if (expose) pendingBluetoothChanged else null
        val headphonesChanged = if (expose) pendingHeadphonesChanged else null
        val scheduledTimeHit = expose && pendingScheduledTimeHit
        val gesturePerformedId = if (expose) pendingGesturePerformedId else null
        val gesturePerformedName = if (expose) pendingGesturePerformedName else null
        val keyboardEvent = if (expose) pendingKeyboardEvent else null
        val musicChanged = if (expose) pendingMusicChanged else null
        val torchChangedTo = if (expose) pendingTorchChangedTo else null
        val dndChangedTo = if (expose) pendingDndChangedTo else null
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
            dayOfWeek = nowDayOfWeek(),
            networkType = networkType,
            wifiSsid = wifiSsid,
            headphonesConnected = headphonesConnected,
            bluetoothAdapterOn = bluetoothAdapterOn,
            bluetoothAudioConnected = bluetoothAudioConnected,
            bluetoothDeviceName = bluetoothDeviceName,
            bluetoothDeviceAddress = bluetoothDeviceAddress,
            airplaneMode = airplaneMode,
            ringerMode = ringerMode,
            volumePercent = volumePercent,
            isMusicPlaying = isMusicPlaying,
            isTorchOn = isTorchOn,
            isDndOn = isDndOn,
            appChangedTo = appChangedTo,
            screenEvent = screenEvent,
            chargingChangedTo = chargingChangedTo,
            volumeChanged = volumeChanged,
            notificationPosted = notificationPosted,
            notificationRemoved = notificationRemoved,
            activeNotifications = activeNotifications,
            packageChanged = packageChanged,
            shareReceived = shareReceived,
            clipboardChanged = clipboardChanged,
            intervalDueKeys = intervalDueKeys,
            bluetoothChanged = bluetoothChanged,
            headphonesChanged = headphonesChanged,
            scheduledTimeHit = scheduledTimeHit,
            gesturePerformedId = gesturePerformedId,
            gesturePerformedName = gesturePerformedName,
            keyboardEvent = keyboardEvent,
            musicChanged = musicChanged,
            torchChangedTo = torchChangedTo,
            dndChangedTo = dndChangedTo,
        )
    }

    private fun clearPendingPulses() {
        pendingAppChangedTo = null
        pendingScreenEvent = null
        pendingChargingChangedTo = null
        pendingVolumeChange = null
        pendingNotification = null
        pendingNotificationRemoved = null
        pendingPackageChange = null
        pendingShare = null
        pendingClipboard = null
        pendingIntervalDueKeys = emptySet()
        pendingBluetoothChanged = null
        pendingHeadphonesChanged = null
        pendingScheduledTimeHit = false
        pendingGesturePerformedId = null
        pendingGesturePerformedName = null
        pendingKeyboardEvent = null
        pendingMusicChanged = null
        pendingTorchChangedTo = null
        pendingDndChangedTo = null
    }

    /**
     * 键盘状态以**真实 IME 窗口**为准（`TYPE_INPUT_METHOD` 是否在 windows 里），
     * 不再由「是否有 EditText 获得焦点」推断：输入框保留焦点但键盘被收起是常态，
     * 旧实现会把「键盘已收起」永远判成弹出，状态型规则和 KEYBOARD_EVENT 的收起边沿
     * 都再也发不出来。焦点判断仅作为拿不到窗口列表时的兜底。
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
