package hunoia.luno.annotate.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import hunoia.luno.core.LunoLog as Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.ExportKind
import hunoia.luno.annotate.render.exportAnnotated
import hunoia.luno.annotate.store.AnnotateSettingsStore
import hunoia.luno.annotate.store.flushAnnotateSettings
import hunoia.luno.annotate.store.observeAnnotateSettings
import hunoia.luno.annotate.store.restoreAnnotateSettings
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.LOG_TAG
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.SubMinInteractiveSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton

/** 工具条内联面板：浮层窗口是 NOT_FOCUSABLE，独立 Dialog 会抢焦点，只能同窗内联。 */
private enum class OverlayPanel { None, Export, ConfirmExit }

/**
 * 屏幕标注浮层（计划 D1 / D5–D8 / §3.1）。
 *
 * 双窗口：全屏画布 + 独立工具条。
 * - 默认态：穿透（透明、画布 `NOT_TOUCHABLE | NOT_FOCUSABLE`），用户直接操作下层应用，
 *   工具条仍可点；进页不截屏，省一次 a11y/Shizuku 抓图与入口延迟；
 * - 关闭穿透：隐藏两窗 → 重新截屏（由注入的 [captureScreenshot] 负责，含 Shizuku → a11y
 *   回退）→ 换底图并撤掉穿透；笔迹默认保留；
 * - 导出：穿透态先自动关穿透，再按原分辨率重放矢量并按选区裁剪。
 *
 * 宿主只负责「取图」和「软隐藏自家浮层」；这里不碰 SurfaceController，也不依赖 `:app`。
 */
