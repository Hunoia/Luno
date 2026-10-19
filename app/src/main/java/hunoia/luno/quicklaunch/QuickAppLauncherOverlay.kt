package hunoia.luno.quicklaunch

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import hunoia.luno.App
import hunoia.luno.R
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.core.AppContext
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.quicklaunch.panel.QuickAppLauncherRoot
import hunoia.luno.quicklaunch.panel.QuickAppLauncherState
import hunoia.luno.runtime.overlay.PanelLifecycleOwner
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 启动面板悬浮窗（对齐 GridPanelOverlay 的 SAW 浮窗模式），取代原透明 Activity 载体。
 *
 * 为什么是 `TYPE_APPLICATION_OVERLAY` 而非 `TYPE_ACCESSIBILITY_OVERLAY`：与网格面板
 * 同考虑 —— 面板要做到「无障碍没开也能用」，a11y 型窗口必须有无障碍服务挂载才合法。
 * 代价是吃悬浮窗授权（球用户必然已有；纯手势用户触发时引导授权，见 [show]）。
 *
 * 窗口全屏（MATCH_PARENT），面板在全屏根内自定位 —— 交互语义与 Activity 版一致
 * （点 scrim 关、自绘键盘、面板内调整页都不跨 Activity），窗口本身只当画布。
 *
 * 关闭策略（销毁重建，与网格面板一致）：退场动画落点 [closeImmediate] 移窗 +
 * 销毁 owner，组合与 QuickAppLauncherState 随之释放；下次 show 全新 attach
 * （读磁盘快照 → 全量重扫），行为与 Activity 版逐项一致。「!attached 重挂原实例」
 * 只作为窗口被系统摘除（ROM 清理/权限撤销）的恢复路径。
 *
 * owner 必须推到 RESUMED：QuickAppLauncherContent 的 collectAsStateWithLifecycle
 * 和 LaunchedEffect 动画都要 STARTED 以上（球窗停在 CREATED 就是为了避开这块，
 * 见 FloatBallLifecycleOwner 类注释）。
 *
 * 键盘是面板自绘（KeyboardRow/KeyboardKey），不依赖系统 IME —— 所以窗口可以像
 * 网格面板一样保持 FLAG_NOT_FOCUSABLE（不收系统返回键，点 scrim 关闭，已知行为变化）。
 */
object QuickAppLauncherOverlay {

    private const val TAG = "LunoLauncher"

    private val context: Context get() = AppContext.get()
    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: ComposeView? = null
    private var attached = false
    private var lifecycleOwner: PanelLifecycleOwner? = null

    /**
     * 组合首次创建时经 onStateReady 登记；销毁重建之间常驻，[show] 用它驱动新会话。
     * teardown 时置 null（组合释放后 state 已 dispose，不能再碰）。
     */
    private var quickState: QuickAppLauncherState? = null

    /**
     * 组合注册的退场动画执行体（state.closeAnimated）。组合重建后会重新注册，
     * **不得**在 show 里清空 —— 和 GridPanelOverlay 同款约束。
     */
    private var closeAnimated: (() -> Unit)? = null

    /**
     * 小窗启动参数：首次 show 时 IO 预载一次（对齐 Activity onCreate 的读法）。
     * 首帧即点小窗启动可能读到默认值 —— 与 Activity 版同语义，低风险。
     */
    @Volatile
    private var advancedSettings = AdvancedSettings()
    private var advancedLoaded = false

    private val disabledPackageEnabler by lazy {
        DisabledPackageEnabler(
            context = AppContext.get(),
            scopeProvider = { App.applicationScope },
            log = { message -> Log.i(TAG, message) },
        )
    }

