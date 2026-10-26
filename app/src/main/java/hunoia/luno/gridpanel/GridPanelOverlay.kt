package hunoia.luno.gridpanel

import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.core.ActionSelectClosedEvent
import hunoia.luno.core.Events
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.widget.PanelAppWidgetHostView
import hunoia.luno.runtime.overlay.OverlayPanelHost
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.service.SideGestureService
import hunoia.luno.ui.actionselect.ActionSelectHostActivity
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.core.LOG_TAG

/**
 * 网格面板悬浮窗（TYPE_APPLICATION_OVERLAY）：面板要「无障碍没开也能用」，a11y 窗口不行；代价是吃悬浮窗授权。
 * 窗口生命周期骨架见 [OverlayPanelHost]；这里只留网格面板自己的编排与组合内容。
 */
object GridPanelOverlay : OverlayPanelHost(
    logTag = LOG_TAG,
    logPrefix = "grid overlay",
    showWhenLocked = false,
) {

    private const val TAG = LOG_TAG

    // 组合消费的状态：重复 show 原位换面板/锚点。
    private var panelIdState by mutableStateOf("")
    private var anchorXState by mutableIntStateOf(0)
    private var anchorYState by mutableIntStateOf(0)

    /** 组合注册的返回键处理器：返回 true 表示已消费（如退出编辑态）。 */
    private var backHandler: (() -> Boolean)? = null

    /** 选择页关闭后的抑制窗口截止时刻：返回下层应用的那次包切换事件不能当成
     *  「小部件拉起应用」而关掉面板。不能以 fromPackage == 本应用判断——包名
     *  tracker 是粘性的，只要用过一次选择页就停在 hunoia.luno，之后所有小部件
     *  拉起应用的事件都会被吞掉（表现为部分部件点击后面板不关闭）。 */
    private var suppressForegroundCloseUntil = 0L

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
        // 音量回弹球、灵动胶囊等都挂在 systemui/媒体系统插件包下，永远不是「部件拉起应用」。
        if (isSystemOverlayPackage(toPackage)) {
            Log.i(TAG, "foreground change ignored (system overlay target): $fromPackage -> $toPackage")
            return
        }
        // 关面板只认「部件刚被点过」：目标包等于 provider 包时给 1.5s，
        // 跨包拉起只给 800ms 短窗口。音量回弹球、灵动胶囊等系统级界面变化
        // 也会触发前台包变化，但它们通常没有部件点击，或落在短窗口之外。
        // 选择页刚关闭的窗口内同理（返回下层应用，无部件点击）。
        if (SystemClock.elapsedRealtime() < suppressForegroundCloseUntil) {
            Log.i(TAG, "foreground change suppressed (select page return): $fromPackage -> $toPackage")
            return
        }
        if (!PanelAppWidgetHostView.recentWidgetLaunch(toPackage)) {
            Log.i(TAG, "foreground change ignored (no matching widget tap): $fromPackage -> $toPackage")
            return
        }
        Log.i(TAG, "foreground change after widget tap, closing panel: $fromPackage -> $toPackage")
        (closeAnimated ?: { closeImmediate() }).invoke()
    }

    private val closedSubscriber: (ActionSelectClosedEvent) -> Unit = {
        // 选择页结束：面板回显（编辑态原样继续 —— 组合里的 editMode/localItems 都在）。
        suppressForegroundCloseUntil = SystemClock.elapsedRealtime() + 1_500L
        Log.i(TAG, "select closed, re-show panel=$panelIdState")
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
        // 新一次展示不复用上一轮部件点击留下的拉起候选。
        if (!shownEventPosted) PanelAppWidgetHostView.clearTapCandidate()
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
        // 的 panelCount 会 +1 而永远等不到配对的 Dismissed，球被永久隐藏。
        if (!windowReady || view == null) return
        // 第三方部件点击命中 Activity 型 PendingIntent 时，抬手就让面板开始退场，
        // 不再等 MIUI 延迟数百毫秒的前台窗口事件。
        PanelAppWidgetHostView.setLaunchCloseListener {
            (closeAnimated ?: { closeImmediate() }).invoke()
        }
        lifecycleOwner?.resume()
        if (!shownEventPosted) {
            shownEventPosted = true
            Events.post(PanelShownEvent())
        }
    }

    /** 编辑态让位：窗口隐身但保留（组合与 surface 都在，选择页叠在其上）。
     *  立即不可点，视觉淡出 150ms，播完 lp alpha 收底 —— 不再瞬间硬切。 */
    private fun hide() {
        PanelAppWidgetHostView.clearTapCandidate()
        PanelAppWidgetHostView.setLaunchCloseListener(null)
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.alpha == 0f) return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.safeUpdateViewLayout(current, lp)
        current.animate()
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 能跑到这里 = 淡出完整播完（restore 会 cancel 本动画），lp alpha 安全收底。
                val settled = current.layoutParams as? WindowManager.LayoutParams ?: return@withEndAction
                settled.alpha = 0f
                runCatching { wm.updateViewLayout(current, settled) }
            }
            .start()
        postDismissed()
    }

    /** 组合的 onCloseAnimated 落点：移窗、销毁 owner（组合随之释放）。 */
    override fun closeImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { closeImmediate() }
            return
        }
        PanelAppWidgetHostView.clearTapCandidate()
        PanelAppWidgetHostView.setLaunchCloseListener(null)
        postDismissed()
        removeAndTeardown()
    }

    override fun onBackKeyUp() {
        dismissFromBack()
    }

    override fun onCompositionStarted() {
        Events.subscribe(ActionSelectClosedEvent::class, closedSubscriber)
    }

    override fun teardownExtra() {
        Events.unsubscribe(ActionSelectClosedEvent::class, closedSubscriber)
        backHandler = null
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
        // 让位期间的淡出可能还在飞（同 instance 动画先取消），从 0 淡回到场。
        current.animate().cancel()
        current.alpha = 0f
        current.animate()
            .alpha(1f)
            .setDuration(170L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        current.requestFocus()
    }

    @Composable
    override fun panelContent() {
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
                    panelId = panelIdState,
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

/**
 * 系统级前台包（音量球、灵动胶囊、媒体控制插件等）：它们也会改变前台窗口，
 * 不是小部件拉起应用，不能因此关面板。
 */
internal fun isSystemOverlayPackage(packageName: String): Boolean {
    val pkg = packageName.lowercase()
    return pkg.contains("systemui") ||
        pkg.contains("miui.misound") ||
        pkg.contains("miui.volume") ||
        pkg.contains("miui.mediaeditor") ||
        pkg == "com.android.media"
}
