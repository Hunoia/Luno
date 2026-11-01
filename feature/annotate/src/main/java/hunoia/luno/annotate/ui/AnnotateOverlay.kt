package hunoia.luno.annotate.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.render.AnnotateExporter
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.bridge.system.ImageOutput
import hunoia.luno.bridge.system.copyImage
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
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** 工具条内联面板：浮层窗口是 NOT_FOCUSABLE，独立 Dialog 会抢焦点，只能同窗内联。 */
private enum class OverlayPanel { None, Export, ConfirmExit, ConfirmRescreenshot }

private enum class ExportKind { Save, Copy, Share }

/**
 * 屏幕标注浮层（计划 D1 / D5–D8 / §3.1）。
 *
 * 双窗口：全屏画布 + 独立工具条。
 * - 默认态：画布画冻结截图，触摸被画布拦截；
 * - 穿透态：撤掉底图（透明）、画布 `NOT_TOUCHABLE | NOT_FOCUSABLE`，工具条仍可点；
 * - 关闭穿透：隐藏两窗 → 重新截屏（由注入的 [captureScreenshot] 负责，含 Shizuku → a11y
 *   回退）→ 换底图；有笔迹时先内联确认；
 * - 导出：不截屏，按原分辨率重放矢量再按选区裁剪。
 *
 * 宿主只负责「取图」和「软隐藏自家浮层」；这里不碰 SurfaceController，也不依赖 `:app`。
 */
