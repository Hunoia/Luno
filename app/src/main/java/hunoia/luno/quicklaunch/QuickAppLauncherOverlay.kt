package hunoia.luno.quicklaunch

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
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
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
 * 启动面板悬浮窗：全屏透明画布，面板在全屏根内自定位。
 * TYPE_APPLICATION_OVERLAY 而非 a11y 窗口（a11y 必须挂无障碍服务）；代价是吃悬浮窗授权，未授权时引导。
 * 关闭 = 移窗 + 销毁 owner，下次全新 attach；!attached 重挂只是 ROM 清理/权限撤销的恢复路径。
 * owner 必须推到 RESUMED；键盘自绘，保持 FLAG_NOT_FOCUSABLE。
 */
object QuickAppLauncherOverlay {

    private const val TAG = "LunoLauncher"

    private val context: Context get() = AppContext.get()
    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var attached = false
    private var lifecycleOwner: PanelLifecycleOwner? = null

    /**
     * 组合首次创建时经 onStateReady 登记；销毁重建之间常驻，[show] 用它驱动新会话。
     * teardown 时置 null（组合释放后 state 已 dispose，不能再碰）。
     */
    private var quickState: QuickAppLauncherState? = null

    /**
     * 组合注册的退场动画执行体（state.closeAnimated）；组合重建后会重新注册，
     * 不得在 show 里清空。
     */
    private var closeAnimated: (() -> Unit)? = null

