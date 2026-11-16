package hunoia.luno.runtime.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.core.Events
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.overlay.safeAddView

/**
 * 全屏触摸盾：触发开了 `GlobalGestureTrigger.consumeTouch` 时，在拖动期间吸掉所有新触摸。
 *
 * 为什么需要它：打断源应用只是「取消它手上那一段手势」，用户接下来还会继续滑动、
 * 再落第二根手指。那些是**新**手势，源应用会照常接到。盾把整个屏幕的可触摸区域
 * 盖住，这些后续触摸就只落到 Luno，不会让底下的应用再滚一下或再弹一次长按。
 *
 * 归位（architecture §7.4「触发接管触摸」/ §7.2）：
 * - 这是 transient Surface：挂上就发 `PanelShownEvent`，拆掉发 `PanelDismissedEvent`，
 *   让面板计数与「等面板退场」的其它流程看到它。
 * - 用完必须撤。盾是全屏可触摸窗，漏撤等于整个屏幕点不动——比不接管更糟。
 * - 不常驻：只在一次接管会话里存在，会话收口即拆。
 */
class TouchShieldOverlay(
    private val host: GestureHost,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var shownEventPosted = false

    val isShown: Boolean get() = view != null

    /**
     * 挂盾。重复调用无副作用（已在屏直接返回 true），可安全用于「先点火再补挂」的重入路径。
     *
     * 必须在主线程调用：返回值代表窗口是否真的挂上，异步 post 会让它恒为 false。
     * 调用方在 IO 线程时自己 `withContext(Dispatchers.Main)`。
     *
     * @return 是否在屏（挂载被系统拒绝时为 false，调用方按 best-effort 继续）。
     */
    fun show(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // 用错线程说明调用点写错了；不静默 post（那会让返回值不可信），直接暴露。
            Log.w(LOG_TAG, "touch shield must be attached on the main thread")
            mainHandler.post { show() }
            return false
        }
        if (view != null) return true

        val wm = host.context.windowManager()
        // 盾故意不实现点击：它只负责把触摸「吸收掉」不让它穿透到底下应用。
        // 换来的代价是 lint ClickableViewAccessibility 警告——但盾本来就不可点、
        // 也不该进无障碍焦点（importantForAccessibility 已排除），没有可 performClick 的动作。
        @SuppressLint("ClickableViewAccessibility")
        val shield = View(host.context).apply {
            // 盾是纯遮挡物，没有任何可读内容；别让无障碍把它读成一屏空白。
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            // 消费每一下落在盾上的触摸（含第二根手指），不让它们穿透到下面。
            setOnTouchListener { _, _ -> true }
        }
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        if (!wm.safeAddView(shield, lp)) {
            // 挂不上就直说：调用方不需要为它准备降级路径，接管本身仍是 best-effort。
            Log.w(LOG_TAG, "touch shield rejected by window manager")
            return false
        }
        view = shield
        if (!shownEventPosted) {
            shownEventPosted = true
            Events.post(PanelShownEvent())
        }
        return true
    }

    /** 撤盾。幂等，未在屏时什么都不做（不发多余的 Dismissed）。 */
    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        val current = view ?: return
        view = null
        host.context.removeWindow(current)
        if (shownEventPosted) {
            shownEventPosted = false
            Events.post(PanelDismissedEvent())
        }
    }

    /** 服务销毁时的收口，与 [hide] 同语义，单独留名字是为了调用点能自证「这是终态」。 */
    fun release() = hide()
}
