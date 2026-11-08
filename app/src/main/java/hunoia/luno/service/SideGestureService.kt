package hunoia.luno.service

import android.content.res.Configuration
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.aaron.composeaccessibility.ComponentAccessibilityService
import hunoia.luno.action.controller.TextInserter
import hunoia.luno.clipboard.ClipboardCapture
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.gridPanelId
import hunoia.luno.quickaccess.launch.DisabledPackageEnabler
import hunoia.luno.quickaccess.QuickLaunchFacade
import hunoia.luno.runtime.GestureCoordinator
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.environment.RecentAppsHub
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.overlay.ScreenPickOverlay
import kotlinx.coroutines.Job
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
    private val speechResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            val text = intent.getStringExtra("text")?.takeIf { it.isNotEmpty() } ?: return
            val result = TextInserter.insertFocused(this@SideGestureService, text)
            if (result != TextInserter.Result.Inserted &&
                result != TextInserter.Result.InsertedViaClipboard
            ) {
                ClipboardCenter.writeTextImmediate(text, "speech_recognition")
            }
        }
    }
    private var speechReceiverRegistered = false
    private val disabledPackageEnabler by lazy {
        DisabledPackageEnabler(
            context = this,
            scopeProvider = { coroutineScope },
            log = { message -> hunoia.luno.core.LunoLog.i(LOG_TAG, message) }
        )
    }

    override val runtimePanelOverlay by lazy {
        RuntimePanelOverlay(this)
    }

    /** 屏幕内容识别拾取窗：必须住在无障碍服务这一侧。
     *  非 lazy：构造时就要订阅面板开关事件，否则第一次从面板触发时会错过 Shown。
     *  引擎走进程内单例，SurfaceActionRuntime（悬浮球/网格面板）共享同一实例。 */
    private val ocrEngine: hunoia.luno.capability.ocr.PpOcrEngine =
        hunoia.luno.domain.ocr.OcrEngineHolder.get(this)

    private val screenPickOverlay = ScreenPickOverlay(
        this,
        ocrEngine = ocrEngine,
        onSearch = { query ->
            hunoia.luno.quickaccess.QuickSearchActivity.start(this, query)
        },
        onOcrResult = { ocr, a11y, image, ocrLoader, preferOcr ->
            hunoia.luno.ui.ocr.OcrResultActivity.start(
                this,
                hunoia.luno.ui.ocr.OcrResultPayload(
                    ocrText = ocr,
                    a11yText = a11y,
                    image = image,
                    ocrLoader = ocrLoader,
                    preferOcr = preferOcr,
                ),
            )
        },
        onOpenLink = { url ->
            val settings = runCatching {
                hunoia.luno.config.ConfigProvider.getActionSettings().linkDispatch
            }.getOrDefault(hunoia.luno.config.model.LinkDispatchSettings())
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
        onAnnotate = { bitmap ->
            hunoia.luno.ui.annotate.ImageAnnotateActivity.startWithBitmap(this, bitmap)
        },
        screenPickGesture = {
            if (::gestureCoordinator.isInitialized) {
                gestureCoordinator.currentScreenPickGesture()
            } else {
                hunoia.luno.config.model.ScreenPickGesture()
            }
        },
    )

    /** 屏幕标注浮层实例；onClosed 时置空。 */
    private var annotateOverlay: hunoia.luno.annotate.ui.AnnotateOverlay? = null

    /** 截屏 + 挂窗是异步的；在途时不允许重复开第二层。 */
    private var annotateOpening = false

    /** 标注截屏/挂窗任务：锁屏时连在途任务一起作废，避免锁屏后标注层才挂上来。 */
    private var annotateOpenJob: Job? = null

    /** 标注期间球/面板一直隐藏，避免盖在画布上（关层时恢复）。 */
    private var annotateSurfacesHidden = false

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
        return screenPickOverlay.show()
    }

    /** 屏幕标注浮层（计划 2026-11-04-annotate）。
     *  截屏是异步的：这里只返回「已接管」；失败 toast，不打开空画布。 */
    fun showScreenAnnotate(): Boolean {
        if (!isDispatchReady()) return false
        if (annotateOverlay?.isShown == true || annotateOpening) return true
        annotateOpening = true
        // 整段标注期间收起自家球/面板：一来不进截图，二来不会盖在画布上抢触摸。
        hunoia.luno.runtime.surface.SurfaceController.softHideAll()
        annotateSurfacesHidden = true
        annotateOpenJob = coroutineScope.launch {
            kotlinx.coroutines.delay(64)
            val bitmap = hunoia.luno.annotate.screenshot.AnnotateScreenshot.capture(this@SideGestureService)
            // 截屏期间可能已经锁屏：此时不能再把标注层挂到 keyguard 上面。
            if (isKeyguardLocked()) {
                bitmap?.recycle()
                annotateOpening = false
                restoreAnnotateSurfaces()
                return@launch
            }
            if (bitmap == null) {
                annotateOpening = false
                restoreAnnotateSurfaces()
                hunoia.luno.bridge.ui.showToast(hunoia.luno.annotate.R.string.annotate_screenshot_failed)
                return@launch
            }
            val overlay = hunoia.luno.annotate.ui.AnnotateOverlay(
                service = this@SideGestureService,
                // 浮层重截前会先隐藏自己两窗；自家 Surface 已在打开时收起，这里只负责抓图。
                captureScreenshot = {
                    hunoia.luno.annotate.screenshot.AnnotateScreenshot.capture(this@SideGestureService)
                },
                onClosed = {
                    annotateOverlay = null
                    restoreAnnotateSurfaces()
                },
                // 偏好落库走 app 的配置门面，feature 只依赖端口。
                settingsStore = hunoia.luno.domain.annotate.AnnotateSettingsCenter,
            )
            annotateOverlay = overlay
            // open() 现在是 suspend（先读偏好再挂窗）：保持 annotateOpening 到真正挂完，
            // 否则读盘这几毫秒内再触发动作会建出第二个浮层。
            val opened = overlay.open(bitmap)
            annotateOpening = false
            if (!opened) {
                annotateOverlay = null
                restoreAnnotateSurfaces()
                hunoia.luno.bridge.ui.showToast(hunoia.luno.annotate.R.string.annotate_screenshot_failed)
            }
        }
        return true
    }

    private fun restoreAnnotateSurfaces() {
        if (!annotateSurfacesHidden) return
        annotateSurfacesHidden = false
        hunoia.luno.runtime.surface.SurfaceController.restoreAll()
    }

    private fun isKeyguardLocked(): Boolean =
        runCatching {
            getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        }.getOrDefault(false)

    /**
     * 锁屏收口：无障碍 overlay 会盖在 keyguard 上，锁屏时全部关闭、在途任务作废。
     * 解锁不自动恢复，需用户重新触发。
     */
    fun closeOverlaysForLock() {
        annotateOpenJob?.cancel()
        annotateOpenJob = null
        annotateOpening = false
        annotateOverlay?.close()
        annotateOverlay = null
        restoreAnnotateSurfaces()
        screenPickOverlay.close()
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
        if (hunoia.luno.gesture.BuildConfig.DEBUG) {
            hunoia.luno.core.LunoLog.i(LOG_TAG, "screen pick portal at ($x,$y) shown=$shown")
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
                    hunoia.luno.core.LunoLog.w(LOG_TAG, "grid panel direct config read failed: ${it.message}")
                }
                .getOrDefault(hunoia.luno.config.model.ActionSettings())
            hunoia.luno.gridpanel.GridPanelOverlay.showIfExists(
                actionSettings, panelId, anchorX, anchorY,
            )
        }
    }

    override fun dispatchAction(
        action: StoredAction,
        idempotencyKey: String?,
        origin: hunoia.luno.core.domain.DispatchOrigin,
        onSettled: ((typeId: String, result: hunoia.luno.core.domain.ActionResult) -> Unit)?,
    ) {
        if (::gestureCoordinator.isInitialized) {
            gestureCoordinator.dispatchAction(action, idempotencyKey, origin, onSettled)
        }
    }

    /**
     * 悬浮球点按动作统一转发入口：动作分类在容器（见 GestureCoordinator.forwardBallTap）。
     */
    fun forwardBallTap(
        action: StoredAction,
        anchorX: Int,
        anchorY: Int,
        colorSpec: ColorSpec,
    ): Boolean {
        if (!::gestureCoordinator.isInitialized) return false
        gestureCoordinator.forwardBallTap(action, anchorX, anchorY, colorSpec)
        return true
    }

    /**
     * 网格面板项的点按转发：与球点按走同一条锚点链（BallTapActionEvent → 容器
     * runGestureActions 分类），子手势/音量刮擦/嵌套弧形面板项因此自动获得正确语义。
     * 面板浮窗自身没有手势会话（SAW 窗口，宿主是 a11y 服务），绝不能绕过容器直发。
     */
    fun forwardPanelTap(
        action: StoredAction,
        anchorX: Int,
        anchorY: Int,
        colorSpec: ColorSpec,
    ): Boolean = forwardBallTap(action, anchorX, anchorY, colorSpec)

    /** 分发链就绪：coordinator 已初始化。供 DispatchService 跨进程查询。 */
    fun isDispatchReady(): Boolean = ::gestureCoordinator.isInitialized

    override fun onSetOverlay() {
        current = this
        if (!speechReceiverRegistered) {
            registerReceiver(
                speechResultReceiver,
                IntentFilter(hunoia.luno.quickaccess.SpeechRecognitionActivity.ACTION_SPEECH_RESULT),
                RECEIVER_NOT_EXPORTED,
            )
            speechReceiverRegistered = true
        }
        hunoia.luno.core.LunoLog.i(
            LOG_TAG,
            "service: onSetOverlay pid=${android.os.Process.myPid()}",
        )
        // debug-only 探测入口（app/src/debug 的 ScreenPickProbeReceiver）；release 恒为 null。
        ScreenPickProbe.hook = { x, y -> probeCapture(x, y) }
        // 剪贴板捕获的唯一监听点：常驻无障碍服务覆盖面大于球/面板，且不受应用窗口可见性限制。
        ClipboardCapture.start(coroutineScope)
        // 服务起来就把引擎加载 / 预热一次：第一次长按不用再等 GPU delegate 建context，
        // 加载失败也会在 logcat 留下 w 级日志（见 PpOcrModelRunner）。
        coroutineScope.launch { runCatching { ocrEngine.warmUp() } }
        if (::gestureCoordinator.isInitialized) gestureCoordinator.onDestroy()
        gestureCoordinator = GestureCoordinator(this)
        // 球侧（:feature:floatball）经注入点下发命令，避免反向依赖宿主服务。
        hunoia.luno.gesture.GestureCommand.showGridPanel = { action, anchorX, anchorY ->
            showGridPanelDirect(action, anchorX, anchorY)
        }
        hunoia.luno.gesture.GestureCommand.forwardBallTap = { action, anchorX, anchorY, colorSpec ->
            forwardBallTap(action, anchorX, anchorY, colorSpec)
        }
        hunoia.luno.gesture.GestureCommand.forwardPanelTap = { action, x, y, colorSpec ->
            forwardPanelTap(action, x, y, colorSpec)
        }
        hunoia.luno.gesture.GestureCommand.insertText = { text, x, y -> insertTextAt(text, x, y) }
        // 网格面板「加项」要起选择页：路由与宿主 Activity 都在 :app，面板模块只发中性请求。
        hunoia.luno.gridpanel.GridPanelCommand.openItemPicker = { pick ->
            hunoia.luno.ui.actionselect.ActionSelectHostActivity.start(
                this,
                hunoia.luno.picker.ActionSelect(
                    gridPanelId = pick.gridPanelId,
                    gridPanelPageIndex = pick.pageIndex,
                    gridPanelReplaceItemId = pick.replaceItemId.orEmpty(),
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
                }.onFailure { hunoia.luno.core.LunoLog.w(LOG_TAG, "probe capture failed: ${it.message}") }
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
            hunoia.luno.core.LunoLog.i(LOG_TAG, sb.toString())
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
        if (speechReceiverRegistered) {
            unregisterReceiver(speechResultReceiver)
            speechReceiverRegistered = false
        }
        super.onDestroy()
        if (current === this) currentRef = null
        ClipboardCapture.stop()
        disabledPackageEnabler.release()
        if (::gestureCoordinator.isInitialized) gestureCoordinator.onDestroy()
        runtimePanelOverlay.close()
        // 拾取窗持有全局面板事件订阅，必须显式退订。
        screenPickOverlay.release()
        // 标注浮层不随服务自动回收；关掉它并恢复被隐藏的球/面板。
        annotateOpenJob?.cancel()
        annotateOpenJob = null
        annotateOverlay?.close()
        annotateOverlay = null
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
        val top = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (!top.isNullOrBlank() && top != packageName) return top

        // 面板/浮层是自身 overlay 窗口：打开时「活动窗口」就是 Luno，
        // 会把「当前应用」污染成自身（小窗当前应用会小窗 Luno）。
        // 从窗口列表按层级找第一个非自身应用窗口（面板下的真实应用）；
        // 扫不到（如停在 Luno 主页、底层应用窗口已停止）再退回最近应用历史
        // （PreviousAppTracker 维护，已过滤自身 + 黑名单 + 不可启动）。
        val fromWindows = runCatching {
            windows
                .sortedByDescending { it.layer }
                .firstOrNull { w ->
                    w.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                        runCatching { w.root?.packageName?.toString() }.getOrNull() != packageName
                }
                ?.root?.packageName?.toString()
        }.getOrNull()
        if (!fromWindows.isNullOrBlank()) {
            if (hunoia.luno.gesture.BuildConfig.DEBUG) {
                hunoia.luno.core.LunoLog.i(LOG_TAG, "currentPackage: window fallback=$fromWindows")
            }
            return fromWindows
        }

        val history = RecentAppsHub.history.value.lastOrNull()
            ?: RecentAppsHub.foreground.value.takeIf { it.isNotBlank() }
        if (hunoia.luno.gesture.BuildConfig.DEBUG) {
            hunoia.luno.core.LunoLog.i(
                LOG_TAG,
                "currentPackage: own overlay -> history/foreground=${history.orEmpty()}",
            )
        }
        return history.orEmpty()
    }
}