    /**
     * 小窗启动参数：首次 show 时 IO 预载一次；首帧即点小窗启动可能读到默认值。
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

    /** 从手势/Service 上下文唤起。权限检查在主线程 hop 之前（均线程安全）；未授权时平台 Toast + 跳设置页。 */
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
        val windowReady = when {
            current == null -> attach()
            // 窗口被系统摘除（ROM 激进清理 / 权限撤销）：原实例重挂，组合未销毁直接复用；
            // 重挂失败说明组合可能已损坏，销毁旧 owner 走全新 attach。
            !attached -> {
                if (!wm.safeAddView(current, layoutParamsFor())) {
                    Log.e(TAG, "quick launcher re-attach failed, falling back to fresh attach")
                    teardownDetached()
                    attach()
                } else {
                    correctWindowOrigin(current)
                    true
                }
            }
            // 已挂载：只开不关，内容保留，只补一次焦点。
            else -> {
                current.requestFocus()
                true
            }
        }
        // 挂窗失败（悬浮窗权限被撤 / ROM 拒绝）：不要发 Shown —— 否则 FloatBallService
        // 的 panelCount 会 +1 而永远等不到配对的 Dismissed。
        if (!windowReady || view == null) return
        lifecycleOwner?.resume()
        // 驱动新会话。已可见时不清词；关闭动画进行中（panelVisible=false）则重开会话
        // 并复位 closing 闩锁。首开时 quickState 还是 null（组合下一帧才跑），由 onStateReady 代劳。
        quickState?.let {
            if (!it.panelVisible) {
                it.resetForNewSession()
                it.setPanelShown(true)
            }
        }
        if (!shownEventPosted) {
            shownEventPosted = true
            Events.post(PanelShownEvent())
        }
    }

    private fun attach(): Boolean {
        val owner = PanelLifecycleOwner().also { it.create() }
        // ComposeView final：用可获焦容器接 BACK，内嵌 ComposeView。
        val created = object : FrameLayout(context) {
            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        (closeAnimated ?: { closeImmediate() }).invoke()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        owner.attachTo(created)
        created.addOnAttachStateChangeListener(attachListener)
        val content = ComposeView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // 重挂不能靠默认策略：窗口一被摘掉组合就释放，重新 addView 时
            // shouldCreateCompositionOnAttachedToWindow 已置 false、不会重建，
            // 「!attached 重挂原实例」只会得到一块空白板。改成随 owner 销毁，
            // 才合得上 PanelLifecycleOwner「不随 detach 自动销毁」的设计。
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // 必须显式限定 QuickAppLauncherOverlay.：ComposeView 自己有 public 的
            // @Composable fun Content()，apply 块里裸写 `Content()` 会解析成它，
            // 变成「透传 → 调回自己」的无限递归（打开即 StackOverflowError）。
            setContent { QuickAppLauncherOverlay.Content() }
        }
        created.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        if (!wm.safeAddView(created, layoutParamsFor())) {
            Log.e(TAG, "quick launcher overlay addView failed (overlay permission or window rejected)")
            showPlatformToast(R.string.quick_launcher_start_failed)
            owner.destroy()
            return false
        }
        // addView 之后这段是裸的：owner.resume() 后组合才真正开跑，调用方没有兜，这里抛出就是直接杀 :service 进程，故整段收口 + 回滚。
        try {
            view = created
            lifecycleOwner = owner
            created.requestFocus()
            // 面包屑：后面几行的异常来自组合协程，try 抓不到；看到这行紧接着崩溃
            // 就说明是 resume 之后的异步段（LaunchedEffect / DataStore 收流）。
            Log.i(TAG, "quick launcher overlay attached, resuming owner (composition coroutines start here)")
            owner.resume()
            correctWindowOrigin(created)
        } catch (t: Throwable) {
            // 故意兜 Throwable（含 Error）：此处唯一的目标是不让挂载失败变成进程崩溃。
            Log.e(TAG, "quick launcher overlay attach failed after addView, rolling back", t)
            rollbackAttach(created, owner)
            return false
        }
        return true
    }

    /** 面板可见时为 true：保证 Shown/Dismissed 事件配对。 */
    private var shownEventPosted = false

    private fun postDismissed() {
        if (!shownEventPosted) return
        shownEventPosted = false
        Events.post(PanelDismissedEvent())
    }

    /**
     * 退场动画的落点（state.closeAnimated 播完 fadeOut + delay 后调用）：移窗 + 销毁
     * owner，组合随之释放，DisposableEffect 里 state.dispose() 解注册广播。
     */
    private fun closeImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { closeImmediate() }
            return
        }
        // 竞态守卫：退场动画的 delay 期间用户再次触发 → show() 已把 panelVisible 置回
        // true 并复位 closing，此时必须放弃销毁，否则刚重开的面板被摘掉。
        if (quickState?.panelVisible == true) return
        postDismissed()
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
        disabledPackageEnabler.release()
    }

    /** 回滚「已 addView 但没挂完」的浮窗：必须移窗（全屏可触摸，留着会挡住底下触摸），先移窗再销毁 owner。 */
    private fun rollbackAttach(created: View, owner: PanelLifecycleOwner) {
        context.removeWindow(created)
        if (view === created) view = null
        if (lifecycleOwner === owner) lifecycleOwner = null
        attached = false
        closeAnimated = null
        quickState = null
        disabledPackageEnabler.release()
        owner.destroy()
    }

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
     * 2038 上悬浮窗的 LP y 原点实测在状态栏下沿。本窗口用 LAYOUT_IN_SCREEN 期望按屏幕
     * 坐标摆，但 ROM 行为不一 —— attach 后实测窗口顶边，非 0 就把 lp.y 反向修正。
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
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            // 锁屏上也可见；球窗没有这个 flag，属首次尝试，需真机验证。
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
     * 平台 Toast（非 Compose 通道）：引导授权时球必然已 detach，Compose 的 showToast
     * 没有宿主消费会被静默丢弃；addView 失败时窗口没挂上，同样无主题宿主。
     */
    private fun showPlatformToast(@StringRes resId: Int) {
        runCatching {
            Toast.makeText(context.applicationContext, resId, Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun Content() {
        SideGestureTheme {
            // 直读 SearchCenter.launcher（跨进程 DataStore 门面），不依赖 Activity 预读。
            // initialValue 用 null 而非空配置：null = 还没读到，空配置会让首帧走错分支糊一帧。
            val settings by SearchCenter.launcher
                .collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@SideGestureTheme
            QuickAppLauncherRoot(
                initialSettings = s,
                requestEnableDisabledPackage = disabledPackageEnabler::request,
                onCloseAnimated = { closeImmediate() },
                onClose = { (closeAnimated ?: { closeImmediate() }).invoke() },
                onLaunch = { appInfo, miniWindow ->
                    // 用 AppContext（非 Activity）：Launcher 自带 FLAG_ACTIVITY_NEW_TASK，
                    // BAL 由面板窗口可见性放行。
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
                    // 仅「首次组合」触发（组合随 owner 存活）：登记常驻引用 + 首会话初始化，
                    // 之后的开会由 show() 用引用直接驱动。
                    quickState = state
                    state.resetForNewSession()
                    state.setPanelShown(true)
                },
            )
        }
    }
}
