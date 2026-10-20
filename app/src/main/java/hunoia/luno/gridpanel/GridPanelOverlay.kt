package hunoia.luno.gridpanel

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
 * 网格面板悬浮窗（docs/grid-panel.md）：SAW 浮窗，取代原透明 Activity 载体。
 *
 * 为什么是 `TYPE_APPLICATION_OVERLAY` 而非 `TYPE_ACCESSIBILITY_OVERLAY`：
 * 面板要做到「无障碍没开也能用」（球直发路径，见 FloatBallService.onBallTapped），
 * a11y 型窗口必须有无障碍服务挂载才合法。代价是吃悬浮窗授权 —— 球用户必然已有。
 *
 * 窗口全屏（MATCH_PARENT），卡片在全屏根内自定位 —— 交互语义与 Activity 版逐字节
 * 一致（点空白关、长按空白进编辑都由组合的全屏裁判处理），窗口本身只当画布。
 *
 * 生命周期：组合常驻复用（一墨「重选让位」语义）。编辑态跳选择页时 [hide] 只把
 * 窗口置 alpha 0 + 不可触摸（**不 removeView**），ComposeView 与组合原样保留；
 * 选择页销毁发 [ActionSelectClosedEvent]（双方同在 :service 进程，进程内总线）
 * 回显。真正关闭才 [closeImmediate]：移窗 + 销毁 owner（组合随之释放）。
 *
 * owner 必须推到 RESUMED：GridPanelContent 的 `collectAsStateWithLifecycle` 和
 * LaunchedEffect 动画都要 STARTED 以上 —— 球窗停在 CREATED 是因为球没有这些。
 */
object GridPanelOverlay {

    private const val TAG = "LunoLauncher"

    private val context: Context get() = AppContext.get()
    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: ComposeView? = null
    private var attached = false
    private var lifecycleOwner: PanelLifecycleOwner? = null

    /** 面板可见时为 true：保证 Shown/Dismissed 事件配对（重复 show 不重复发）。 */
    private var shownEventPosted = false

    // 组合消费的状态：重复 show 原位换面板/锚点（等价 Activity 的 onNewIntent）。
    private var panelIdState by mutableStateOf("")
    private var anchorXState by mutableIntStateOf(0)
    private var anchorYState by mutableIntStateOf(0)

