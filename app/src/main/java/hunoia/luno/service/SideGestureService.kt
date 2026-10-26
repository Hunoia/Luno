package hunoia.luno.service

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.aaron.composeaccessibility.ComponentAccessibilityService
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.action.model.StoredAction
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.GestureCoordinator
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.overlay.PointPickOverlay
import hunoia.luno.runtime.overlay.ScreenPickOverlay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import hunoia.luno.core.LOG_TAG

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
            log = { message -> android.util.Log.i(LOG_TAG, message) }
        )
    }

    override val runtimePanelOverlay by lazy {
        RuntimePanelOverlay(this)
    }

    /** 屏幕取点窗口。必须住在无障碍服务这一侧 —— 见 DispatchService 的说明。 */
    override val pointPickOverlay by lazy {
        PointPickOverlay(this)
    }

    /** 屏幕内容识别拾取窗。与 pointPickOverlay 同规：必须住在无障碍服务这一侧。
     *  非 lazy：构造时就要订阅面板开关事件，否则第一次从面板触发时会错过 Shown。 */
    private val screenPickOverlay = ScreenPickOverlay(this)

    /** 识别屏幕内容动作入口（动作执行已在 :service 进程直连）。返回是否成功弹起。 */
    fun showScreenPick(): Boolean {
        if (!isDispatchReady()) return false
        // 挂窗是同步的：锁屏/系统拒绝时直接返回失败，动作层才能给出正确结果。
        return screenPickOverlay.show()
    }

    override val advancedSettings: AdvancedSettings?
        get() = if (::gestureCoordinator.isInitialized)
            gestureCoordinator.runtimeSettingsStore.snapshot().advancedSettings
        else null

    override fun dispatchAction(action: StoredAction) {
        if (::gestureCoordinator.isInitialized) gestureCoordinator.dispatchAction(action)
    }

    /**
     * 悬浮球点按动作统一转发入口：动作分类在容器（见 GestureCoordinator.forwardBallTap）。
     */
    fun forwardBallTap(action: StoredAction, anchorX: Int, anchorY: Int, color: Int): Boolean {
        if (!::gestureCoordinator.isInitialized) return false
        gestureCoordinator.forwardBallTap(action, anchorX, anchorY, color)
        return true
    }

    /**
     * 网格面板项的点按转发：与球点按走同一条锚点链（BallTapActionEvent → 容器
     * runGestureActions 分类），子手势/音量刮擦/嵌套弧形面板项因此自动获得正确语义。
     * 面板浮窗自身没有手势会话（SAW 窗口，宿主是 a11y 服务），绝不能绕过容器直发。
     */
    fun forwardPanelTap(action: StoredAction, anchorX: Int, anchorY: Int, color: Int): Boolean =
        forwardBallTap(action, anchorX, anchorY, color)

    /** 分发链就绪：coordinator 已初始化。供 DispatchService 跨进程查询。 */
    fun isDispatchReady(): Boolean = ::gestureCoordinator.isInitialized

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
        // 与 runtimePanelOverlay 同进退：点拾取全屏窗别指望系统随服务销毁回收。
        pointPickOverlay.close()
        // 拾取窗持有全局面板事件订阅，必须显式退订。
        screenPickOverlay.release()
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
