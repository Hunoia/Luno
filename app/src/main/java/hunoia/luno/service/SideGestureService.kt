package hunoia.luno.service

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.aaron.composeaccessibility.ComponentAccessibilityService
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.freeze.FrozenPackageEnabler
import hunoia.luno.pointer.PointerOverlayHost
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.GestureCoordinator
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.overlay.QuickAppLauncherOverlay
import hunoia.luno.runtime.overlay.QuickAppLauncherOverlayHost
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.overlay.RuntimePanelOverlayHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class SideGestureService : ComponentAccessibilityService(), GestureHost, QuickAppLauncherOverlayHost, RuntimePanelOverlayHost, PointerOverlayHost {

    companion object {
        private var currentRef: java.lang.ref.WeakReference<SideGestureService>? = null
        var current: SideGestureService?
            get() = currentRef?.get()
            private set(value) { currentRef = if (value != null) java.lang.ref.WeakReference(value) else null }
    }

    override val context: android.content.Context get() = this
    override val accessibilityService: android.accessibilityservice.AccessibilityService get() = this
    override val coroutineScope = MainScope()

    private lateinit var gestureCoordinator: GestureCoordinator
    private val frozenPackageEnabler by lazy {
        FrozenPackageEnabler(
            context = this,
            scopeProvider = { coroutineScope },
            log = { message -> android.util.Log.d("LunoLauncher", message) }
        )
    }

    override val quickAppLauncherOverlay by lazy {
        QuickAppLauncherOverlay(this).apply {
            onAppLaunchRequested = { app ->
                coroutineScope.launch(Dispatchers.IO) {
                    recordQuickAppLaunchIfSuccess(true, "${app.packageName}/${app.className}") {
                        hunoia.luno.config.ConfigProvider.recordQuickAppLaunch(it)
                    }
                }
            }
            QuickLaunchFacade.showOverlay = { show() }
        }
    }
    override val runtimePanelOverlay by lazy {
        RuntimePanelOverlay(this)
    }

    override val advancedSettings: AdvancedSettings?
        get() = if (::gestureCoordinator.isInitialized)
            gestureCoordinator.runtimeSettingsStore.snapshot().advancedSettings
        else null

    override fun onSetOverlay() {
        current = this
        gestureCoordinator = GestureCoordinator(this)
        gestureCoordinator.onSetOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::gestureCoordinator.isInitialized) return
        gestureCoordinator.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::gestureCoordinator.isInitialized) return
        gestureCoordinator.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (current === this) currentRef = null
        frozenPackageEnabler.release()
        if (::gestureCoordinator.isInitialized) gestureCoordinator.onDestroy()
        quickAppLauncherOverlay.closeImmediately()
        runtimePanelOverlay.close()
        QuickLaunchFacade.showOverlay = {}
    }

    override fun nowInLauncher(): Boolean {
        return QuickLaunchFacade.isLauncherPackage(this, getCurrentPackageName())
    }

    override fun requestEnableFrozenPackage(packageName: String, onResult: (Boolean) -> Unit) {
        frozenPackageEnabler.request(packageName, onResult)
    }

    override fun getCurrentPackageName(): String {
        return rootInActiveWindow?.packageName?.toString() ?: ""
    }
}

internal suspend fun recordQuickAppLaunchIfSuccess(
    success: Boolean,
    appKey: String,
    record: suspend (String) -> Unit,
) {
    if (success) {
        record(appKey)
    }
}
