package hunoia.luno.quicklaunch

import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.R
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.core.Events
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
import hunoia.luno.quicklaunch.panel.QuickAppLauncherRoot
import hunoia.luno.quicklaunch.panel.QuickAppLauncherState
import hunoia.luno.runtime.overlay.OverlayPanelHost
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.launch
import hunoia.luno.core.LOG_TAG

/**
 * 启动面板悬浮窗：全屏透明画布，面板在全屏根内自定位。
 * TYPE_APPLICATION_OVERLAY 而非 a11y 窗口（a11y 必须挂无障碍服务）；代价是吃悬浮窗授权，未授权时引导。
 * 关闭 = 移窗 + 销毁 owner，下次全新 attach；!attached 重挂只是 ROM 清理/权限撤销的恢复路径。
 * 窗口生命周期骨架见 [OverlayPanelHost]；键盘自绘，保持 FLAG_NOT_FOCUSABLE。
 */
object QuickAppLauncherOverlay : OverlayPanelHost(
    logTag = LOG_TAG,
    logPrefix = "quick launcher overlay",
    showWhenLocked = true,
) {

    override val surfaceId: String = "quick-launcher"

    private const val TAG = LOG_TAG

    // 组合消费的状态：重复 show 原位换锚点（同 GridPanelOverlay）。
    private var anchorXState by mutableIntStateOf(0)
    private var anchorYState by mutableIntStateOf(0)

    /**
     * 组合首次创建时经 onStateReady 登记；销毁重建之间常驻，[show] 用它驱动新会话。
     * teardown 时置 null（组合释放后 state 已 dispose，不能再碰）。
     */
    private var quickState: QuickAppLauncherState? = null

    private val disabledPackageEnabler by lazy {
        DisabledPackageEnabler(
            context = AppContext.get(),
            scopeProvider = { AppContext.applicationScope!! },
            log = { message -> Log.i(TAG, message) },
        )
    }

    /** 从手势/Service 上下文唤起（锚点=触发触点）。权限检查在主线程 hop 之前（均线程安全）；未授权时平台 Toast + 跳设置页。 */
    fun show(anchorX: Int, anchorY: Int) {
        if (!context.canDrawOverlays()) {
            showPlatformToast(R.string.quick_launcher_need_overlay_permission)
            context.gotoOverlaySettings()
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(anchorX, anchorY) }
            return
        }
        anchorXState = anchorX
        anchorYState = anchorY
        val current = view
        val windowReady = when {
            current == null -> attach()
            // 窗口被系统摘除（ROM 激进清理 / 权限撤销）：原实例重挂，组合未销毁直接复用；
            // 重挂失败说明组合可能已损坏，销毁旧 owner 走全新 attach。
            !attached -> {
                if (!wm.safeAddView(current, layoutParamsFor())) {
                    Log.e(TAG, "quick launcher re-attach failed, falling back to fresh attach")
                    teardownRefs()
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

    /**
     * 退场动画的落点（state.closeAnimated 播完 fadeOut + delay 后调用）：移窗 + 销毁
     * owner，组合随之释放，DisposableEffect 里 state.dispose() 解注册广播。
     */
    override fun closeImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { closeImmediate() }
            return
        }
        // 竞态守卫：退场动画的 delay 期间用户再次触发 → show() 已把 panelVisible 置回
        // true 并复位 closing，此时必须放弃销毁，否则刚重开的面板被摘掉。
        if (quickState?.panelVisible == true) return
        postDismissed()
        removeAndTeardown()
    }

    override fun onBackKeyUp() {
        (closeAnimated ?: { closeImmediate() }).invoke()
    }

    override fun onAddViewFailed() {
        showPlatformToast(R.string.quick_launcher_start_failed)
    }

    override fun teardownExtra() {
        quickState = null
        disabledPackageEnabler.release()
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
    override fun panelContent() {
        SideGestureTheme {
            // 经搜索域门面读启动器配置，不依赖 Activity 预读。
            // initialValue 用 null 而非空配置：null = 还没读到，空配置会让首帧走错分支糊一帧。
            val settings by hunoia.luno.domain.search.SearchCenter.launcher
                .collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@SideGestureTheme
            QuickAppLauncherRoot(
                initialSettings = s,
                anchorX = anchorXState,
                anchorY = anchorYState,
                requestEnableDisabledPackage = disabledPackageEnabler::request,
                onCloseAnimated = { closeImmediate() },
                onClose = { (closeAnimated ?: { closeImmediate() }).invoke() },
                onLaunch = { appInfo, miniWindow ->
                    // Surface 只构造动作；启动、统计、三态统一由进程级 Dispatcher 负责。
                    hunoia.luno.runtime.surface.SurfaceDispatch.dispatch(
                        action = hunoia.luno.core.domain.AppLaunchAction.stored(
                            packageName = appInfo.packageName,
                            miniWindow = miniWindow,
                        ),
                        origin = hunoia.luno.core.domain.DispatchOrigin.PANEL,
                    ) { launched ->
                        if (!launched) showPlatformToast(R.string.quick_launcher_start_failed)
                    }
                    // 已提交即视为「已接管」：真实失败由 onSettled 的平台 Toast 反馈。
                    true
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
