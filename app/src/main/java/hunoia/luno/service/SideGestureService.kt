package hunoia.luno.service

import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.aaron.composeaccessibility.ComponentAccessibilityService
import hunoia.luno.action.controller.TextInserter
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.gridPanelId
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.GestureCoordinator
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.automation.RuleTestResult
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.overlay.PointPickOverlay
import hunoia.luno.link.ScreenLinksOverlay
import hunoia.luno.runtime.overlay.ScreenPickOverlay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
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
    private val screenLinksOverlay = ScreenLinksOverlay(this)

    /** 识别屏幕内容动作入口（动作执行已在 :service 进程直连）。返回是否成功弹起。 */
    fun showScreenPick(): Boolean {
        if (!isDispatchReady()) return false
        // 挂窗是同步的：锁屏/系统拒绝时直接返回失败，动作层才能给出正确结果。
        return screenPickOverlay.show()
    }

    /** 识屏取链：同样同步挂窗，扫描与分发在 overlay 内部异步完成。 */
    fun showScreenLinks(): Boolean {
        if (!isDispatchReady()) return false
        // 从面板触发时必须先收面板：面板是自家全屏窗，留着就扫不到底下应用的节点。
        gestureCoordinator.dismissPanelsForScreenAction()
        return screenLinksOverlay.show()
    }

    /**
     * 剪贴板拖拽落点输入：在屏幕 (x,y) 下的输入框里直接写入文本（仅个别编辑器才用剪贴板粘贴兜底）。
     * 面板浮窗自己吞掉系统拖放事件，跨应用输入只能走无障碍节点（见 TextInserter）。
     */
    fun insertTextAt(text: String, x: Int, y: Int): TextInserter.Result =
        TextInserter.insertAt(this, text, x, y)

    override val advancedSettings: AdvancedSettings?
        get() = if (::gestureCoordinator.isInitialized)
            gestureCoordinator.runtimeSettingsStore.snapshot().advancedSettings
        else null

    /** 球直发网格面板：悬空引用（面板被删）静默收口，空面板照常弹（本体是编辑入口）。
     *  实现在 service 侧，避免 runtime 包直接依赖 gridpanel。 */
    fun showGridPanelDirect(action: StoredAction, anchorX: Int, anchorY: Int) {
        coroutineScope.launch {
            val panelId = action.gridPanelId() ?: return@launch
            val actionSettings = runCatching { hunoia.luno.domain.tune.TuneConfig.getAction() }
                .onFailure {
                    android.util.Log.w(LOG_TAG, "grid panel direct config read failed: ${it.message}")
                }
                .getOrDefault(hunoia.luno.config.model.ActionSettings())
            hunoia.luno.gridpanel.GridPanelOverlay.showIfExists(
                actionSettings, panelId, anchorX, anchorY,
            )
        }
    }

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

    /** 权限变化后让 :service 的条件源重新读取（Wi-Fi SSID/蓝牙设备身份）。 */
    fun refreshConditionSources(): Boolean {
        if (!::gestureCoordinator.isInitialized) return false
        gestureCoordinator.refreshConditionSources()
        return true
    }

    /** 运行中的规则 id（规则列表页的「运行中/停止」入口）。 */
    fun runningRuleIds(): Set<String> =
        if (::gestureCoordinator.isInitialized) gestureCoordinator.runningRuleIds() else emptySet()

    /** 手动停止一条规则的所有实例。 */
    fun stopRule(ruleId: String): Boolean =
        if (::gestureCoordinator.isInitialized) gestureCoordinator.stopRule(ruleId) else false

    /** 编辑页测试运行：条件按当前快照求值，步骤真实执行一次；executeSteps=false 只检查条件。 */
    fun runRuleTest(
        rule: AutomationRule,
        executeSteps: Boolean,
        onResult: (RuleTestResult) -> Unit,
    ): Boolean {
        if (!::gestureCoordinator.isInitialized) return false
        gestureCoordinator.runRuleTest(rule, executeSteps, onResult)
        return true
    }

    override fun onSetOverlay() {
        current = this
        if (::gestureCoordinator.isInitialized) gestureCoordinator.onDestroy()
        gestureCoordinator = GestureCoordinator(this)
        // 球侧（:feature:floatball）经注入点下发命令，避免反向依赖宿主服务。
        hunoia.luno.gesture.GestureCommand.showGridPanel = { action, anchorX, anchorY ->
            showGridPanelDirect(action, anchorX, anchorY)
        }
        hunoia.luno.gesture.GestureCommand.forwardBallTap = { action, anchorX, anchorY, color ->
            forwardBallTap(action, anchorX, anchorY, color)
        }
        hunoia.luno.gesture.GestureCommand.forwardPanelTap = { action, x, y, color ->
            forwardPanelTap(action, x, y, color)
        }
        hunoia.luno.gesture.GestureCommand.insertText = { text, x, y -> insertTextAt(text, x, y) }
        // 网格面板「加项」要起选择页：路由与宿主 Activity 都在 :app，面板模块只发中性请求。
        hunoia.luno.gridpanel.GridPanelCommand.openItemPicker = { pick ->
            hunoia.luno.ui.actionselect.ActionSelectHostActivity.start(
                this,
                hunoia.luno.ui.navigation.ActionSelect(
                    gridPanelId = pick.gridPanelId,
                    gridPanelPageIndex = pick.pageIndex,
                ),
            )
        }
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
        screenLinksOverlay.release()
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