    /** 组合注册的关闭动画执行体。组合常驻期间一直有效，**不得**在 show 里清空
     *  —— LaunchedEffect(Unit) 只注册一次，清了就再没人注册回来。 */
    private var closeAnimated: (() -> Unit)? = null

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
        when {
            current == null -> attach()
            !attached -> {
                // 窗口被系统移除（ROM 激进清理）：原实例重新挂，组合未销毁直接复用。
                if (wm.safeAddView(current, layoutParamsFor())) correctWindowOrigin(current)
            }
            else -> restoreVisibility()
        }
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
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
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
    }

    private fun postDismissed() {
        if (!shownEventPosted) return
        shownEventPosted = false
        Events.post(PanelDismissedEvent())
    }

    private fun attach() {
        val owner = PanelLifecycleOwner().also { it.create() }
        val created = ComposeView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            owner.attachTo(this)
            addOnAttachStateChangeListener(attachListener)
            // 组合常驻的关键：默认策略是 DisposeOnDetachedFromWindowOrReleasedFromPool，
            // 窗口一被系统摘掉组合就释放，而重新 addView 时
            // shouldCreateCompositionOnAttachedToWindow 已被置 false、不会重建 ——
            // 于是 show() 的「!attached 重新挂原实例」分支只会得到一块空白板。
            // 改成随 owner 销毁，才对得上 [PanelLifecycleOwner] 「不随 detach 自动销毁」
            // 的设计（真正关闭时由 [closeImmediate] 调 destroy() 释放）。
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // 必须显式限定 GridPanelOverlay.：ComposeView 自己有一个 public 的
            // @Composable fun Content()（AbstractComposeView 的抽象成员，内容透传
            // `{ content.value?.invoke(this) }`），而这里在 apply 块里、apply 的接收者
            // 比外层对象更近 —— 裸写 `Content()` 会被解析成 ComposeView.Content()，
            // 变成「透传 → 调回自己」的无限递归，真机表现是打开即 StackOverflowError。
            setContent { GridPanelOverlay.Content() }
        }
        if (!wm.safeAddView(created, layoutParamsFor())) {
            Log.e(TAG, "grid overlay addView failed (overlay permission or window rejected)")
            owner.destroy()
            return
        }
        // addView 之后这段是裸的。owner.resume() 把 owner 推到 RESUMED，组合里的
        // collectAsStateWithLifecycle / LaunchedEffect 正是从这一刻起真正开跑（球窗停在
        // CREATED 就是为了避开这块，见 FloatBallLifecycleOwner 类注释）；而调用方里
        // FloatBallService.showGridPanelDirect 在 scope.launch 内也没兜。这里抛出就是
        // 直接杀 :service 进程，故整段收口 + 回滚。
        try {
            view = created
            lifecycleOwner = owner
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
        }
    }

    /**
     * 回滚「已 addView 但没挂完」的浮窗。**必须移窗**：本窗口是全屏可触摸的，
     * 留在屏幕上会挡住底下所有触摸；而 view 已置 null 时 [closeImmediate] 的
     * `view ?: return` 够不着它，没有任何路径能清掉，下一次 show 只会再叠一个全屏窗。
     * 销毁顺序与 [closeImmediate] 一致（先移窗再销毁 owner）。
     */
    private fun rollbackAttach(created: ComposeView, owner: PanelLifecycleOwner) {
        Events.unsubscribe(ActionSelectClosedEvent::class, closedSubscriber)
        context.removeWindow(created)
        if (view === created) view = null
        if (lifecycleOwner === owner) lifecycleOwner = null
        attached = false
        closeAnimated = null
        owner.destroy()
    }

    private fun restoreVisibility() {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.alpha == 1f && lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0) return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        lp.alpha = 1f
        wm.safeUpdateViewLayout(current, lp)
    }

    /**
     * 2038 悬浮窗的 LP y 原点实测在状态栏下沿（球窗的发现，见 FloatBallWindow
     * lpOriginY 注释）。本窗口用 LAYOUT_IN_SCREEN 期望按屏幕坐标摆，但 ROM 行为
     * 不一 —— attach 后实测窗口顶边，非 0 就把 lp.y 反向修正，兜住坐标系歧义。
     */
    private fun correctWindowOrigin(created: ComposeView) {
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
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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
            // 配置直读 TuneConfig（跨进程 DataStore 门面），不依赖 a11y 运行态快照
            // —— 无障碍没开时面板照常渲染，这是无 a11y 可用的另一半。
            //
            // initialValue 给 null 而不是空配置：**空 ActionSettings 会让首帧
            // panelById 落空 → GridPanelRoot 走「面板已删除」分支，而那个分支不吃
            // appearance 动画，于是全屏 32% 黑遮罩糊一帧再换真面板（真机闪屏）。
            // null = 还没读到，此刻什么都不渲染 —— 窗口本来就是透明画布，出场观感
            // 交给 GridPanelRoot 里 appearance 的 0→1 淡入，观感反而是对的。
            // 动作库同理：空 entries 会让库引用项掉图标/显示裸 typeId。
            val actionSettings by TuneConfig.action
                .collectAsStateWithLifecycle(initialValue = null)
            val librarySettings by ActionLibrary.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val settings = actionSettings ?: return@SideGestureTheme
            val library = librarySettings ?: return@SideGestureTheme
            // ActionDisplay 的行名/图标读这几个 local（默认空列表）：不给就会让
            // 动作库引用项掉图标、嵌套面板项显示裸 typeId。
            CompositionLocalProvider(
                LocalActionLibraryEntries provides library.entries,
                LocalActionPanels provides settings.actionPanels.panels,
                LocalGridPanels provides settings.actionGridPanels.panels,
            ) {
                GridPanelRoot(
                    panel = settings.actionGridPanels.panelById(panelIdState),
                    anchorX = anchorXState,
                    anchorY = anchorYState,
                    dispatch = { action, x, y ->
                        SideGestureService.current?.forwardPanelTap(action, x, y, Color.TRANSPARENT) == true
                    },
                    onPickItem = { select ->
                        // 先让位再起选择页（面板在选择页之上，不让位会盖住新页并被误点）。
                        // 但**让位不可逆**，所以启动失败必须把面板放回来 —— 否则窗口
                        // 永远停在 alpha 0，除重新触发没有恢复路径。
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
                )
            }
        }
    }
}