class AnnotateOverlay(
    private val service: AccessibilityService,
    /** 重新截屏入口；调用前本层窗口已隐藏。null = 失败。 */
    private val captureScreenshot: suspend () -> Bitmap?,
    private val onClosed: () -> Unit = {},
    /** 偏好持久化端口；由宿主注入，feature 不直连配置层。 */
    private val settingsStore: AnnotateSettingsStore? = null,
) {

    private companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起），与其它无障碍 overlay 同源。 */
        const val TRUSTED_OVERLAY_FLAG = 0x300000
        const val TOOLBAR_MARGIN_DP = 28
        /** 面板窗口与工具条窗口的间距（与 Chrome 内 6dp 间距一致）。 */
        const val PANEL_GAP_DP = 6
        /** 面板窗口固定高度：进出场只做原地淡入淡出，不随内容 resize / 遮挡工具条。 */
        const val PANEL_MAX_HEIGHT_DP = 160
        /** 面板淡入淡出时长。 */
        const val PANEL_FADE_MS = 160
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var canvasView: ComposeView? = null
    private var toolbarView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var canvasLp: WindowManager.LayoutParams? = null
    private var toolbarLp: WindowManager.LayoutParams? = null
    private var panelLp: WindowManager.LayoutParams? = null

    private var document by mutableStateOf<AnnotateDocument?>(null)
    private var base by mutableStateOf<BaseImage?>(null)
    private var penetrating by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var panel by mutableStateOf(OverlayPanel.None)
    /** 当前展开样式面板的工具；null = 无。与 [panel] 互斥。 */
    private var stylePanelTool by mutableStateOf<ToolKind?>(null)
    /** 裁剪态“恢复整图”请求令牌。 */
    private var cropResetToken by mutableStateOf(0)

    private var toolbarX = 0
    /** 窗口底边屏幕 Y（工具条贴在窗口底部：面板向上弹时工具条不动）。 */
    private var toolbarBottomY = 0
    private var toolbarDragged = false
    /** 首次布局定位完成（之后布局变化不再重置锚点，只按屏幕尺寸变化重算）。 */
    private var toolbarPositioned = false
    private var lastScreenW = 0
    private var lastScreenH = 0

    val isShown: Boolean get() = document != null

    /**
     * 打开浮层；重复调用直接返回。
     *
     * 入参是文档坐标尺寸（进页默认穿透、不截屏，用屏幕尺寸占位），
     * 关闭穿透重截后由 [AnnotateState.replaceImage] 换成真实截屏尺寸。
     */
    suspend fun open(imageWidth: Int, imageHeight: Int): Boolean {
        if (document != null) return true
        if (imageWidth <= 0 || imageHeight <= 0) return false

        val doc = AnnotateDocument(AnnotateState.of(imageWidth, imageHeight))
        // 先恢复偏好再挂窗：工具条首个位置就是持久化位置，避免打开后跳一下。
        restoreAnnotateSettings(doc, settingsStore)
        document = doc
        // 默认穿透：进页不铺底图（铺了黑 gutter 会盖满屏幕），画布也不可触摸。
        base = null
        penetrating = true
        panel = OverlayPanel.None
        stylePanelTool = null

        val wm = service.getSystemService(WindowManager::class.java) ?: return false
        if (!addCanvas(wm)) {
            cleanup()
            return false
        }
        // 首次挂窗就带 NOT_TOUCHABLE | NOT_FOCUSABLE：晚一步 updateViewLayout
        // 会有一帧抢下层触摸。
        applyCanvasFlags()
        if (!addToolbar(wm)) {
            canvasView?.let { runCatching { wm.removeViewImmediate(it) } }
            cleanup()
            return false
        }
        // 面板独立窗口：懒创建（首次显示样式卡/导出/确认时才 addView），
        // 工具条窗口高度恒定，面板开合不影响工具条位置。
        scope.observeAnnotateSettings(doc, settingsStore)
        Log.i(LOG_TAG, "annotate overlay shown (penetrating) ${imageWidth}x${imageHeight}")
        return true
    }

    fun close() {
        document?.commitPendingText()
        hideIme()
        // 防抖窗口内退出不丢偏好：立即补一次落盘（独立于本层 scope）。
        document?.let { flushAnnotateSettings(it, settingsStore) }
        val wm = service.getSystemService(WindowManager::class.java)
        val canvas = canvasView
        val toolbar = toolbarView
        val panelWin = panelView
        canvasView = null
        toolbarView = null
        panelView = null
        canvasLp = null
        toolbarLp = null
        panelLp = null
        if (wm != null) {
            panelWin?.let { runCatching { wm.removeViewImmediate(it) } }
            toolbar?.let { runCatching { wm.removeViewImmediate(it) } }
            canvas?.let { runCatching { wm.removeViewImmediate(it) } }
        }
        document = null
        base = null
        penetrating = false
        panel = OverlayPanel.None
        stylePanelTool = null
        // 不在这里 recycle 已显示过的底图：窗口移除与最后几帧绘制可能仍有引用，
        // 立刻回收会让 RenderThread 用已回收位图（"trying to use a recycled bitmap"），
        // 交给 GC 回收更稳。
        scope.cancel()
        onClosed()
    }

    private fun cleanup() {
        document = null
        base = null
    }

    private fun addCanvas(wm: WindowManager): Boolean {
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
        val view = ComposeView(service).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(service)
            setContent {
                SideGestureTheme {
                    val doc = document
                    val currentBase = base
                    if (doc != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnnotateCanvas(
                                document = doc,
                                base = currentBase,
                                modifier = Modifier.fillMaxSize(),
                                // 编辑文字时收起工具条：无障碍浮层的工具条窗口固定在屏幕底部，
                                // 不随键盘上移，会整个压在键盘上挡住按键；键盘的「完成」会提交。
                                onTextEditingChanged = { editing ->
                                    toolbarView?.visibility = if (editing) View.INVISIBLE else View.VISIBLE
                                },
                                // 落笔即交互开始：收起样式面板/导出/确认卡。
                                onPointerDown = {
                                    panel = OverlayPanel.None
                                    stylePanelTool = null
                                },
                                cropResetToken = cropResetToken,
                            )
                            // 导出/重截有实际耗时：给可见进度（与图片编辑宿主的遮罩+转圈一致）。
                            // 重截抓图前画布窗会短暂 INVISIBLE，进度不会进截图。
                            if (busy) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
        canvasLp = lp
        canvasView = view
        // 画布铺满屏幕，旋转/窗口尺寸变化一定触发它重排；顺手按比例重算工具条位置，
        // 否则横竖屏切换后工具条可能跑出可视区。面板开合/拖动不触发（屏幕尺寸没变）。
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val sw = screenWidthPx()
            val sh = screenHeightPx()
            if (sw != lastScreenW || sh != lastScreenH) {
                toolbarView?.let { positionToolbarInitial(wm, it) }
                lastScreenW = sw
                lastScreenH = sh
            }
        }
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: canvas addView failed ${it.message}") }
            .isSuccess
    }

    private fun addToolbar(wm: WindowManager): Boolean {
        val density = service.resources.displayMetrics.density
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
            // 首次就按底边距定位（BOTTOM 锚定），避免 onLayoutChange 后再跳一帧。
            y = (TOOLBAR_MARGIN_DP * density).toInt()
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val view = ComposeView(service).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(service)
            setContent {
                SideGestureTheme {
                    val doc = document
                    if (doc != null) {
                        AnnotateOverlayChrome(
                            document = doc,
                            penetrating = penetrating,
                            busy = busy,
                            onTogglePenetrate = ::togglePenetrate,
                            onExit = ::requestExit,
                            onExport = ::requestExport,
                            onToolClick = ::onToolClick,
                            onDrag = { delta -> dragToolbar(wm, delta) },
                            onDragEnd = ::dragToolbarEnd,
                            onCropReset = { cropResetToken++ },
                        )
                        // 面板独立窗口：样式卡/导出/确认卡显隐与位置跟随状态。
                        LaunchedEffect(panel, stylePanelTool) { updatePanelVisibility() }
                    }
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                // 只在首次定位，或屏幕尺寸变化（旋转/分辨率）时按比例重算；
                // 面板/样式卡开合与拖动触发的布局变化一律不重置锚点，
                // 底边锚定下窗口高度变化自动向上长，工具条位置稳定。
                val sw = screenWidthPx()
                val sh = screenHeightPx()
                val screenChanged = sw != lastScreenW || sh != lastScreenH
                if (!toolbarPositioned || screenChanged) {
                    positionToolbarInitial(wm, v)
                    toolbarPositioned = true
                    lastScreenW = sw
                    lastScreenH = sh
                } else {
                    // 拖动/其它布局变化：用当前锚点重设底边（不重置 toolbarX/toolbarBottomY），
                    // 保证工具条底边固定在原位；同值 updateViewLayout 幂等不抖动。
                    // 面板已独立窗口，其开合不再触发本窗口 resize。
                    val lpv = toolbarLp
                    if (lpv != null) applyToolbarPosition(wm, v, lpv)
                }
            }
        }
        toolbarLp = lp
        toolbarView = view
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: toolbar addView failed ${it.message}") }
            .isSuccess
    }

    /**
     * 面板独立窗口：样式卡/导出/退出确认显示在工具条上方，工具条窗口高度恒定。
     * 初始 INVISIBLE；显示时机由 [updatePanelVisibility] 控制（不 add/remove，避免窗口动画）。
     */
    private fun addPanel(wm: WindowManager): Boolean {
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
            height = (PANEL_MAX_HEIGHT_DP * density).toInt()
            x = 0
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.BOTTOM or Gravity.LEFT
        }
        val view = ComposeView(service).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(service)
            visibility = View.INVISIBLE
            setContent {
                SideGestureTheme {
                    val doc = document
                    if (doc != null) {
                        // 内容始终渲染（不因关闭清空，避免窗口尺寸变化）；alpha 做原地淡入淡出，
                        // 面板卡底部对齐窗口底边（贴工具条上方），固定高窗口上方留透明。
                        val visible = panel != OverlayPanel.None || stylePanelTool != null
                        val alpha = remember { Animatable(0f) }
                        LaunchedEffect(visible) {
                            if (visible) {
                                alpha.animateTo(1f, tween(PANEL_FADE_MS))
                            } else {
                                alpha.animateTo(0f, tween(PANEL_FADE_MS))
                                hidePanel()
                            }
                        }
                        // 缓存最后非空内容：宿主清空后仍用它渲染，淡出有内容可看。
                        var lastPanel by remember { mutableStateOf(OverlayPanel.None) }
                        var lastStyleTool by remember { mutableStateOf<ToolKind?>(null) }
                        LaunchedEffect(panel, stylePanelTool) {
                            if (panel != OverlayPanel.None) {
                                lastPanel = panel
                                lastStyleTool = null
                            } else if (stylePanelTool != null) {
                                lastStyleTool = stylePanelTool
                                lastPanel = OverlayPanel.None
                            }
                        }
                        val effectivePanel = when {
                            panel != OverlayPanel.None -> panel
                            stylePanelTool != null -> OverlayPanel.None
                            else -> lastPanel
                        }
                        val effectiveStyleTool = when {
                            stylePanelTool != null -> stylePanelTool
                            panel != OverlayPanel.None -> null
                            else -> lastStyleTool
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PANEL_MAX_HEIGHT_DP.dp)
                                .graphicsLayer { this.alpha = alpha.value },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            AnnotatePanelContent(
                                document = doc,
                                panel = effectivePanel,
                                stylePanelTool = effectiveStyleTool,
                                onPanel = { next ->
                                    panel = next
                                    if (next != OverlayPanel.None) stylePanelTool = null
                                },
                                onSave = { startExport(ExportKind.Save) },
                                onCopy = { startExport(ExportKind.Copy) },
                                onShare = { startExport(ExportKind.Share) },
                                onConfirmExit = ::close,
                                onCropReset = { cropResetToken++ },
                            )
                        }
                    }
                }
            }
            // 窗口尺寸固定，布局变化只可能来自定位；保持底边锚定并跟随工具条。
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (visibility == View.VISIBLE) positionPanel(wm)
            }
        }
        panelLp = lp
        panelView = view
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: panel addView failed ${it.message}") }
            .isSuccess
    }

    /** 面板显隐：懒创建窗口；显示时设 VISIBLE 并定位。隐藏由面板内淡出完成后 [hidePanel]。 */
    private fun updatePanelVisibility() {
        val wm = service.getSystemService(WindowManager::class.java) ?: return
        if (panel == OverlayPanel.None && stylePanelTool == null) return
        var view = panelView
        if (view == null) {
            if (!addPanel(wm)) return
            view = panelView ?: return
        }
        if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
        // 布局后 onLayoutChange 会再定位；这里先试一次（若已布局）。
        positionPanel(wm)
    }

    /** 淡出完成后真正隐藏面板窗口（保留尺寸，工具条不受影响）。 */
    private fun hidePanel() {
        val view = panelView ?: return
        if (view.visibility != View.INVISIBLE) view.visibility = View.INVISIBLE
    }

    /**
     * 面板窗口定位：水平居中于工具条，底边 = 工具条顶边 - 间距。
     * 面板内容高度变化由面板窗口自身承载（BOTTOM 锚定向上长），工具条不动。
     */
    private fun positionPanel(wm: WindowManager) {
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

    private fun canvasBaseFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            TRUSTED_OVERLAY_FLAG

    private fun positionToolbarInitial(wm: WindowManager, view: View) {
        val margin = (TOOLBAR_MARGIN_DP * service.resources.displayMetrics.density).toInt()
        val lp = toolbarLp ?: return
        if (view.width <= 0 || view.height <= 0) return
        // 底边锚定：面板/样式卡向上弹时窗口只变高，工具条（窗口底部）不动。
        lp.gravity = Gravity.BOTTOM or Gravity.LEFT
        // 工具条位置只在本次会话内有效，每次打开都从底部居中开始。
        toolbarX = ((screenWidthPx() - view.width) / 2).coerceAtLeast(0)
        toolbarBottomY = screenHeightPx() - margin
        applyToolbarPosition(wm, view, lp)
        // 面板跟随工具条重定位（旋转/首次定位；面板未显示时 positionPanel 提前返回）。
        positionPanel(wm)
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

    private fun dragToolbar(wm: WindowManager, delta: Offset) {
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
        positionPanel(wm)
    }

    /**
     * 拖动结束把位置写回模型（比例锚定：窗口左边 / 屏宽、窗口底边 / 屏高）。
     * 底边锚定 → 面板开合不影响工具条位置，恢复时也不会因面板高度不同而偏。
     */
    private fun dragToolbarEnd() {
        if (!toolbarDragged) return
        // 不持久化工具条位置；样式偏好仍由 observeAnnotateSettings 保存。
    }

    private fun screenWidthPx(): Int = service.resources.displayMetrics.widthPixels

    private fun screenHeightPx(): Int = service.resources.displayMetrics.heightPixels

    /** 穿透开关：只改画布窗口 flags，工具条保持可点。进页默认已在穿透态。 */
    private fun togglePenetrate() {
        if (document == null || busy) return
        // 切换前先交卷：穿透会让画布不可聚焦，编辑器无法再完成输入。
        document?.commitPendingText()
        when {
            !penetrating -> {
                // 需要操作下层应用时先收掉 IME，否则键盘会继续盖住目标窗口。
                hideIme()
                penetrating = true
                base = null
                applyCanvasFlags()
            }

            // 关穿透直接保留笔迹（F1）：不再弹确认框。清空交给工具条「清除全部」，
            // 那条路是可撤销的，误清一步就能回来。
            else -> recapture()
        }
    }

    private fun hideIme() {
        val view = canvasView ?: return
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return
        // 先 hide、不清焦点：先 `clearFocus()` 会先断 InputConnection，平台会瞬断键盘
        // （没有下滑动画）。窗口拆除 / 改为不可聚焦时焦点自然失效。
        runCatching { imm.hideSoftInputFromWindow(view.windowToken, 0) }
    }

    private fun applyCanvasFlags() {
        val wm = service.getSystemService(WindowManager::class.java) ?: return
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

    private fun requestExit() {
        if (busy) return
        document?.commitPendingText()
        stylePanelTool = null
        if (document?.state?.hasElements == true) {
            panel = OverlayPanel.ConfirmExit
        } else {
            close()
        }
    }

    private fun requestExport() {
        if (busy) return
        stylePanelTool = null
        panel = OverlayPanel.Export
    }

    /** 点工具：选中已由 [AnnotateToolbar] 完成，这里管理样式面板开合（与导出/确认卡互斥）。 */
    private fun onToolClick(tool: ToolKind) {
        panel = OverlayPanel.None
        stylePanelTool = if (stylePanelTool == tool) null else tool
    }

    private fun startExport(kind: ExportKind) {
        if (busy || document == null) return
        panel = OverlayPanel.None
        // D7：穿透态导出先自动关穿透，否则底图是空的。
        if (penetrating) {
            recapture() { performExport(kind) }
        } else {
            performExport(kind)
        }
    }

    private fun performExport(kind: ExportKind) {
        val doc = document ?: return
        val currentBase = base ?: return
        // 导出前提交正在输入的文字，避免漏掉未失焦的编辑器内容。
        doc.commitPendingText()
        busy = true
        scope.launch {
            // 渲染 / 落盘 / 提示都在 exportAnnotated 内部，这里只管 busy。
            service.exportAnnotated(doc.state, currentBase, kind)
            busy = false
        }
    }

    /**
     * 关闭穿透后重截（D6）：隐藏两窗 → 等两帧 → 取图 → 换底图。
     * 笔迹始终保留（F1「默认保留」）；要清空走工具条「清除全部」（可撤销）。
     */
    private fun recapture(onDone: () -> Unit = {}) {
        if (busy) return
        document?.commitPendingText()
        busy = true
        // 面板是独立窗口：不清空/隐藏会被截进新底图。
        panel = OverlayPanel.None
        stylePanelTool = null
        hidePanel()
        scope.launch {
            val canvas = canvasView
            val toolbar = toolbarView
            val newBitmap = try {
                canvas?.visibility = View.INVISIBLE
                toolbar?.visibility = View.INVISIBLE
                awaitFrames(2)
                captureScreenshot()
            } finally {
                canvas?.visibility = View.VISIBLE
                toolbar?.visibility = View.VISIBLE
            }
            if (newBitmap == null) {
                // 失败：留在穿透态，用户可以再试一次或直接退出。
                busy = false
                showToast(R.string.annotate_screenshot_failed)
                return@launch
            }
            val doc = document
            if (doc == null) {
                if (!newBitmap.isRecycled) newBitmap.recycle()
                busy = false
                return@launch
            }
            val old = base?.bitmap
            doc.replaceImage(newBitmap.width, newBitmap.height, keepElements = true)
            base = BaseImage(newBitmap)
            // 旧底图可能还被上一帧的 RenderThread 引用；等两帧再回收，避免用已回收位图。
            if (old != null && old !== newBitmap) {
                scope.launch {
                    delay(200)
                    if (!old.isRecycled) old.recycle()
                }
            }
            penetrating = false
            applyCanvasFlags()
            busy = false
            onDone()
        }
    }

    private suspend fun awaitFrames(count: Int) {
        repeat(count) {
            suspendCancellableCoroutine { cont ->
                Choreographer.getInstance().postFrameCallback { cont.resume(Unit) }
            }
        }
        // 再给窗口一次提交事务的机会（部分 ROM 的 SurfaceFlinger 合成滞后）。
        delay(32)
    }
}

@Composable
private fun AnnotateOverlayChrome(
    document: AnnotateDocument,
    penetrating: Boolean,
    busy: Boolean,
    onTogglePenetrate: () -> Unit,
    onExit: () -> Unit,
    onExport: () -> Unit,
    onToolClick: (ToolKind) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onCropReset: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            // 长按工具条空白处拖动：贴边停靠由窗口坐标夹取保证。
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 面板已移到独立窗口（[AnnotatePanelContent]）：工具条窗口高度恒定，
        // 面板开合不再引起工具条窗口 resize / 进场动画。
        AnnotateToolbar(
            document = document,
            onExit = onExit,
            onExport = onExport,
            exportLabel = stringResource(R.string.annotate_export),
            modifier = Modifier.fillMaxWidth(),
            onToolClick = onToolClick,
            onCropReset = onCropReset,
            extraActions = {
                PressIconButton(
                    onClick = onTogglePenetrate,
                    enabled = !busy,
                    modifier = Modifier.size(SubMinInteractiveSize),
                ) {
                    Icon(
                        imageVector = if (penetrating) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = stringResource(
                            if (penetrating) R.string.annotate_penetrate_on
                            else R.string.annotate_penetrate,
                        ),
                        modifier = Modifier.size(CompactControlIconSize),
                    )
                }
            },
        )
    }
}

/**
 * 独立面板窗口内容：样式二层卡 / 导出卡 / 退出确认卡，叠在工具条上方。
 * 窗口 `WRAP_CONTENT`，位置由 [AnnotateOverlay.positionPanel] 控制（跟随工具条）。
 */
@Composable
private fun AnnotatePanelContent(
    document: AnnotateDocument,
    panel: OverlayPanel,
    stylePanelTool: ToolKind?,
    onPanel: (OverlayPanel) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onConfirmExit: () -> Unit,
    onCropReset: () -> Unit = {},
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        // 无投影：弹出卡与工具卡同一视觉层次。
        shadowElevation = 0.dp,
    ) {
        when {
            stylePanelTool != null -> {
                // 点工具弹样式二层卡（B）：按工具展示颜色/大小/橡皮/选区操作。
                StylePanel(
                    tool = stylePanelTool,
                    document = document,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    onCropReset = onCropReset,
                )
            }

            panel == OverlayPanel.Export -> Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ExportAction(
                    icon = Icons.Default.SaveAlt,
                    label = stringResource(R.string.annotate_save),
                    onClick = onSave,
                )
                ExportAction(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.annotate_copy),
                    onClick = onCopy,
                )
                ExportAction(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.annotate_share),
                    onClick = onShare,
                )
                PressTextButton(onClick = { onPanel(OverlayPanel.None) }) {
                    Text(stringResource(R.string.annotate_cancel))
                }
            }

            panel == OverlayPanel.ConfirmExit -> ConfirmPanel(
                title = stringResource(R.string.annotate_discard_title),
                onCancel = { onPanel(OverlayPanel.None) },
            ) {
                PressTextButton(
                    onClick = onConfirmExit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.annotate_discard_confirm))
                }
            }
        }
    }
}
