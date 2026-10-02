package hunoia.luno.runtime

import android.app.WallpaperManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import hunoia.luno.BuildConfig
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.action.ActionDispatcher
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.runtime.action.PreviousAppTracker
import hunoia.luno.runtime.condition.AudioVolumeTracker
import hunoia.luno.runtime.condition.hasVolumeCondition
import hunoia.luno.runtime.automation.AutomationEngine
import hunoia.luno.runtime.automation.AutomationRuntime
import hunoia.luno.runtime.button.ButtonHideRuntime
import hunoia.luno.runtime.button.ButtonRefreshCoordinator
import hunoia.luno.runtime.condition.BatteryTracker
import hunoia.luno.runtime.condition.TimeConditionTicker
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.environment.ConditionSources
import hunoia.luno.runtime.environment.EnvironmentTracker
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.overlay.OverlayCoordinator
import hunoia.luno.runtime.settings.SettingsStore
import hunoia.luno.runtime.volume.VolumeScrubRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GestureCoordinator(
    private val host: GestureHost,
) {
    val runtimeSettingsStore = SettingsStore(host.coroutineScope)
    val environmentTracker = EnvironmentTracker(
        getCurrentPackageName = { host.getCurrentPackageName() },
        nowInLauncher = { host.nowInLauncher() },
        findFocusedNode = {
            host.accessibilityService.rootInActiveWindow?.findFocus(
                android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
            )
        },
        onKeyboardStateChanged = { active ->
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "keyboard active=$active")
            refreshGestureButtons()
        },
    )
    val overlayCoordinator = OverlayCoordinator(host)
    private var refreshJob: Job? = null
    private val scopeJobs = mutableListOf<Job>()

    private val broadcastObserver = BroadcastObserver(
        context = host.context,
        onScreenOff = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "screen off")
            environmentTracker.isNowInLockScreenPage = true
            host.quickAppLauncherOverlay.closeImmediately()
            host.runtimePanelOverlay.closeImmediately()
            refreshGestureButtons()
        },
        onScreenOn = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "screen on")
            environmentTracker.onScreenEvent(ScreenEventType.SCREEN_ON)
            refreshGestureButtons()
        },
        onUserPresent = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "user present")
            environmentTracker.isNowInLockScreenPage = false
            refreshGestureButtons()
        }
    )

    private val conditionSources = ConditionSources(
        context = host.context,
        onStateChanged = { refreshGestureButtons() },
    )

    private val audioVolumeTracker = AudioVolumeTracker(
        context = host.context,
        onVolumeChanged = { change ->
            environmentTracker.onVolumeChanged(change)
            refreshGestureButtons()
        },
    )

    private val buttonHideRuntime = ButtonHideRuntime(
        scope = host.coroutineScope,
        onStateChanged = { refreshGestureButtons() },
    )

    private val batteryTracker = BatteryTracker(
        context = host.context,
        onChanged = { charging ->
            environmentTracker.onChargingChanged(charging)
            refreshGestureButtons()
        },
    )

    private val timeConditionTicker = TimeConditionTicker(
        scope = host.coroutineScope,
        rulesProvider = { runtimeSettingsStore.snapshot().automationRules },
        onTimeSignatureChanged = { refreshGestureButtons(delayMs = 0L) },
    )

    private val previousAppTracker = PreviousAppTracker(
        packageManager = host.context.packageManager,
        startActivity = { host.context.startActivity(it) },
        rootInActiveWindowPackageName = { host.accessibilityService.rootInActiveWindow?.packageName?.toString() },
    )

    private val actionDispatcher = ActionDispatcher(
        host = host,
        scope = host.coroutineScope,
        previousAppTracker = previousAppTracker,
        settingsSnapshot = {
            val s = runtimeSettingsStore.snapshot()
            hunoia.luno.runtime.action.SettingsSnapshot(
                actionSettings = s.actionSettings,
                advancedSettings = s.advancedSettings,
                gestureSettings = s.gestureSettings,
                newActionLibrarySettings = s.newActionLibrarySettings,
            )
        },
        onToggleQuickAppLauncher = { host.quickAppLauncherOverlay.toggle() },
        onShowVolumeScrub = { config -> volumeScrubRuntime.show(config) },
        onHideGestureButton = { button, delayMs ->
            if (button != null) buttonHideRuntime.hideTemporarily(button, delayMs)
        },
    )

    private val automationEngine = AutomationEngine(
        onRunEntry = { entryId -> actionDispatcher.runActionEntry(entryId) },
    )

    private val buttonRefreshCoordinator = ButtonRefreshCoordinator(
        runtimeSettingsStore = runtimeSettingsStore,
        buttonWindowController = overlayCoordinator.buttonWindowController,
    )

    private val automationRuntime = AutomationRuntime(
        runtimeSettingsStore = runtimeSettingsStore,
        buildRuntimeState = ::buildRuntimeState,
        automationEngine = automationEngine,
        onVisibilityRefresh = { state -> buttonRefreshCoordinator.refresh(state) },
    )

    private val volumeScrubRuntime = VolumeScrubRuntime(
        context = host.context,
        onStateChanged = { refreshGestureButtons() },
    )

    private var started = false
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null

    private val callbacks: GestureOverlayCallbacks = object : GestureOverlayCallbacks {
        override fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int) {
            if (inSubGesture) overlayCoordinator.attachSubGestureOverlay(center, radiusPx)
            else overlayCoordinator.detachSubGestureOverlay()
        }

        override fun onActionPanelOverlayChanged(show: Boolean) {
            if (show) overlayCoordinator.attachActionPanelOverlay()
            else overlayCoordinator.detachActionPanelOverlay()
        }

        override fun onAction(action: Action, sourceButton: GestureButton?, sourceOverride: GestureButtonActionSettingsOverride?) {
            actionDispatcher.onAction(action, sourceButton, sourceOverride)
        }
    }

    fun onSetOverlay() {
        if (started) return
        started = true
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "GestureCoordinator start")
        runtimeSettingsStore.start()
        broadcastObserver.register()
        batteryTracker.register()
        audioVolumeTracker.sync(volumePollNeeded())
        conditionSources.start()
        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            hunoia.luno.core.Events.post(hunoia.luno.bridge.WallpaperChangedEvent())
        }
        wallpaperColorsListener = listener
        WallpaperManager.getInstance(host.context).addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))

        overlayCoordinator.replaceMainOverlay { renderMainOverlay() }

        scopeJobs += host.coroutineScope.launch {
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.gestureButtons }
                .collectLatest { state ->
                    if (BuildConfig.DEBUG) Log.i("LunoLauncher", "gesture buttons changed: count=${state.gestureButtons.size}")
                    overlayCoordinator.replaceGestureButtons(state.gestureButtons)
                    refreshGestureButtons(delayMs = 0L)
                }
        }

        scopeJobs += host.coroutineScope.launch(Dispatchers.IO) {
            QuickLaunchFacade.queryApps(host.context)
        }

        scopeJobs += host.coroutineScope.launch {
            var firstRuleEmission = true
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.automationRules }
                .collect { state ->
                    timeConditionTicker.sync(state.automationRules)
                    audioVolumeTracker.sync(volumePollNeeded(state.automationRules))
                    if (firstRuleEmission) {
                        firstRuleEmission = false
                    } else {
                        refreshGestureButtons(delayMs = 0L)
                    }
                }
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        previousAppTracker.onAccessibilityEvent(event)
        environmentTracker.onAccessibilityEvent(event)
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "window changed: pkg=${event.packageName}")
            refreshGestureButtons()
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        val oldOrientation = environmentTracker.orientation
        if (oldOrientation != newConfig.orientation) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "orientation=${newConfig.orientation}")
            environmentTracker.onOrientationChanged(newConfig.orientation)
            overlayCoordinator.updateMainLayout()
            refreshGestureButtons()
        }
    }

    fun onDestroy() {
        if (!started) return
        started = false
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "GestureCoordinator destroy")
        runtimeSettingsStore.stop()
        timeConditionTicker.stop()
        batteryTracker.unregister()
        audioVolumeTracker.unregister()
        conditionSources.stop()
        scopeJobs.forEach { it.cancel() }
        scopeJobs.clear()
        overlayCoordinator.release()
        broadcastObserver.unregister()
        volumeScrubRuntime.onDestroy()
        previousAppTracker.onRelease()
        wallpaperColorsListener?.let { listener ->
            WallpaperManager.getInstance(host.context).removeOnColorsChangedListener(listener)
            wallpaperColorsListener = null
        }
    }

    private fun buildRuntimeState(): GestureRuntimeState =
        environmentTracker.buildRuntimeState(
            hiddenGestureButtons = buttonHideRuntime.getSnapshot(),
            isCharging = batteryTracker.isCharging,
            batteryLevel = batteryTracker.batteryLevel,
            networkType = conditionSources.networkType,
            headphonesConnected = conditionSources.headphonesConnected,
            bluetoothAdapterOn = conditionSources.bluetoothAdapterOn,
            bluetoothAudioConnected = conditionSources.bluetoothAudioConnected,
            airplaneMode = conditionSources.airplaneMode,
            isRingerSilent = audioVolumeTracker.isRingerSilent,
            volumePercent = audioVolumeTracker.volumePercent,
        )

    private fun volumePollNeeded(rules: List<hunoia.luno.config.model.AutomationRule>? = null): Boolean {
        val list = rules ?: runtimeSettingsStore.snapshot().automationRules
        return list.any { rule -> rule.condition.hasVolumeCondition() }
    }

    private fun refreshGestureButtons(delayMs: Long = 100L) {
        refreshJob?.cancel()
        refreshJob = host.coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            automationRuntime.evaluate()
        }
    }

    @Composable
    private fun renderMainOverlay() {
        hunoia.luno.runtime.overlay.GestureOverlayView(
            callbacks = callbacks,
            settingsState = runtimeSettingsStore.state,
        )
    }
}
