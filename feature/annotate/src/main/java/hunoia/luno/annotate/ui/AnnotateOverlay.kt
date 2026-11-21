package hunoia.luno.annotate.ui

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Choreographer
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.StickerPickActivity
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.ExportKind
import hunoia.luno.annotate.render.exportAnnotated
import hunoia.luno.annotate.screenshot.AnnotateScreenshot
import hunoia.luno.annotate.store.flushAnnotateSettings
import hunoia.luno.annotate.store.observeAnnotateSettings
import hunoia.luno.annotate.store.restoreAnnotateSettings
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.LunoLoadingIndicator
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.SubMinInteractiveSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 屏幕标注浮层（计划 D1 / D5–D8 / §3.1）。
 *
 * 双窗口：全屏画布 + 独立工具条（窗口管线见 [AnnotateWindowSession]）。
 * - 默认态：穿透（透明、画布 `NOT_TOUCHABLE | NOT_FOCUSABLE`），用户直接操作下层应用，
 *   工具条仍可点；进页不截屏，省一次 a11y 抓图与入口延迟；
 * - 关闭穿透：隐藏两窗 → 重新截屏（由注入的 [captureScreenshot] 负责，纯 a11y）
 *   → 换底图并撤掉穿透；笔迹默认保留；
 * - 导出：穿透态先自动关穿透，再按原分辨率重放矢量并按选区裁剪。
 *
 * 宿主只负责「取图」和「软隐藏自家浮层」；这里不碰 SurfaceController，也不依赖 `:app`。
 * 面板内容（样式卡/导出卡/确认卡）与图片编辑宿主共用 [AnnotatePanelContent]。
 */
