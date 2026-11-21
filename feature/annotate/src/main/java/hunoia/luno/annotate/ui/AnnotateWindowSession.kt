package hunoia.luno.annotate.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.LOG_TAG

/**
 * 屏幕标注浮层的窗口管线（审计 P1c 从 AnnotateOverlay 提取）。
 *
 * 只管「三个无障碍窗口怎么挂、怎么定位、怎么隐藏」：
 * 画布窗（全屏，穿透态不可触）、工具条窗（底部可拖）、面板窗（工具条上方，懒创建）。
 * 内容组合由宿主以 lambda 注入（读宿主的 Compose 状态）；穿透 / 重截 / 导出等
 * 状态机留在 AnnotateOverlay —— 这里的每一条 flags / 锚定规则都是真机踩坑换来的，
 * 见各方法注释，改动需真机回归。
 */
internal class AnnotateWindowSession(
    private val service: AccessibilityService,
    private val canvasContent: @Composable () -> Unit,
    private val toolbarContent: @Composable () -> Unit,
    private val panelContent: @Composable () -> Unit,
) {

    companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起），与其它无障碍 overlay 同源。 */
        private const val TRUSTED_OVERLAY_FLAG = 0x300000
        private const val TOOLBAR_MARGIN_DP = 28
        /** 面板窗口与工具条窗口的间距（与 Chrome 内 6dp 间距一致）。 */
        private const val PANEL_GAP_DP = 6
        /** 面板窗口固定高度：进出场只做原地淡入淡出，不随内容 resize / 遮挡工具条。 */
        const val PANEL_WINDOW_HEIGHT_DP = 160
    }

    private var canvasView: ComposeView? = null
    private var toolbarView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var canvasLp: WindowManager.LayoutParams? = null
    private var toolbarLp: WindowManager.LayoutParams? = null
    private var panelLp: WindowManager.LayoutParams? = null

    private var toolbarX = 0
    /** 窗口底边屏幕 Y（工具条贴在窗口底部：面板向上弹时工具条不动）。 */
    private var toolbarBottomY = 0
    private var toolbarDragged = false
    /** 首次布局定位完成（之后布局变化不再重置锚点，只按屏幕尺寸变化重算）。 */
    private var toolbarPositioned = false
    private var lastScreenW = 0
    private var lastScreenH = 0

    /** 画布窗 token：宿主收 IME 用（hideSoftInputFromWindow）。 */
    val canvasWindowToken: IBinder? get() = canvasView?.windowToken

    private fun windowManager(): WindowManager? =
        service.getSystemService(WindowManager::class.java)

    // ------------------------------------------------------------------
    // 挂窗 / 拆窗
    // ------------------------------------------------------------------

    /** 全屏画布窗：默认穿透 flags 由 [applyCanvasFlags] 二次确认（见其注释）。 */
    fun addCanvas(): Boolean {
        val wm = windowManager() ?: return false
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = canvasBaseFlags()
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            // 不用 ADJUST_RESIZE：无障碍浮层被键盘顶缩后，冻结底图会重排（落字位置跳），
            // 而且工具条是独立窗口不会跟着上移，反而被键盘盖住。保持画布不动，键盘覆盖底部即可。
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        val view = overlayComposeView {
            canvasContent()
        }
        canvasLp = lp
        canvasView = view
        // 画布铺满屏幕，旋转/窗口尺寸变化一定触发它重排；顺手按比例重算工具条位置，
        // 否则横竖屏切换后工具条可能跑出可视区。面板开合/拖动不触发（屏幕尺寸没变）。
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val sw = screenWidthPx()
            val sh = screenHeightPx()
            if (sw != lastScreenW || sh != lastScreenH) {
                positionToolbarInitial()
                lastScreenW = sw
                lastScreenH = sh
            }
        }
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: canvas addView failed ${it.message}") }
            .isSuccess
    }

    /** 工具条窗：首次挂窗就按底边距定位（BOTTOM 锚定），避免 onLayoutChange 后再跳一帧。 */
    // WrongConstant：[TRUSTED_OVERLAY_FLAG] 是私有常量，lint 的 flag 白名单只认 SDK 常量
    // （AnnotateOverlay 同款用法走的是 baseline；这里按调用点抑制，免受 SDK 更新漂移影响）。
    @SuppressLint("WrongConstant")
    fun addToolbar(): Boolean {
        val wm = windowManager() ?: return false
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                TRUSTED_OVERLAY_FLAG
            // 工具条宽度统一为屏幕约 80%，面板/样式卡开合只变高度，不撑宽窗口。
            width = (screenWidthPx() * 0.8f).toInt().coerceAtLeast(1)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 0
            y = (TOOLBAR_MARGIN_DP * service.resources.displayMetrics.density).toInt()
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val view = overlayComposeView {
            toolbarContent()
        }
        toolbarLp = lp
        toolbarView = view
        toolbarView?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            // 只在首次定位，或屏幕尺寸变化（旋转/分辨率）时按比例重算；
            // 面板/样式卡开合与拖动触发的布局变化一律不重置锚点，
            // 底边锚定下窗口高度变化自动向上长，工具条位置稳定。
            val sw = screenWidthPx()
            val sh = screenHeightPx()
            val screenChanged = sw != lastScreenW || sh != lastScreenH
            if (!toolbarPositioned || screenChanged) {
                positionToolbarInitial()
                toolbarPositioned = true
                lastScreenW = sw
                lastScreenH = sh
            } else {
                // 拖动/其它布局变化：用当前锚点重设底边（不重置 toolbarX/toolbarBottomY），
                // 保证工具条底边固定在原位；同值 updateViewLayout 幂等不抖动。
                // 面板已独立窗口，其开合不再触发本窗口 resize。
                val lpv = toolbarLp
                if (lpv != null && v.width > 0) applyToolbarPosition(wm, v, lpv)
            }
        }
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: toolbar addView failed ${it.message}") }
            .isSuccess
    }

    /**
     * 面板独立窗：样式卡/导出/退出确认显示在工具条上方，工具条窗口高度恒定。
     * 初始 INVISIBLE；显示时机由 [showPanelWindow] 控制（不 add/remove，避免窗口动画）。
     */
    // WrongConstant：同 [addToolbar]，私有 flag 常量不在 lint 白名单里。
    @SuppressLint("WrongConstant")
    private fun addPanel(): Boolean {
        val wm = windowManager() ?: return false
        val density = service.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                TRUSTED_OVERLAY_FLAG
            width = WindowManager.LayoutParams.WRAP_CONTENT
            // 固定高度：进出场只做原地淡入淡出，不随内容 resize / 遮挡工具条。
            height = (PANEL_WINDOW_HEIGHT_DP * density).toInt()
            x = 0
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.BOTTOM or Gravity.LEFT
        }
        val view = overlayComposeView(initialVisibility = View.INVISIBLE) {
            panelContent()
        }
        // 窗口尺寸固定，布局变化只可能来自定位；保持底边锚定并跟随工具条。
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (view.visibility == View.VISIBLE) positionPanel()
        }
        panelLp = lp
        panelView = view
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: panel addView failed ${it.message}") }
            .isSuccess
    }

    /** 面板显隐：懒创建窗口；显示时设 VISIBLE 并定位。隐藏由面板内淡出完成后 [hidePanelWindow]。 */
    fun showPanelWindow() {
        val view = panelView
        if (view == null) {
            if (!addPanel()) return
        }
        val shown = panelView ?: return
        if (shown.visibility != View.VISIBLE) shown.visibility = View.VISIBLE
        // 布局后 onLayoutChange 会再定位；这里先试一次（若已布局）。
        positionPanel()
    }

    /** 淡出完成后真正隐藏面板窗口（保留尺寸，工具条不受影响）。 */
    fun hidePanelWindow() {
        val view = panelView ?: return
        if (view.visibility != View.INVISIBLE) view.visibility = View.INVISIBLE
    }

    private fun overlayComposeView(
        initialVisibility: Int = View.VISIBLE,
        content: @Composable () -> Unit,
    ): ComposeView = ComposeView(service).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        visibility = initialVisibility
        applyOverlayViewTreeOwners(service)
        setContent(content = content)
    }

    /** 拆掉全部窗口（close / 打开失败收场）。面板窗可能从未挂过，逐个容错。 */
    fun removeAll() {
        val wm = windowManager()
        val canvas = canvasView
        val toolbar = toolbarView
        val panel = panelView
        canvasView = null
        toolbarView = null
        panelView = null
        canvasLp = null
        toolbarLp = null
        panelLp = null
        toolbarPositioned = false
        toolbarDragged = false
        if (wm != null) {
            panel?.let { runCatching { wm.removeViewImmediate(it) } }
            toolbar?.let { runCatching { wm.removeViewImmediate(it) } }
            canvas?.let { runCatching { wm.removeViewImmediate(it) } }
        }
    }

    // ------------------------------------------------------------------
    // 画布 flags（穿透开关）
    // ------------------------------------------------------------------

    private fun canvasBaseFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            TRUSTED_OVERLAY_FLAG

    /**
     * 穿透开关：只改画布窗口 flags，工具条保持可点。
     * 首次挂窗（[addCanvas]）只带基础 flags，**必须**在 addView 后立刻调
     * `applyCanvasFlags(penetrating = true)` 补 NOT_TOUCHABLE | NOT_FOCUSABLE——
     * 晚一步 updateViewLayout 会有一帧抢下层触摸。
     */
    fun applyCanvasFlags(penetrating: Boolean) {
        val wm = windowManager() ?: return
        val view = canvasView ?: return
        val lp = canvasLp ?: return
        lp.flags = if (penetrating) {
            canvasBaseFlags() or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            canvasBaseFlags()
        }
        runCatching { wm.updateViewLayout(view, lp) }
    }

    // ------------------------------------------------------------------
    // 定位 / 拖动
    // ------------------------------------------------------------------

    /**
     * 面板窗口定位：水平居中于工具条，底边 = 工具条顶边 - 间距。
     * 面板内容高度变化由面板窗口自身承载（BOTTOM 锚定向上长），工具条不动。
     */
    private fun positionPanel() {
        val wm = windowManager() ?: return
        val view = panelView ?: return
        val lp = panelLp ?: return
        if (view.width <= 0 || view.height <= 0) return
        val toolbar = toolbarView ?: return
        val density = service.resources.displayMetrics.density
        val gap = (PANEL_GAP_DP * density).toInt()
        val left = toolbarX + (toolbar.width - view.width) / 2
        val bottomY = toolbarBottomY - toolbar.height - gap
        lp.gravity = Gravity.BOTTOM or Gravity.LEFT
        lp.x = left.coerceIn(0, (screenWidthPx() - view.width).coerceAtLeast(0))
        lp.y = (screenHeightPx() - bottomY).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun positionToolbarInitial() {
        val wm = windowManager() ?: return
        val view = toolbarView ?: return
        val lp = toolbarLp ?: return
        if (view.width <= 0 || view.height <= 0) return
        // 底边锚定：面板/样式卡向上弹时窗口只变高，工具条（窗口底部）不动。
        lp.gravity = Gravity.BOTTOM or Gravity.LEFT
        // 工具条位置只在本次会话内有效，每次打开都从底部居中开始。
        toolbarX = ((screenWidthPx() - view.width) / 2).coerceAtLeast(0)
        toolbarBottomY = screenHeightPx() - (TOOLBAR_MARGIN_DP * service.resources.displayMetrics.density).toInt()
        applyToolbarPosition(wm, view, lp)
        // 面板跟随工具条重定位（旋转/首次定位；面板未显示时 positionPanel 提前返回）。
        positionPanel()
    }

    /**
     * 统一按「窗口底边」定位：面板加在工具条上方，窗口变高只向上长，工具条不动。
     * gravity 恒 BOTTOM|LEFT：lp.y 是「距屏底」边距，高度变化时系统保持底边，工具条稳定。
     */
    private fun applyToolbarPosition(wm: WindowManager, view: View, lp: WindowManager.LayoutParams) {
        val sw = screenWidthPx()
        val sh = screenHeightPx()
        toolbarX = toolbarX.coerceIn(0, (sw - view.width).coerceAtLeast(0))
        // 底边至少留出整个窗口高（否则窗口顶会到屏外），最多贴屏幕底。
        toolbarBottomY = toolbarBottomY.coerceIn(view.height.coerceAtMost(sh), sh)
        lp.gravity = Gravity.BOTTOM or Gravity.LEFT
        lp.x = toolbarX
        lp.y = (sh - toolbarBottomY).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(view, lp) }
    }

    /** 长按拖动工具条；面板跟随。宿主不持久化工具条位置（会话内有效）。 */
    fun dragToolbar(delta: Offset) {
        val wm = windowManager() ?: return
        val view = toolbarView ?: return
        val lp = toolbarLp ?: return
        if (!toolbarDragged) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            toolbarX = loc[0]
            toolbarBottomY = loc[1] + view.height
            toolbarDragged = true
        }
        toolbarX += delta.x.toInt()
        toolbarBottomY += delta.y.toInt()
        applyToolbarPosition(wm, view, lp)
        // 面板跟随工具条一起拖动。
        positionPanel()
    }

    /** 重截期间两窗暂时 INVISIBLE（截不到自家浮层）；收场必须亮回来。 */
    fun setSurfacesVisible(visible: Boolean) {
        canvasView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        toolbarView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /** 文字编辑中收起工具条（固定在屏幕底部，会压在键盘上挡住按键）。 */
    fun setToolbarVisible(visible: Boolean) {
        toolbarView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun screenWidthPx(): Int = service.resources.displayMetrics.widthPixels

    private fun screenHeightPx(): Int = service.resources.displayMetrics.heightPixels
}
