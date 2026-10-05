package hunoia.luno.service

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.aaron.composeaccessibility.ComponentAccessibilityService
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.Action
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.GestureCoordinator
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.MainScope

class SideGestureService : ComponentAccessibilityService(), GestureHost {

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
    private val disabledPackageEnabler by lazy {
        DisabledPackageEnabler(
            context = this,
            scopeProvider = { coroutineScope },
            log = { message -> android.util.Log.i("LunoLauncher", message) }
        )
    }

    override val runtimePanelOverlay by lazy {
        RuntimePanelOverlay(this)
    }

    override val advancedSettings: AdvancedSettings?
        get() = if (::gestureCoordinator.isInitialized)
            gestureCoordinator.runtimeSettingsStore.snapshot().advancedSettings
        else null

    override fun dispatchAction(action: Action) {
        if (::gestureCoordinator.isInitialized) gestureCoordinator.dispatchAction(action)
    }

    override fun onSetOverlay() {
        current = this
        if (::gestureCoordinator.isInitialized) gestureCoordinator.onDestroy()
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
        // 注意：这里**不**释放常驻面板窗口。manifest 没声明 android:configChanges，
        // 无障碍服务不一定收得到这个回调（实测旋转后进程 PID 不变、回调未确认送达），
        // 靠它做旋转重建不可靠。旋转后的尺寸修正在 UI 层做 ——
        // QuickAppLauncherRoot 监听 LocalConfiguration 的 orientation 变化后重算窗口与图标尺寸。
        if (!::gestureCoordinator.isInitialized) return
        gestureCoordinator.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (current === this) currentRef = null
        disabledPackageEnabler.release()
        if (::gestureCoordinator.isInitialized) gestureCoordinator.onDestroy()
        runtimePanelOverlay.close()
        coroutineScope.cancel()
    }

    override fun nowInLauncher(): Boolean {
        return QuickLaunchFacade.isLauncherPackage(this, getCurrentPackageName())
    }

    override fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit) {
        disabledPackageEnabler.request(packageName, onResult)
    }

    override fun getCurrentPackageName(): String {
        return rootInActiveWindow?.packageName?.toString() ?: ""
    }
}
