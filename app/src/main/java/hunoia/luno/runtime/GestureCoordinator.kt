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
import hunoia.luno.pointer.PointerFacade
import hunoia.luno.pointer.PointerRuntime
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.action.ActionDispatcher
import hunoia.luno.runtime.action.KeepScreenOnController
import hunoia.luno.runtime.action.PreviousAppTracker
import hunoia.luno.runtime.button.ButtonHideRuntime
import hunoia.luno.runtime.button.ButtonRefreshCoordinator
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.environment.EnvironmentTracker
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.overlay.OverlayCoordinator
import hunoia.luno.runtime.settings.SettingsStore
import hunoia.luno.runtime.volume.VolumeScrubRuntime
import hunoia.luno.bridge.feedback.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "keyboard active=$active")
            refreshGestureButtons()
        },
    )
    val overlayCoordinator = OverlayCoordinator(host)
    private var refreshJob: Job? = null

    private val broadcastObserver = BroadcastObserver(
        context = host.context,
        onScreenOff = {
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "screen off")
            environmentTracker.isNowInLockScreenPage = true
            host.quickAppLauncherOverlay.closeImmediately()
            host.runtimePanelOverlay.closeImmediately()
            refreshGestureButtons()
        },
        onUserPresent = {
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "user present")
            environmentTracker.isNowInLockScreenPage = false
            refreshGestureButtons()
        }
    )

    private val buttonHideRuntime = ButtonHideRuntime(
        scope = host.coroutineScope,
        onStateChanged = { refreshGestureButtons() },
    )

    private val buttonRefreshCoordinator = ButtonRefreshCoordinator(
        runtimeSettingsStore = runtimeSettingsStore,
        buttonWindowController = overlayCoordinator.buttonWindowController,
        buildRuntimeState = {
            environmentTracker.buildRuntimeState(buttonHideRuntime.getSnapshot())
        },
    )

    private val previousAppTracker = PreviousAppTracker(
        packageManager = host.context.packageManager,
        startActivity = { host.context.startActivity(it) },
        rootInActiveWindowPackageName = { host.accessibilityService.rootInActiveWindow?.packageName?.toString() },
        excludePackageNames = { runtimeSettingsStore.snapshot().actionSettings.previousApp.packageNames },
    )

    private val keepScreenOnController = KeepScreenOnController(
        context = host.context,
        showToast = { showToast(it) },
    )

    private val actionDispatcher = ActionDispatcher(
        host = host,
        scope = host.coroutineScope,
        previousAppTracker = previousAppTracker,
        keepScreenOnController = keepScreenOnController,
        settingsSnapshot = {
            val s = runtimeSettingsStore.snapshot()
            hunoia.luno.runtime.action.SettingsSnapshot(
                actionSettings = s.actionSettings,
                advancedSettings = s.advancedSettings,
                gestureSettings = s.gestureSettings,
            )
        },
        onToggleQuickAppLauncher = { host.quickAppLauncherOverlay.toggle() },
        onShowPointer = { continuousModeOverride ->
            pointerRuntime.show(continuousModeOverride)
        },
        onShowVolumeScrub = { volumeScrubRuntime.show() },
        onHideGestureButton = { button, delayMs ->
            if (button != null) buttonHideRuntime.hideTemporarily(button, delayMs)
        },
    )

    private val pointerRuntime = PointerRuntime(
        host = host,
        scope = host.coroutineScope,
        gestureSettingsProvider = { runtimeSettingsStore.snapshot().gestureSettings },
        onStateChanged = { refreshGestureButtons(delayMs = 0L) },
    ).also { PointerFacade.runtimeProvider = { it } }

    private val volumeScrubRuntime = VolumeScrubRuntime(
        context = host.context,
        actionSettingsProvider = { runtimeSettingsStore.snapshot().actionSettings },
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

        override fun onPointerStart(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean {
            return pointerRuntime.beginBridge(settings)
        }

        override fun onPointerShow(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean {
            return pointerRuntime.showBridge(settings)
        }

        override fun onPointerEnd() {
            pointerRuntime.end()
        }

        override fun onPointerActionAtPosition(x: Int, y: Int, keepActive: Boolean) {
            pointerRuntime.performBridgeActionAt(x, y, keepActive)
        }
    }

    fun onSetOverlay() {
        if (started) return
        started = true
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "GestureCoordinator start")
        runtimeSettingsStore.start()
        broadcastObserver.register()
        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            hunoia.luno.core.Events.post(hunoia.luno.bridge.WallpaperChangedEvent())
        }
        wallpaperColorsListener = listener
        WallpaperManager.getInstance(host.context).addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))

        overlayCoordinator.replaceMainOverlay { renderMainOverlay() }

        host.coroutineScope.launch {
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.gestureButtons }
                .collectLatest { state ->
                    if (BuildConfig.DEBUG) Log.d("LunoLauncher", "gesture buttons changed: count=${state.gestureButtons.size}")
                    overlayCoordinator.replaceGestureButtons(state.gestureButtons)
                    refreshGestureButtons(delayMs = 0L)
                }
        }

        host.coroutineScope.launch(Dispatchers.IO) {
            QuickLaunchFacade.queryApps(host.context)
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        previousAppTracker.onAccessibilityEvent(event)
        environmentTracker.onAccessibilityEvent(event)
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "window changed: pkg=${event.packageName}")
            refreshGestureButtons()
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        val oldOrientation = environmentTracker.orientation
        if (oldOrientation != newConfig.orientation) {
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "orientation=${newConfig.orientation}")
            environmentTracker.onOrientationChanged(newConfig.orientation)
            overlayCoordinator.updateMainLayout()
            refreshGestureButtons()
        }
    }

    fun onDestroy() {
        if (!started) return
        started = false
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "GestureCoordinator destroy")
        overlayCoordinator.release()
        broadcastObserver.unregister()
        pointerRuntime.onDestroy()
        volumeScrubRuntime.onDestroy()
        previousAppTracker.onRelease()
        keepScreenOnController.onRelease()
        wallpaperColorsListener?.let { listener ->
            WallpaperManager.getInstance(host.context).removeOnColorsChangedListener(listener)
            wallpaperColorsListener = null
        }
        PointerFacade.runtimeProvider = null
        host.coroutineScope.cancel()
    }

    private fun refreshGestureButtons(delayMs: Long = 100L) {
        refreshJob?.cancel()
        refreshJob = host.coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            buttonRefreshCoordinator.refresh()
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
