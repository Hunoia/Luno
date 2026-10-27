package hunoia.luno.runtime.overlay

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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.core.AppContext
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent

/**
 * 全屏面板悬浮窗宿主的公共骨架（TYPE_APPLICATION_OVERLAY）：可获焦 FrameLayout 容器
 * 接 BACK + 内嵌常驻组合的 ComposeView + [PanelLifecycleOwner] 挂靠。
 * 窗口全屏只当画布；组合常驻复用：让位只置 alpha 0 不可触摸，关闭才移除。
 * owner 必须推到 RESUMED：collectAsStateWithLifecycle 与 LaunchedEffect 都要 STARTED 以上。
 *
 * 子类通过 [panelContent] 提供组合内容，show/close 的编排差异与附加状态
 * （事件订阅、会话引用）留在子类，经 [teardownExtra] 等钩子接入。
 */
abstract class OverlayPanelHost(
    private val logTag: String,
    private val logPrefix: String,
    /** 锁屏上是否可见（仅启动面板开；球窗没有这个 flag，属首次尝试，需真机验证）。 */
    private val showWhenLocked: Boolean,
) {
    protected val context: Context get() = AppContext.get()
    protected val wm: WindowManager get() = context.windowManager()
    protected val mainHandler = Handler(Looper.getMainLooper())

    protected var view: View? = null
    protected var attached = false
    protected var lifecycleOwner: PanelLifecycleOwner? = null

    /** 面板可见时为 true：保证 Shown/Dismissed 事件配对（重复 show 不重复发）。 */
    protected var shownEventPosted = false

    /** 组合注册的关闭动画执行体。组合常驻期间一直有效，**不得**在 show 里清空
     *  —— LaunchedEffect(Unit) 只注册一次，清了就再没人注册回来。 */
    protected var closeAnimated: (() -> Unit)? = null

    /** 退场动画播完 / 直接关闭 / BACK 的最终落点：移窗 + 销毁 owner + 清子类状态。 */
    protected abstract fun closeImmediate()

    /** BACK 键（容器获焦时 ACTION_UP）：子类决定先让组合消费还是直接播退场动画。 */
    protected abstract fun onBackKeyUp()

    /** 组合内容。实现里要显式限定宿主对象调用内部 Composable，避免解析成
     *  ComposeView.Content()（透传自身）无限递归，真机表现为打开即 StackOverflow。 */
    @Composable
    protected abstract fun panelContent()

    /** addView 失败（权限被撤 / ROM 拒绝）的附加处理（如平台 Toast 引导授权）。 */
    protected open fun onAddViewFailed() {}

    /** resume 之后、origin 修正之前的挂载收尾（如订阅外部事件）。 */
    protected open fun onCompositionStarted() {}

    /** 回滚 / 关闭时清子类附加状态（事件退订、会话引用），在移窗与销毁 owner 之间调用。 */
    protected open fun teardownExtra() {}

    protected fun postDismissed() {
        if (!shownEventPosted) return
        shownEventPosted = false
        Events.post(PanelDismissedEvent())
    }

    /**
     * 挂载：owner + 可获焦 BACK 容器 + 常驻组合的 ComposeView，addView 后 resume。
     * addView 之后这段是裸的：owner.resume() 后组合的 LaunchedEffect / 收流才真正
     * 开跑，期间抛出会直接杀 :service 进程，故整段收口 + 回滚。
     */
    protected fun attach(): Boolean {
        val owner = PanelLifecycleOwner().also { it.create() }
        // ComposeView 是 final，不能子类化；用可获焦容器接 BACK，内嵌 ComposeView。
        val created = object : FrameLayout(context) {
            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onBackKeyUp()
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
            setContent { panelContent() }
        }
        created.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        if (!wm.safeAddView(created, layoutParamsFor())) {
            Log.e(logTag, "$logPrefix addView failed (overlay permission or window rejected)")
            onAddViewFailed()
            owner.destroy()
            return false
        }
        try {
            view = created
            lifecycleOwner = owner
            created.requestFocus()
            // 面包屑：后面几行的异常来自组合协程，try 抓不到；看到这行紧接着崩溃
            // 就说明是 resume 之后的异步段（LaunchedEffect / DataStore 收流）。
            Log.i(logTag, "$logPrefix attached, resuming owner (composition coroutines start here)")
            owner.resume()
            onCompositionStarted()
            correctWindowOrigin(created)
        } catch (t: Throwable) {
            // 故意兜 Throwable（含 Error）：此处唯一的目标是不让挂载失败变成进程崩溃。
            Log.e(logTag, "$logPrefix attach failed after addView, rolling back", t)
            rollbackAttach(created, owner)
            return false
        }
        return true
    }

    /**
     * 2038 悬浮窗的 LP y 原点实测在状态栏下沿。本窗口用 LAYOUT_IN_SCREEN 期望按
     * 屏幕坐标摆，但 ROM 行为不一：attach 后实测窗口顶边，非 0 就反向修正 lp.y。
     */
    protected fun correctWindowOrigin(created: View) {
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

    /** 清宿主引用并销毁 owner（组合随之释放）；先 [teardownExtra] 再销毁。不移窗。 */
    protected fun teardownRefs() {
        teardownExtra()
        view = null
        attached = false
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        closeAnimated = null
    }

    /**
     * 关闭落点：先移窗再清引用。必须移窗：全屏可触摸窗留在屏幕上会挡住底下所有
     * 触摸，且 view 置 null 后就再也够不着它。view 为 null 时不做事（保持 close 的
     * 幂等语义：引用与 owner 原样保留）。
     */
    protected fun removeAndTeardown() {
        val current = view ?: return
        context.removeWindow(current)
        teardownRefs()
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            attached = true
        }

        override fun onViewDetachedFromWindow(v: View) {
            attached = false
        }
    }

    private fun rollbackAttach(created: View, owner: PanelLifecycleOwner) {
        removeAndTeardown(created, owner)
    }

    /**
     * 回滚「已 addView 但没挂完」的浮窗。销毁顺序与 closeImmediate 一致（先移窗再销毁
     * owner），但即使 view 引用尚未赋值也要清干净传入的 created。
     */
    private fun removeAndTeardown(created: View, owner: PanelLifecycleOwner) {
        teardownExtra()
        context.removeWindow(created)
        if (view === created) view = null
        if (lifecycleOwner === owner) lifecycleOwner = null
        attached = false
        closeAnimated = null
        owner.destroy()
    }

    protected fun layoutParamsFor() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (showWhenLocked) WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED else 0
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        gravity = Gravity.START or Gravity.TOP
        x = 0
        y = 0
    }
}