    /**
     * 从手势/Service 上下文唤起启动面板。
     *
     * 权限检查放在主线程 hop 之前：canDrawOverlays / Toast / startActivity 均线程安全，
     * 后台线程（手势协程）触发时引导也能立即发生。未授权 → 平台 Toast + 跳系统设置页，
     * 不回退 Activity（决策：引导授权）。
     */
    fun show() {
        if (!context.canDrawOverlays()) {
            showPlatformToast(R.string.quick_launcher_need_overlay_permission)
            context.gotoOverlaySettings()
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show() }
            return
        }
        ensureAdvancedSettings()
        val current = view
        when {
            // 首开（或销毁重建后）：全新挂载。
            current == null -> attach()
            // 窗口被系统摘除（ROM 激进清理 / 权限撤销）：原实例重挂，组合未销毁直接复用。
            // 重挂也失败 → 组合可能已损坏，销毁旧 owner 走全新 attach（GridPanelOverlay 无此兜底）。
            !attached -> {
                if (!wm.safeAddView(current, layoutParamsFor())) {
                    Log.e(TAG, "quick launcher re-attach failed, falling back to fresh attach")
                    teardownDetached()
                    attach()
                } else {
                    correctWindowOrigin(current)
                }
            }
            // 已挂载：等同 Activity onNewIntent 的「只开不关」，内容保留，不做任何事。
            else -> Unit
        }
        lifecycleOwner?.resume()
        // 驱动新会话。已可见（panelVisible=true）时不清词 —— 对齐 Activity onNewIntent
        // 保留内容的语义；关闭动画进行中（panelVisible=false）则重开会话并复位 closing 闩锁。
        // 首开时 quickState 还是 null（组合下一帧才跑），由 onStateReady 代劳。
        quickState?.let {
            if (!it.panelVisible) {
                it.resetForNewSession()
                it.setPanelShown(true)
            }
        }
    }

    private fun attach() {
        val owner = PanelLifecycleOwner().also { it.create() }
        val created = ComposeView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            owner.attachTo(this)
            addOnAttachStateChangeListener(attachListener)
            // 重挂关键：默认策略窗口一被摘掉组合就释放，而重新 addView 时
            // shouldCreateCompositionOnAttachedToWindow 已被置 false、不会重建 ——
            // 于是 show() 的「!attached 重新挂原实例」分支只会得到一块空白板。
            // 改成随 owner 销毁，才对得上 PanelLifecycleOwner「不随 detach 自动销毁」
            // 的设计（真正关闭时由 closeImmediate 调 destroy() 释放）。
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // 必须显式限定 QuickAppLauncherOverlay.：ComposeView 自己有一个 public 的
            // @Composable fun Content()（AbstractComposeView 的抽象成员，内容透传
            // `{ content.value?.invoke(this) }`），而这里在 apply 块里、apply 的接收者
            // 比外层对象更近 —— 裸写 `Content()` 会被解析成 ComposeView.Content()，
            // 变成「透传 → 调回自己」的无限递归，真机表现是打开即 StackOverflowError
            // （GridPanelOverlay 同款教训）。
            setContent { QuickAppLauncherOverlay.Content() }
        }
        if (!wm.safeAddView(created, layoutParamsFor())) {
            Log.e(TAG, "quick launcher overlay addView failed (overlay permission or window rejected)")
            showPlatformToast(R.string.quick_launcher_start_failed)
            owner.destroy()
            return
        }
        // addView 之后这段是裸的。owner.resume() 把 owner 推到 RESUMED，组合里的
        // collectAsStateWithLifecycle / LaunchedEffect 正是从这一刻起真正开跑（球窗停在
        // CREATED 就是为了避开这块，见 FloatBallLifecycleOwner 类注释）；而调用方里
        // （手势协程 / FloatBallService 的 scope.launch 内）也没兜。这里抛出就是
        // 直接杀 :service 进程，故整段收口 + 回滚。
        try {
            view = created
            lifecycleOwner = owner
            // 面包屑：后面几行的异常来自组合协程，try 抓不到；看到这行紧接着崩溃
            // 就说明是 resume 之后的异步段（LaunchedEffect / DataStore 收流）。
            Log.i(TAG, "quick launcher overlay attached, resuming owner (composition coroutines start here)")
            owner.resume()
            correctWindowOrigin(created)
        } catch (t: Throwable) {
            // 故意兜 Throwable（含 Error）：此处唯一的目标是不让挂载失败变成进程崩溃。
            Log.e(TAG, "quick launcher overlay attach failed after addView, rolling back", t)
            rollbackAttach(created, owner)
        }
    }

    /**
     * state 退场动画的落点（state.closeAnimated 播完 fadeOut + delay 后调用）：
     * 移窗 + 销毁 owner（组合随之释放 → DisposableEffect → state.dispose() 解注册广播）。
     * 下次 show 走全新 attach（销毁重建，与网格面板一致）。
     */
    private fun closeImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { closeImmediate() }
            return
        }
        // 竞态守卫：退场动画的 delay 期间用户再次触发 → show() 已把 panelVisible 置回
        // true（setPanelShown 同时复位 closing 闩锁），此时必须放弃销毁，否则刚重开的
        // 面板被摘掉（Activity 版靠 finish 也有此竞态，overlay 版显式兜住）。
        if (quickState?.panelVisible == true) return
        val current = view ?: return
        context.removeWindow(current)
        teardownDetached()
    }

    /** 清全部引用并销毁 owner（组合随之释放 → state.dispose()）。 */
    private fun teardownDetached() {
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        view = null
        attached = false
        closeAnimated = null
        quickState = null
    }

    /**
     * 回滚「已 addView 但没挂完」的浮窗。**必须移窗**：本窗口是全屏可触摸的，
     * 留在屏幕上会挡住底下所有触摸；而 view 已置 null 时没有任何路径能清掉它，
     * 下一次 show 只会再叠一个全屏窗。销毁顺序与 teardownDetached 一致（先移窗再销毁 owner）。
     */
    private fun rollbackAttach(created: ComposeView, owner: PanelLifecycleOwner) {
        context.removeWindow(created)
        if (view === created) view = null
        if (lifecycleOwner === owner) lifecycleOwner = null
        attached = false
        closeAnimated = null
        quickState = null
        owner.destroy()
    }

    /**
     * 小窗启动参数预载：首次 show 时 IO 读一次。与 Activity 版同语义 ——
     * 首帧即点小窗启动可能读到默认 AdvancedSettings。
     */
    private fun ensureAdvancedSettings() {
        if (advancedLoaded) return
        advancedLoaded = true
        App.applicationScope.launch(Dispatchers.IO) {
            runCatching { ConfigProvider.getAdvancedSettings() }
                .getOrDefault(AdvancedSettings())
                .let { advancedSettings = it }
        }
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
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            // 对齐原 Activity 清单的 showWhenLocked="true"（球窗无此 flag，无先例，真机验证）。
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
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

    /**
     * 平台 Toast（非 Compose 通道）：权限引导发生时球必然已 detach
     * （FloatBallService.applyVisibility：未授权不挂球），此刻 showToast 的
     * Compose 通道没有宿主消费，会被静默丢弃 —— 恰是最需要它的时候。
     * addView 失败提示同理（此刻窗口没挂上，无主题宿主）。
     */
    private fun showPlatformToast(@StringRes resId: Int) {
        runCatching {
            Toast.makeText(context.applicationContext, resId, Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun Content() {
        SideGestureTheme {
            // 配置直读 SearchCenter.launcher（= ConfigProvider.quickAppLauncherSettings，
            // 跨进程 DataStore 门面），不依赖 Activity 的 onCreate 预读。
            //
            // initialValue 给 null 而不是空配置：null = 还没读到，此刻什么都不渲染 ——
            // 窗口本来就是透明画布，首帧观感交给 QuickAppLauncherRoot 的 AnimatedVisibility
            // 淡入（GridPanelOverlay 同款教训：空配置会让首帧走错分支糊一帧）。
            val settings by SearchCenter.launcher
                .collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@SideGestureTheme
            QuickAppLauncherRoot(
                initialSettings = s,
                requestEnableDisabledPackage = disabledPackageEnabler::request,
                // 退场动画（fadeOut + delay）的落点：移窗 + 销毁 owner/组合/state。
                onCloseAnimated = { closeImmediate() },
                onClose = { (closeAnimated ?: { closeImmediate() }).invoke() },
                onLaunch = { appInfo, miniWindow ->
                    // context 用 AppContext（非 Activity）：Launcher 内部自带
                    // FLAG_ACTIVITY_NEW_TASK，BAL 由面板窗口可见性放行（与 Activity 版同机制）。
                    val success = Launcher.launchAppInfo(
                        context = AppContext.get(),
                        appInfo = appInfo,
                        miniWindow = miniWindow,
                        miniWindowSettings = advancedSettings.miniWindowSettings(),
                    )
                    if (success) {
                        App.applicationScope.launch(Dispatchers.IO) {
                            runCatching {
                                ConfigProvider.recordQuickAppLaunch(
                                    "${appInfo.packageName}/${appInfo.className}",
                                )
                            }
                        }
                    }
                    success
                },
                onRegisterCloseAnimated = { callback -> closeAnimated = callback },
                onStateReady = { state ->
                    // 仅「史上第一次组合」触发（组合随 owner 存活，销毁重建后才会再来一次）：
                    // 登记常驻引用 + 首会话初始化。后续开会在 show() 里用引用直接驱动。
                    quickState = state
                    state.resetForNewSession()
                    state.setPanelShown(true)
                },
            )
        }
    }
}