class AnnotateOverlay(
    private val service: AccessibilityService,
    /** 重新截屏入口；调用前本层窗口已隐藏。失败带原因，宿主据此选提示。 */
    private val captureScreenshot: suspend () -> AnnotateScreenshot.CaptureResult,
    private val onClosed: () -> Unit = {},
) {

    private companion object {
        /** 面板淡入淡出时长。 */
        const val PANEL_FADE_MS = 160
        /** 重截的总预算（含等帧 + 截图链）。截图链自己有 5s 预算，这里只做最后一道保险：
         * 任何一步挂住都必须能收场，不能把两个窗口永久留在 INVISIBLE。 */
        const val CAPTURE_TIMEOUT_MS = 7_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 后台导出的独立 scope：不随 [close] 取消。
     *
     * 保存/复制/分享点按即关整层（视觉上无等待），导出必须能脱离浮层继续跑完，
     * 否则 `close()` 里的 `scope.cancel()` 会把刚起的导出杀掉、一张图都存不下来。
     */
    private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var document by mutableStateOf<AnnotateDocument?>(null)
    private var base by mutableStateOf<BaseImage?>(null)
    private var penetrating by mutableStateOf(false)
    /** 正在重截：两窗暂时 INVISIBLE，但**退出仍然可用**（取消协程即可收场）。 */
    private var capturing by mutableStateOf(false)
    /** 正在导出：写盘期间不允许退出，避免留下半截文件。 */
    private var exporting by mutableStateOf(false)
    /** 有耗时任务在跑：显示进度遮罩，并禁用穿透/导出（退出只受 [exporting] 限制）。 */
    private val busy: Boolean get() = capturing || exporting
    private var panel by mutableStateOf(AnnotatePanelKind.None)
    /** 当前展开样式面板的工具；null = 无。与 [panel] 互斥。 */
    private var stylePanelTool by mutableStateOf<ToolKind?>(null)
    /** 裁剪态“恢复整图”请求令牌。 */
    private var cropResetToken by mutableStateOf(0)

    private val windows = AnnotateWindowSession(
        service = service,
        canvasContent = { canvasWindowContent() },
        toolbarContent = { toolbarWindowContent() },
        panelContent = { panelWindowContent() },
    )

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
        restoreAnnotateSettings(doc)
        document = doc
        // 默认穿透：进页不铺底图（铺了黑 gutter 会盖满屏幕），画布也不可触摸。
        base = null
        penetrating = true
        panel = AnnotatePanelKind.None
        stylePanelTool = null

        if (!windows.addCanvas()) {
            cleanup()
            return false
        }
        // 首次挂窗就带 NOT_TOUCHABLE | NOT_FOCUSABLE：晚一步 updateViewLayout
        // 会有一帧抢下层触摸。
        windows.applyCanvasFlags(penetrating = true)
        if (!windows.addToolbar()) {
            windows.removeAll()
            cleanup()
            return false
        }
        // 面板独立窗口：懒创建（首次显示样式卡/导出/确认时才 addView），
        // 工具条窗口高度恒定，面板开合不影响工具条位置。
        scope.observeAnnotateSettings(doc)
        Log.i(LOG_TAG, "annotate overlay shown (penetrating) ${imageWidth}x${imageHeight}")
        return true
    }

    /** 关闭浮层并交还宿主。[reason] 只用于诊断（user / lock / service-destroy）。 */
    fun close(reason: String = "user") {
        document?.commitPendingText()
        hideIme()
        // 防抖窗口内退出不丢偏好：立即补一次落盘（独立于本层 scope）。
        document?.let { flushAnnotateSettings(it) }
        windows.removeAll()
        document = null
        base = null
        penetrating = false
        capturing = false
        exporting = false
        panel = AnnotatePanelKind.None
        stylePanelTool = null
        // 不在这里 recycle 已显示过的底图：窗口移除与最后几帧绘制可能仍有引用，
        // 立刻回收会让 RenderThread 用已回收位图（"trying to use a recycled bitmap"），
        // 交给 GC 回收更稳。
        scope.cancel()
        Log.i(LOG_TAG, "annotate close: reason=$reason → onClosed→restore")
        onClosed()
    }

    private fun cleanup() {
        document = null
        base = null
    }

    // ------------------------------------------------------------------
    // 三个窗口的组合内容
    // ------------------------------------------------------------------

    private fun hideIme() {
        val token = windows.canvasWindowToken ?: return
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return
        // 先 hide、不清焦点：先 `clearFocus()` 会先断 InputConnection，平台会瞬断键盘
        // （没有下滑动画）。窗口拆除 / 改为不可聚焦时焦点自然失效。
        runCatching { imm.hideSoftInputFromWindow(token, 0) }
    }

    @Composable
    private fun canvasWindowContent() {
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
                            windows.setToolbarVisible(!editing)
                        },
                        // 落笔即交互开始：收起样式面板/导出/确认卡。
                        onPointerDown = {
                            panel = AnnotatePanelKind.None
                            stylePanelTool = null
                        },
                        // 贴纸工具：落点后弹系统选图（:service 进程内交接，见 StickerPickCenter）。
                        onPickSticker = { StickerPickActivity.start(service) },
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
                            LunoLoadingIndicator()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun toolbarWindowContent() {
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
                    onDrag = { delta -> windows.dragToolbar(delta) },
                    onDragEnd = { /* 位置不持久化；样式偏好由 observeAnnotateSettings 保存 */ },
                    onCropReset = { cropResetToken++ },
                )
                // 面板独立窗口：样式卡/导出/确认卡显隐与位置跟随状态。
                // 面板窗口懒创建（首次显示才 addView），必须有人响应状态变化去 show，
                // 隐藏由面板内淡出动画完成后自己收（panelWindowContent → hidePanelWindow）。
                LaunchedEffect(panel, stylePanelTool) {
                    updatePanelVisibility()
                }
            }
        }
    }

    /** 面板有内容时创建/显示面板窗口（隐藏走淡出完成回调，不走这里）。 */
    private fun updatePanelVisibility() {
        if (panel == AnnotatePanelKind.None && stylePanelTool == null) return
        windows.showPanelWindow()
    }

    @Composable
    private fun panelWindowContent() {
        SideGestureTheme {
            val doc = document
            if (doc != null) {
                // 内容始终渲染（不因关闭清空，避免窗口尺寸变化）；alpha 做原地淡入淡出，
                // 面板卡底部对齐窗口底边（贴工具条上方），固定高窗口上方留透明。
                val visible = panel != AnnotatePanelKind.None || stylePanelTool != null
                val alpha = remember { Animatable(0f) }
                LaunchedEffect(visible) {
                    if (visible) {
                        alpha.animateTo(1f, tween(PANEL_FADE_MS))
                    } else {
                        alpha.animateTo(0f, tween(PANEL_FADE_MS))
                        windows.hidePanelWindow()
                    }
                }
                // 缓存最后非空内容：宿主清空后仍用它渲染，淡出有内容可看。
                var lastPanel by remember { mutableStateOf(AnnotatePanelKind.None) }
                var lastStyleTool by remember { mutableStateOf<ToolKind?>(null) }
                LaunchedEffect(panel, stylePanelTool) {
                    if (panel != AnnotatePanelKind.None) {
                        lastPanel = panel
                        lastStyleTool = null
                    } else if (stylePanelTool != null) {
                        lastStyleTool = stylePanelTool
                        lastPanel = AnnotatePanelKind.None
                    }
                }
                val effectivePanel = when {
                    panel != AnnotatePanelKind.None -> panel
                    stylePanelTool != null -> AnnotatePanelKind.None
                    else -> lastPanel
                }
                val effectiveStyleTool = when {
                    stylePanelTool != null -> stylePanelTool
                    panel != AnnotatePanelKind.None -> null
                    else -> lastStyleTool
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AnnotateWindowSession.PANEL_WINDOW_HEIGHT_DP.dp)
                        .graphicsLayer { this.alpha = alpha.value },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        // 无投影：弹出卡与工具卡同一视觉层次。
                        shadowElevation = 0.dp,
                    ) {
                        AnnotatePanelContent(
                            document = doc,
                            panel = effectivePanel,
                            stylePanelTool = effectiveStyleTool,
                            onPanel = { next ->
                                panel = next
                                if (next != AnnotatePanelKind.None) stylePanelTool = null
                            },
                            onExport = { kind -> startExport(kind) },
                            onConfirmExit = ::close,
                            onCropReset = { cropResetToken++ },
                            // 形状子类型选完自动收卡（P3 手感）。
                            onShapeSelected = { stylePanelTool = null },
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 状态机：穿透 / 重截 / 导出 / 面板开合
    // ------------------------------------------------------------------

    /** 穿透开关：只改画布窗口 flags，工具条保持可点。进页默认已在穿透态。 */
    private fun togglePenetrate() {
        if (document == null) return
        if (busy) {
            Log.i(LOG_TAG, "annotate penetrate ignored: busy(capturing=$capturing exporting=$exporting)")
            return
        }
        // 切换前先交卷：穿透会让画布不可聚焦，编辑器无法再完成输入。
        document?.commitPendingText()
        when {
            !penetrating -> {
                // 需要操作下层应用时先收掉 IME，否则键盘会继续盖住目标窗口。
                hideIme()
                penetrating = true
                base = null
                windows.applyCanvasFlags(penetrating = true)
                Log.i(LOG_TAG, "annotate penetrate on")
            }

            // 关穿透直接保留笔迹（F1）：不再弹确认框。清空交给工具条「清除全部」，
            // 那条路是可撤销的，误清一步就能回来。
            else -> {
                Log.i(LOG_TAG, "annotate penetrate off → recapture")
                recapture()
            }
        }
    }

    private fun applyCanvasFlags() {
        windows.applyCanvasFlags(penetrating = penetrating)
    }

    private fun requestExit() {
        // 重截期间允许退出：截图协程会被取消，finally 负责把两窗亮回来。
        if (exporting) {
            Log.i(LOG_TAG, "annotate exit ignored: exporting")
            return
        }
        document?.commitPendingText()
        stylePanelTool = null
        if (document?.state?.hasElements == true) {
            panel = AnnotatePanelKind.ConfirmExit
        } else {
            close("exit")
        }
    }

    private fun requestExport() {
        if (busy) {
            Log.i(LOG_TAG, "annotate export ignored: busy(capturing=$capturing exporting=$exporting)")
            return
        }
        stylePanelTool = null
        panel = AnnotatePanelKind.Export
    }

    /** 点工具：选中已由 [AnnotateToolbar] 完成，这里管理样式面板开合（与导出/确认卡互斥）。 */
    private fun onToolClick(tool: ToolKind) {
        panel = AnnotatePanelKind.None
        stylePanelTool = if (stylePanelTool == tool) null else tool
    }

    private fun startExport(kind: ExportKind) {
        if (busy || document == null) return
        val doc = document ?: return
        panel = AnnotatePanelKind.None
        // 点击即关：导出动作不等淡出，直接收掉面板窗口，避免保存/复制后面板仍短暂可见。
        windows.hidePanelWindow()
        // D7：穿透态导出先自动关穿透，否则底图是空的（recapture 很快，~75ms）。
        if (penetrating) {
            recapture() {
                performExport(doc, kind)
                // 取到底图后立即关整层：导出在后台跑，不等保存完成。
                close("after-export")
            }
        } else {
            // 常规（非穿透）：点按即关整层，导出在 exportScope 后台完成，不转圈等保存。
            performExport(doc, kind)
            close("after-export")
        }
    }

    /**
     * 后台导出：挂 [exportScope]（不随 [close] 取消），捕获 [doc] 与当前底图后立即返回，
     * 浮层随之关闭；渲染/落盘/toast 都在 [exportAnnotated] 内完成。
     */
    private fun performExport(doc: AnnotateDocument, kind: ExportKind) {
        val currentBase = base ?: return
        // 导出前提交正在输入的文字，避免漏掉未失焦的编辑器内容。
        doc.commitPendingText()
        exportScope.launch {
            service.exportAnnotated(doc.state, currentBase, kind)
        }
    }

    /**
     * 关闭穿透后重截（D6）：隐藏两窗 → 等两帧 → 取图 → 换底图。
     * 笔迹始终保留（F1「默认保留」）；要清空走工具条「清除全部」（可撤销）。
     *
     * **必须能失败**：截图链任何一步挂住（系统丢 a11y 回调 / 超时），
     * 都要在 [CAPTURE_TIMEOUT_MS] 内收场并把两窗亮回来 —— 否则画布和工具条永久
     * INVISIBLE、`capturing` 永真，整个标注层既看不见也点不动，只能重绑无障碍服务。
     */
    private fun recapture(onDone: () -> Unit = {}) {
        if (busy || document == null) return
        document?.commitPendingText()
        capturing = true
        // 面板是独立窗口：不清空/隐藏会被截进新底图。
        panel = AnnotatePanelKind.None
        stylePanelTool = null
        windows.hidePanelWindow()
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(LOG_TAG, "annotate recapture: start (penetrating=$penetrating)")
        scope.launch {
            var outcome = "timeout (${CAPTURE_TIMEOUT_MS}ms)"
            var captureResult: AnnotateScreenshot.CaptureResult? = null
            var shot: Bitmap? = null
            try {
                windows.setSurfacesVisible(visible = false)
                captureResult = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                    awaitFrames(2)
                    captureScreenshot().also { outcome = describeCapture(it) }
                }
                if (captureResult is AnnotateScreenshot.CaptureResult.Success) {
                    shot = captureResult.bitmap
                }
            } catch (cancel: CancellationException) {
                // 用户按退出 / 服务销毁：窗口正在被拆除，不代表失败。
                outcome = "cancelled"
                throw cancel
            } catch (t: Throwable) {
                outcome = "threw ${t.javaClass.simpleName}: ${t.message}"
                Log.e(LOG_TAG, "annotate recapture failed: $outcome")
            } finally {
                // 唯一出口：成功、失败、超时、异常、取消都必须走到这里。
                windows.setSurfacesVisible(visible = true)
                capturing = false
                Log.i(
                    LOG_TAG,
                    "annotate recapture: done ($outcome, ${SystemClock.elapsedRealtime() - startedAt}ms," +
                        " penetrating=$penetrating, shot=${shot?.width}x${shot?.height})",
                )
            }
            if (shot == null) {
                // 失败：留在穿透态，用户可以再试一次或直接退出（退出不再被 capturing 挡住）。
                // 外层超时（captureResult == null）也按超时提示。
                val reason = (captureResult as? AnnotateScreenshot.CaptureResult.Failed)?.reason
                    ?: AnnotateScreenshot.CaptureResult.Reason.Timeout
                showToast(
                    when (reason) {
                        AnnotateScreenshot.CaptureResult.Reason.SecureWindow ->
                            R.string.annotate_screenshot_secure

                        AnnotateScreenshot.CaptureResult.Reason.Timeout ->
                            R.string.annotate_screenshot_timeout

                        AnnotateScreenshot.CaptureResult.Reason.Unavailable ->
                            R.string.annotate_screenshot_failed
                    },
                )
                return@launch
            }
            val doc = document
            if (doc == null) {
                if (!shot.isRecycled) shot.recycle()
                return@launch
            }
            val newBitmap = shot
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
            onDone()
        }
    }

    /** recapture 日志用的结果描述。 */
    private fun describeCapture(result: AnnotateScreenshot.CaptureResult): String = when (result) {
        is AnnotateScreenshot.CaptureResult.Success -> "ok"
        is AnnotateScreenshot.CaptureResult.Failed -> when (result.reason) {
            AnnotateScreenshot.CaptureResult.Reason.SecureWindow -> "secure window"
            AnnotateScreenshot.CaptureResult.Reason.Timeout -> "timeout"
            AnnotateScreenshot.CaptureResult.Reason.Unavailable -> "capture returned null"
        }
    }

    private suspend fun awaitFrames(count: Int) {
        repeat(count) {
            suspendCancellableCoroutine { cont ->
                Choreographer.getInstance().postFrameCallback { cont.resume(Unit) }
            }
        }
        // 再给窗口一次提交事务的机会（部分 ROM 的 SurfaceFlinger 合成滞后）。
        delay(16)
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
            // 长按工具条空白处拖动：位置按窗口坐标夹取不出屏，不持久化。
            // 卡片贴屏边距由窗口宽（屏宽 − 2 × 16dp）提供，这里不再加横向 padding。
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
