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
    ): GestureRuntimeState {
        val appChangedTo = pendingAppChangedTo
        val screenEvent = pendingScreenEvent
        val chargingChangedTo = pendingChargingChangedTo
        val volumeChanged = pendingVolumeChange
        pendingAppChangedTo = null
        pendingScreenEvent = null
        pendingChargingChangedTo = null
        pendingVolumeChange = null
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