class AnnotateOverlay(
    private val service: AccessibilityService,
    /** 重新截屏入口；调用前本层窗口已隐藏。null = 失败。 */
    private val captureScreenshot: suspend () -> Bitmap?,
    private val onClosed: () -> Unit = {},
) {

    private companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起），与 PointPickOverlay 同源。 */
        const val TRUSTED_OVERLAY_FLAG = 0x300000
        const val SURFACE_ID = "annotate.overlay"
        const val TOOLBAR_MARGIN_DP = 28
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var canvasView: ComposeView? = null
    private var toolbarView: ComposeView? = null
    private var canvasLp: WindowManager.LayoutParams? = null
    private var toolbarLp: WindowManager.LayoutParams? = null

    private var document by mutableStateOf<AnnotateDocument?>(null)
    private var base by mutableStateOf<BaseImage?>(null)
    private var penetrating by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var panel by mutableStateOf(OverlayPanel.None)

    private var toolbarX = 0
    private var toolbarY = 0
    private var toolbarDragged = false

    val isShown: Boolean get() = document != null

    /** 打开浮层；重复调用只保留第一张底图（避免连点动作覆盖正在编辑的内容）。 */
    fun open(bitmap: Bitmap): Boolean {
        if (document != null) return true
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false

        val doc = AnnotateDocument(AnnotateState.of(bitmap.width, bitmap.height))
        document = doc
        base = BaseImage(bitmap)
        penetrating = false
        panel = OverlayPanel.None

        val wm = service.getSystemService(WindowManager::class.java) ?: return false
        if (!addCanvas(wm)) {
            cleanup(bitmap)
            return false
        }
        if (!addToolbar(wm)) {
            canvasView?.let { runCatching { wm.removeViewImmediate(it) } }
            cleanup(bitmap)
            return false
        }
        Log.i(LOG_TAG, "annotate overlay shown image=${bitmap.width}x${bitmap.height}")
        return true
    }

    fun close() {
        document?.commitPendingText()
        hideIme()
        val wm = service.getSystemService(WindowManager::class.java)
        val canvas = canvasView
        val toolbar = toolbarView
        canvasView = null
        toolbarView = null
        canvasLp = null
        toolbarLp = null
        if (wm != null) {
            toolbar?.let { runCatching { wm.removeViewImmediate(it) } }
            canvas?.let { runCatching { wm.removeViewImmediate(it) } }
        }
        document = null
        base = null
        penetrating = false
        panel = OverlayPanel.None
        // 不在这里 recycle 已显示过的底图：窗口移除与最后几帧绘制可能仍有引用，
        // 立刻回收会让 RenderThread 用已回收位图（"trying to use a recycled bitmap"），
        // 交给 GC 回收更稳。
        scope.cancel()
        onClosed()
    }

    private fun cleanup(bitmap: Bitmap) {
        document = null
        base = null
        if (!bitmap.isRecycled) bitmap.recycle()
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
                        AnnotateCanvas(
                            document = doc,
                            base = currentBase,
                            modifier = Modifier.fillMaxSize(),
                            // 编辑文字时收起工具条：无障碍浮层的工具条窗口固定在屏幕底部，
                            // 不随键盘上移，会整个压在键盘上挡住按键；键盘的「完成」会提交。
                            onTextEditingChanged = { editing ->
                                toolbarView?.visibility = if (editing) View.INVISIBLE else View.VISIBLE
                            },
                        )
                    }
                }
            }
        }
        canvasLp = lp
        canvasView = view
        // 画布铺满屏幕，旋转/窗口尺寸变化一定触发它重排；顺手把工具条重新夹回屏内，
        // 否则横竖屏切换后工具条可能跑出可视区。
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            toolbarView?.let { toolbar ->
                if (!toolbarDragged) positionToolbarInitial(wm, toolbar) else clampToolbar(wm, toolbar)
            }
        }
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: canvas addView failed ${it.message}") }
            .isSuccess
    }

    private fun addToolbar(wm: WindowManager): Boolean {
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                TRUSTED_OVERLAY_FLAG
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 0
            y = 0
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
                            panel = panel,
                            onTogglePenetrate = ::togglePenetrate,
                            onExit = ::requestExit,
                            onExport = ::requestExport,
                            onPanel = { panel = it },
                            onSave = { startExport(ExportKind.Save) },
                            onCopy = { startExport(ExportKind.Copy) },
                            onShare = { startExport(ExportKind.Share) },
                            onConfirmExit = ::close,
                            onRescreenshotKeep = { recapture(clearElements = false) },
                            onRescreenshotClear = { recapture(clearElements = true) },
                            onDrag = { delta -> dragToolbar(wm, delta) },
                        )
                    }
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (!toolbarDragged) positionToolbarInitial(wm, v) else clampToolbar(wm, v)
            }
        }
        toolbarLp = lp
        toolbarView = view
        return runCatching { wm.addView(view, lp) }
            .onFailure { Log.e(LOG_TAG, "annotate: toolbar addView failed ${it.message}") }
            .isSuccess
    }

    private fun canvasBaseFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            TRUSTED_OVERLAY_FLAG

    private fun positionToolbarInitial(wm: WindowManager, view: View) {
        val margin = (TOOLBAR_MARGIN_DP * service.resources.displayMetrics.density).toInt()
        val lp = toolbarLp ?: return
        if (view.width <= 0 || view.height <= 0) return
        lp.gravity = Gravity.TOP or Gravity.LEFT
        toolbarX = ((screenWidthPx() - view.width) / 2).coerceAtLeast(0)
        toolbarY = (screenHeightPx() - view.height - margin).coerceAtLeast(0)
        lp.x = toolbarX
        lp.y = toolbarY
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun dragToolbar(wm: WindowManager, delta: Offset) {
        val view = toolbarView ?: return
        val lp = toolbarLp ?: return
        if (!toolbarDragged) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            toolbarX = loc[0]
            toolbarY = loc[1]
            toolbarDragged = true
            lp.gravity = Gravity.TOP or Gravity.LEFT
        }
        toolbarX = (toolbarX + delta.x.toInt()).coerceIn(0, (screenWidthPx() - view.width).coerceAtLeast(0))
        toolbarY = (toolbarY + delta.y.toInt()).coerceIn(0, (screenHeightPx() - view.height).coerceAtLeast(0))
        lp.x = toolbarX
        lp.y = toolbarY
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun clampToolbar(wm: WindowManager, view: View) {
        val lp = toolbarLp ?: return
        if (view.width <= 0 || view.height <= 0) return
        lp.gravity = Gravity.TOP or Gravity.LEFT
        toolbarX = toolbarX.coerceIn(0, (screenWidthPx() - view.width).coerceAtLeast(0))
        toolbarY = toolbarY.coerceIn(0, (screenHeightPx() - view.height).coerceAtLeast(0))
        lp.x = toolbarX
        lp.y = toolbarY
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun screenWidthPx(): Int = service.resources.displayMetrics.widthPixels

    private fun screenHeightPx(): Int = service.resources.displayMetrics.heightPixels

    /** 穿透开关（D8）：只改画布窗口 flags，工具条保持可点。 */
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

            document?.state?.hasElements == true -> panel = OverlayPanel.ConfirmRescreenshot
            else -> recapture(clearElements = false)
        }
    }

    private fun hideIme() {
        val view = canvasView ?: return
        view.clearFocus()
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return
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
        if (document?.state?.hasElements == true) {
            panel = OverlayPanel.ConfirmExit
        } else {
            close()
        }
    }

    private fun requestExport() {
        if (busy) return
        panel = OverlayPanel.Export
    }

    private fun startExport(kind: ExportKind) {
        if (busy || document == null) return
        panel = OverlayPanel.None
        // D7：穿透态导出先自动关穿透，否则底图是空的。
        if (penetrating) {
            recapture(clearElements = false) { performExport(kind) }
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
            val rendered = runCatching {
                withContext(Dispatchers.Default) {
                    AnnotateExporter.renderCropped(doc.state, currentBase)
                }
            }.getOrNull()
            if (rendered == null) {
                busy = false
                showToast(R.string.annotate_export_failed)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                try {
                    when (kind) {
                        ExportKind.Save ->
                            ImageOutput.saveToGallery(service, rendered, "annotate") != null
                        // 复制需要一个可被粘贴方读取的 URI：先落图库再写剪贴板。
                        ExportKind.Copy ->
                            ImageOutput.saveToGallery(service, rendered, "annotate")
                                ?.let { uri -> copyImage(service, "annotate", uri) } ?: false
                        ExportKind.Share ->
                            ImageOutput.saveToCache(service, rendered, "annotate")
                                ?.let { uri -> ImageOutput.share(service, uri, "annotate") } ?: false
                    }
                } finally {
                    if (!rendered.isRecycled) rendered.recycle()
                }
            }
            busy = false
            when {
                !ok -> showToast(R.string.annotate_export_failed)
                kind == ExportKind.Save -> showToast(R.string.annotate_saved)
                kind == ExportKind.Copy -> showToast(R.string.annotate_copied)
            }
        }
    }

    /**
     * 关闭穿透后重截（D6）：隐藏两窗 → 等两帧 → 取图 → 换底图。
     * [clearElements] 为 true 时是破坏性清空（不进撤销栈）。
     */
    private fun recapture(clearElements: Boolean, onDone: () -> Unit = {}) {
        if (busy) return
        document?.commitPendingText()
        busy = true
        panel = OverlayPanel.None
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
            if (clearElements) {
                doc.clearElements()
                doc.replaceImage(newBitmap.width, newBitmap.height, keepElements = false)
            } else {
                doc.replaceImage(newBitmap.width, newBitmap.height, keepElements = true)
            }
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
    panel: OverlayPanel,
    onTogglePenetrate: () -> Unit,
    onExit: () -> Unit,
    onExport: () -> Unit,
    onPanel: (OverlayPanel) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onConfirmExit: () -> Unit,
    onRescreenshotKeep: () -> Unit,
    onRescreenshotClear: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            // 长按工具条空白处拖动：贴边停靠由窗口坐标夹取保证。
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnnotateToolbar(
            document = document,
            onExit = onExit,
            onExport = onExport,
            exportLabel = stringResource(R.string.annotate_export),
            modifier = Modifier.widthIn(max = 560.dp),
            extraActions = {
                IconButton(
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

        if (panel != OverlayPanel.None) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (panel) {
                        OverlayPanel.Export -> {
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
                            TextButton(onClick = { onPanel(OverlayPanel.None) }) {
                                Text(stringResource(R.string.annotate_cancel))
                            }
                        }

                        OverlayPanel.ConfirmExit -> {
                            ConfirmPanel(
                                title = stringResource(R.string.annotate_discard_title),
                                onCancel = { onPanel(OverlayPanel.None) },
                            ) {
                                TextButton(
                                    onClick = onConfirmExit,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.annotate_discard_confirm))
                                }
                            }
                        }

                        OverlayPanel.ConfirmRescreenshot -> {
                            ConfirmPanel(
                                title = stringResource(R.string.annotate_rescreenshot_title),
                                onCancel = { onPanel(OverlayPanel.None) },
                            ) {
                                TextButton(
                                    onClick = onRescreenshotKeep,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.annotate_rescreenshot_keep))
                                }
                                TextButton(
                                    onClick = onRescreenshotClear,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.annotate_rescreenshot_clear))
                                }
                            }
                        }

                        OverlayPanel.None -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmPanel(
    title: String,
    onCancel: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // 纵向堆叠：浮层工具条窗口是 WRAP_CONTENT，横向排三个 TextButton 在窄屏上会把
    // 最后一个按钮挤成两行。每行占满面板宽度，点击区也更好按。
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        actions()
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.annotate_cancel))
        }
    }
}

@Composable
private fun ExportAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
    }
}
