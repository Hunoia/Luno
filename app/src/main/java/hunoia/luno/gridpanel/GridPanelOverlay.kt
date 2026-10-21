package hunoia.luno.gridpanel

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.core.ActionSelectClosedEvent
import hunoia.luno.core.AppContext
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.domain.action.ActionLibrary
import kotlinx.coroutines.launch
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.runtime.overlay.PanelLifecycleOwner
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.service.SideGestureService
import hunoia.luno.ui.actionselect.ActionSelectHostActivity
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.theme.SideGestureTheme

/**
 * 网格面板悬浮窗（TYPE_APPLICATION_OVERLAY）：面板要「无障碍没开也能用」，a11y 窗口不行；代价是吃悬浮窗授权。
 * 窗口全屏只当画布；组合常驻复用：跳选择页只置 alpha 0 不可触摸，关闭才移除。
 * owner 必须推到 RESUMED：collectAsStateWithLifecycle 与 LaunchedEffect 都要 STARTED 以上。
 */
object GridPanelOverlay {

    private const val TAG = "LunoLauncher"

    private val context: Context get() = AppContext.get()
    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var attached = false
    private var lifecycleOwner: PanelLifecycleOwner? = null

    /** 面板可见时为 true：保证 Shown/Dismissed 事件配对（重复 show 不重复发）。 */
    private var shownEventPosted = false

    // 组合消费的状态：重复 show 原位换面板/锚点。
    private var panelIdState by mutableStateOf("")
    private var anchorXState by mutableIntStateOf(0)
    private var anchorYState by mutableIntStateOf(0)

    /** 组合注册的关闭动画执行体。组合常驻期间一直有效，**不得**在 show 里清空
     *  —— LaunchedEffect(Unit) 只注册一次，清了就再没人注册回来。 */
    private var closeAnimated: (() -> Unit)? = null

    /** 组合注册的返回键处理器：返回 true 表示已消费（如退出编辑态）。 */
    private var backHandler: (() -> Boolean)? = null

