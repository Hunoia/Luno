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
    private val ocrEngine = hunoia.luno.capability.ocr.PpOcrEngine(this)

    private val screenPickOverlay = ScreenPickOverlay(
        this,
        ocrEngine = ocrEngine,
        onSearch = { query ->
            hunoia.luno.quicklaunch.QuickSearchActivity.start(this, query)
        },
        onOpenLink = { url ->
            val settings = hunoia.luno.domain.link.LinkSettingsCenter.linkDispatch()
            val rule = if (settings.enabled) {
                hunoia.luno.link.LinkUrls.firstMatch(url, settings.rules)
            } else {
                null
            }
            val dispatched = settings.enabled &&
                hunoia.luno.link.Links.dispatch(this, url, settings)
            if (!dispatched) {
                hunoia.luno.link.Links.fallbackFor(this, url, rule, settings)
            }
            true
        },
        screenPickGesture = {
            if (::gestureCoordinator.isInitialized) {
                gestureCoordinator.currentScreenPickGesture()
            } else {
                hunoia.luno.config.model.ScreenPickGesture()
            }
        },
        onClosed = {
            if (::gestureCoordinator.isInitialized) {
                gestureCoordinator.setScreenPickInterceptTouchable(true)
            }
        },
    )

    /**
     * OCR 模型包导入 / 删除后调用：丢弃旧引擎（模型 native 句柄）并重新预热，
     * 避免设置页换了模型而 `:service` 一直用旧的。
     */
    fun refreshOcrEngine() {
        coroutineScope.launch { runCatching { ocrEngine.reload() } }
    }

    /** 识别屏幕内容动作入口（动作执行已在 :service 进程直连）。返回是否成功弹起。 */
    fun showScreenPick(): Boolean {
        if (!isDispatchReady()) return false
        // 挂窗是同步的：锁屏/系统拒绝时直接返回失败，动作层才能给出正确结果。
        val shown = screenPickOverlay.show()
        if (shown && ::gestureCoordinator.isInitialized) {
            gestureCoordinator.setScreenPickInterceptTouchable(false)
        }
        return shown
    }

    /**
     * 全屏长按识屏入口：落到幽灵卡（不经过轻点取词 / 拖动框选）。
     * 后续拖动/抬手由 GlobalLongPressTrigger 从同一股内核触摸流转发进来。
     */
    fun showScreenPickPortal(
        x: Int,
        y: Int,
        preCapture: hunoia.luno.runtime.overlay.text.ScreenPreCapture? = null,
    ): Boolean {
        if (!isDispatchReady()) return false
        val shown = screenPickOverlay.showPortalAt(x, y, preCapture)
        if (shown && ::gestureCoordinator.isInitialized) {
            gestureCoordinator.setScreenPickInterceptTouchable(false)
        }
        if (hunoia.luno.gesture.BuildConfig.DEBUG) {
            android.util.Log.i(LOG_TAG, "screen pick portal at ($x,$y) shown=$shown")
        }
        return shown
    }

    fun moveScreenPickPortal(x: Int, y: Int) {
        screenPickOverlay.movePortal(x, y)
    }

    fun releaseScreenPickPortal(x: Int, y: Int) {
        screenPickOverlay.releasePortal(x, y)
    }

    /** 幽灵卡是否正在显示（抬手保留后，下一次按下由 overlay 自己处理）。 */
    fun isScreenPickPortalVisible(): Boolean = screenPickOverlay.isPortalActive

    /** 识屏拾取层是否在屏（普通框选态也算：此时长按由 overlay 自己处理）。 */
    fun isScreenPickVisible(): Boolean = screenPickOverlay.isShown

    /** 屏幕取点窗是否在屏。 */
    fun isPointPickVisible(): Boolean = pointPickOverlay.isShown

    /** 前台 Activity 是否 Luno 自身界面（设置/搜索/链接选择等）。 */
    fun isOwnUiForeground(): Boolean =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() == packageName

    /**
     * 触点是否落在 Luno 自家窗口上（浮球/面板/识屏层）：按 layer 从高到低找第一个
     * 包含触点的窗口，取它的包名。自家 overlay 的 root 偶发为空时继续往下找，
     * 交由调用方的面板/Surface 状态兜底。
     */
    fun isOwnWindowAt(x: Int, y: Int): Boolean {
        val own = packageName
        val currentWindows = runCatching { windows }.getOrNull() ?: return false
        for (window in currentWindows.sortedByDescending { it.layer }) {
            val bounds = android.graphics.Rect()
            window.getBoundsInScreen(bounds)
            if (!bounds.contains(x, y)) continue
            val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
            if (pkg != null) return pkg == own
        }
        return false
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
        // debug-only 探测入口（app/src/debug 的 ScreenPickProbeReceiver）；release 恒为 null。
        ScreenPickProbe.hook = { x, y -> probeCapture(x, y) }
        // 服务起来就把引擎加载 / 预热一次：第一次长按不用再等 GPU delegate 建context，
        // 加载失败也会在 logcat 留下 w 级日志（见 PpOcrModelRunner）。
        coroutineScope.launch { runCatching { ocrEngine.warmUp() } }
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

    /** debug 探测：在指定屏幕坐标跑一次快照+命中，把结果打到 logcat（tag 同 LOG_TAG）。 */
    private fun probeCapture(x: Int, y: Int) {
        coroutineScope.launch {
            val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    hunoia.luno.runtime.overlay.text.A11yTextSnapshotReader(
                        this@SideGestureService,
                        packageName,
                    ).capture(x, y)
                }.onFailure { android.util.Log.w(LOG_TAG, "probe capture failed: ${it.message}") }
                    .getOrNull()
            } ?: return@launch
            val tolerance = 8
            val hit = hunoia.luno.runtime.overlay.text.TextSnapshotOps.hitText(snapshot, x, y, tolerance)
            val sb = StringBuilder(
                "probe@($x,$y): leaves=${snapshot.leaves.size} " +
                    "charLeaves=${snapshot.leaves.count { it.hasCharGeometry }} " +
                    "images=${snapshot.images.size} layouts=${snapshot.layouts.size} complete=${snapshot.complete} " +
                    "pkg=${snapshot.targetPackage} hit=${hit?.text?.take(40)}",
            )
            snapshot.layouts.take(3).forEach { rect ->
                sb.append("\n  lay ").append(rect)
            }
            snapshot.leaves.forEach { leaf ->
                sb.append("\n  leaf ").append(leaf.bounds).append(" cd=").append(leaf.fromContentDescription)
                    .append(' ').append(leaf.text.take(30))
            }
            snapshot.images.take(10).forEach { img ->
                sb.append("\n  img ").append(img.bounds)
            }
            android.util.Log.i(LOG_TAG, sb.toString())
        }
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
        // 识别器随服务销毁释放（模型 native 资源）；下次服务重建会重新懒加载。
        ocrEngine.close()
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