    /** 返回键：优先让组合消费（编辑态退编辑），否则播退场动画关闭。 */
    fun dismissFromBack(): Boolean {
        if (!shownEventPosted) return false
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissFromBack() }
            return true
        }
        val handled = backHandler?.invoke() == true
        if (!handled) (closeAnimated ?: { closeImmediate() }).invoke()
        return true
    }

    /**
     * 前台包变化：第三方桌面小部件的点击由 RemoteViews 内部处理，Luno 拿不到
     * 回调；它拉起新应用后只能靠窗口切换事件收口，否则全屏面板会一直盖在新应用上。
     */
    fun onForegroundPackageChanged(fromPackage: String, toPackage: String) {
        if (!shownEventPosted) return
        if (toPackage.isEmpty() || toPackage == context.packageName) return
        // 从本应用 Activity（如动作选择页）返回下层应用时，包名会从 hunoia.luno
        // 变回去；这是面板刚回显的正常路径，不能当成“小部件拉起应用”而立刻关掉。
        if (fromPackage == context.packageName) return
        (closeAnimated ?: { closeImmediate() }).invoke()
    }

    private val closedSubscriber: (ActionSelectClosedEvent) -> Unit = {
        // 选择页结束：面板回显（编辑态原样继续 —— 组合里的 editMode/localItems 都在）。
        show(panelIdState, anchorXState, anchorYState)
    }

    /**
     * 球侧直发的入口：容器不在线（无障碍没开）时球自己读配置再调这里。
     * 悬空引用（面板被删）静默收口，**空面板照常弹** —— 面板本体就是编辑入口。
     * 走容器的路径（触钮/侧滑/子手势）由 executor 解析面板后调 [show]。
     */
    fun showIfExists(actionSettings: ActionSettings, panelId: String, anchorX: Int, anchorY: Int) {
        val panel = actionSettings.actionGridPanels.panelById(panelId) ?: return
        show(panel.id, anchorX, anchorY)
    }

    fun show(panelId: String, anchorX: Int, anchorY: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(panelId, anchorX, anchorY) }
            return
        }
        panelIdState = panelId
        anchorXState = anchorX
        anchorYState = anchorY
        val current = view
        val windowReady = when {
            current == null -> attach()
            !attached -> {
                // 窗口被系统移除（ROM 激进清理）：原实例重新挂，组合未销毁直接复用。
                wm.safeAddView(current, layoutParamsFor()).also { ok ->
                    if (ok) correctWindowOrigin(current)
                }
            }
            else -> {
                restoreVisibility()
                true
            }
        }
        // 挂窗失败（悬浮窗权限被撤 / ROM 拒绝）：不要发 Shown —— 否则 FloatBallService
        // 的 panelCount 会 +1 而永远等不到配对的 Dismissed，球被 hideOnPanel 永久隐藏。
        if (!windowReady || view == null) return
        lifecycleOwner?.resume()
        if (!shownEventPosted) {
            shownEventPosted = true
            Events.post(PanelShownEvent())
        }
    }

    /** 编辑态让位：窗口隐身但保留（组合与 surface 都在，选择页叠在其上）。 */
    private fun hide() {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.alpha == 0f) return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        lp.alpha = 0f
        wm.safeUpdateViewLayout(current, lp)
        postDismissed()
    }

    /** 组合的 onCloseAnimated 落点：移窗、销毁 owner（组合随之释放）。 */
    fun closeImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { closeImmediate() }
            return
        }
        Events.unsubscribe(ActionSelectClosedEvent::class, closedSubscriber)
        postDismissed()
        val current = view ?: return
        context.removeWindow(current)
        view = null
        attached = false
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        closeAnimated = null
        backHandler = null
    }

    private fun postDismissed() {
        if (!shownEventPosted) return
        shownEventPosted = false
        Events.post(PanelDismissedEvent())
    }

    private fun attach(): Boolean {
        val owner = PanelLifecycleOwner().also { it.create() }
        // ComposeView 是 final，不能子类化；用可获焦容器接 BACK，内嵌 ComposeView。
        val created = object : FrameLayout(context) {
            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) dismissFromBack()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        owner.attachTo(created)
        created.addOnAttachStateChangeListener(attachListener)
        val content = ComposeView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // 组合常驻的关键：默认策略在窗口被摘掉时就释放组合，重新 addView 时
            // 不会重建，show() 的「!attached 重新挂原实例」分支只会得到空白板。
            // 随 owner 销毁，才对得上 [PanelLifecycleOwner] 「不随 detach 自动销毁」。
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // 必须显式限定 GridPanelOverlay.：apply 块里裸写 `Content()` 会解析成
            // ComposeView.Content()（透传自身），无限递归，真机表现为打开即 StackOverflow。
            setContent { GridPanelOverlay.Content() }
        }
        created.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        if (!wm.safeAddView(created, layoutParamsFor())) {
            Log.e(TAG, "grid overlay addView failed (overlay permission or window rejected)")
            owner.destroy()
            return false
        }
        // addView 之后这段是裸的：owner.resume() 后组合的 LaunchedEffect / 收流才真正
        // 开跑，期间抛出会直接杀 :service 进程，故整段收口 + 回滚。
        try {
            view = created
            lifecycleOwner = owner
            created.requestFocus()
            // 面包屑：后面几行的异常来自组合协程，try 抓不到；看到这行紧接着崩溃
            // 就说明是 resume 之后的异步段（LaunchedEffect / DataStore 收流）。
            Log.i(TAG, "grid overlay attached, resuming owner (composition coroutines start here)")
            owner.resume()
            Events.subscribe(ActionSelectClosedEvent::class, closedSubscriber)
            correctWindowOrigin(created)
        } catch (t: Throwable) {
            // 故意兜 Throwable（含 Error）：此处唯一的目标是不让挂载失败变成进程崩溃。
            Log.e(TAG, "grid overlay attach failed after addView, rolling back", t)
            rollbackAttach(created, owner)
            return false
        }
        return true
    }

    /**
     * 回滚「已 addView 但没挂完」的浮窗。必须移窗：全屏可触摸窗留在屏幕上会挡住
     * 底下所有触摸，且 view 已置 null 时 [closeImmediate] 够不着它。
     * 销毁顺序与 [closeImmediate] 一致（先移窗再销毁 owner）。
     */
    private fun rollbackAttach(created: View, owner: PanelLifecycleOwner) {
        Events.unsubscribe(ActionSelectClosedEvent::class, closedSubscriber)
        context.removeWindow(created)
        if (view === created) view = null
        if (lifecycleOwner === owner) lifecycleOwner = null
        attached = false
        closeAnimated = null
        backHandler = null
        owner.destroy()
    }

    private fun restoreVisibility() {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.alpha == 1f && lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0) {
            current.requestFocus()
            return
        }
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        lp.alpha = 1f
        wm.safeUpdateViewLayout(current, lp)
        current.requestFocus()
    }

    /**
     * 2038 悬浮窗的 LP y 原点实测在状态栏下沿。本窗口用 LAYOUT_IN_SCREEN 期望按
     * 屏幕坐标摆，但 ROM 行为不一：attach 后实测窗口顶边，非 0 就反向修正 lp.y。
     */
    private fun correctWindowOrigin(created: View) {
        created.post {
            val current = view ?: return@post
            val location = IntArray(2)
            current.getLocationOnScreen(location)
            if (location[1] == 0) return@post
            val lp = current.layoutParams as? WindowManager.LayoutParams ?: return@post
            lp.y -= location[1]
            wm.safeUpdateViewLayout(current, lp)
        }
    }

    private fun layoutParamsFor() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        gravity = Gravity.START or Gravity.TOP
        x = 0
        y = 0
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            attached = true
        }

        override fun onViewDetachedFromWindow(v: View) {
            attached = false
        }
    }

    @Composable
    private fun Content() {
        SideGestureTheme {
            // 配置直读 TuneConfig（跨进程 DataStore 门面），不依赖 a11y 运行态快照。
            // ---- 无障碍没开时面板照常渲染，这是无 a11y 可用的另一半。 ----
            // initialValue 必须是 null：空 ActionSettings 会让首帧 panelById 落空，
            // GridPanelRoot 走「面板已删除」分支且不吃 appearance 动画，会糊一帧
            // 32% 黑遮罩；null 表示还没读到，此刻不渲染，出场交给 0→1 淡入。
            val actionSettings by TuneConfig.action
                .collectAsStateWithLifecycle(initialValue = null)
            val librarySettings by ActionLibrary.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val settings = actionSettings ?: return@SideGestureTheme
            val library = librarySettings ?: return@SideGestureTheme
            // 动作库为空会让库引用项掉图标、嵌套面板项显示裸 typeId。
            CompositionLocalProvider(
                LocalActionLibraryEntries provides library.entries,
                LocalActionPanels provides settings.actionPanels.panels,
                LocalGridPanels provides settings.actionGridPanels.panels,
                // 自绘小部件里启动外部应用后，用同一个退场动画关掉面板。
                LocalGridPanelClose provides { (closeAnimated ?: { closeImmediate() }).invoke() },
            ) {
                GridPanelRoot(
                    panel = settings.actionGridPanels.panelById(panelIdState),
                    anchorX = anchorXState,
                    anchorY = anchorYState,
                    dispatch = { action, x, y ->
                        SideGestureService.current?.forwardPanelTap(action, x, y, Color.TRANSPARENT) == true
                    },
                    onPickItem = { select ->
                        // 先让位再起选择页，否则面板会盖住新页并被误点；让位不可逆，
                        // 启动失败必须把面板放回来，否则窗口永远停在 alpha 0。
                        hide()
                        if (!ActionSelectHostActivity.start(context, select)) {
                            Log.e(TAG, "grid overlay start action select failed, restore panel")
                            restoreVisibility()
                        }
                        true
                    },
                    onClose = { (closeAnimated ?: { closeImmediate() }).invoke() },
                    onCloseAnimated = { closeImmediate() },
                    onRegisterCloseAnimated = { callback -> closeAnimated = callback },
                    onRegisterBackHandler = { handler -> backHandler = handler },
                )
            }
        }
    }
}
