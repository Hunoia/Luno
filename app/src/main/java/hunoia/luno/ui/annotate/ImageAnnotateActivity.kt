package hunoia.luno.ui.annotate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.lifecycleScope
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.R as AnnotateR
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.ExportKind
import hunoia.luno.annotate.render.exportAnnotated
import hunoia.luno.annotate.store.flushAnnotateSettings
import hunoia.luno.annotate.store.observeAnnotateSettings
import hunoia.luno.annotate.store.restoreAnnotateSettings
import hunoia.luno.annotate.ui.AnnotateCanvas
import hunoia.luno.annotate.ui.AnnotateToolbar
import hunoia.luno.annotate.ui.ConfirmPanel
import hunoia.luno.annotate.ui.ExportAction
import hunoia.luno.annotate.ui.StylePanel
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.domain.annotate.AnnotateSettingsCenter
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import hunoia.luno.ui.component.PressTextButton

/**
 * 图片编辑页（计划 §3.2）：透明 `:service` Activity，顶部 contain 图片、底部固定工具条。
 *
 * 两种进入方式：
 * - `image.edit` 动作 → 先弹系统图片选择器；
 * - 识屏框选 / OCR 结果页 → 进程内位图交接（[ImageAnnotateHandoff]），跳过选择器。
 *
 * 与屏幕标注共用 `:feature:annotate` 的模型 / 渲染 / 导出，只有 chrome 不同。
 */
class ImageAnnotateActivity : ComponentActivity() {

    companion object {
        internal const val EXTRA_SESSION = "hunoia.luno.annotate.extra.SESSION"

        /** 从 `:service` 拉起图片编辑页（picker 模式），返回是否启动成功。 */
        fun startPicker(context: Context): Boolean = start(context, FEATURE_PICKER)

        /** 带着已有位图进入（payload 模式）。位图所有权转移给本页。 */
        fun startWithBitmap(context: Context, bitmap: Bitmap): Boolean {
            if (bitmap.isRecycled) return false
            val id = ImageAnnotateHandoff.put(bitmap)
            val ok = start(context, FEATURE_PAYLOAD, id)
            if (!ok) ImageAnnotateHandoff.discard(id)
            return ok
        }

        private const val FEATURE_PICKER = "picker"
        private const val FEATURE_PAYLOAD = "payload"
        private const val MAX_DECODE_PX = 4096
        /** 原图导出上限（P1）：超过则降采样，避免超大照片导出 OOM。 */
        private const val MAX_FULL_DECODE_PX = 8192

        private fun start(context: Context, feature: String, id: Long = -1L): Boolean {
            val intent = Intent(context, ImageAnnotateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_SESSION, id)
                putExtra(EXTRA_FEATURE, feature)
            }
            return runCatching { context.startActivity(intent) }.isSuccess
        }

        private const val EXTRA_FEATURE = "hunoia.luno.annotate.extra.FEATURE"
    }

    private var document by mutableStateOf<AnnotateDocument?>(null)
    private var base by mutableStateOf<BaseImage?>(null)
    private var dialog by mutableStateOf<Dialog?>(null)
    /** 当前展开样式面板的工具；null = 无。与 [dialog] 互斥。 */
    private var stylePanelTool by mutableStateOf<ToolKind?>(null)
    private var busy by mutableStateOf(false)
    private var pickerLaunched = false
    /** 偏好恢复/落盘协程：换底图或退出时取消，避免旧文档继续写。 */
    private var settingsJob: Job? = null
    /** picker 模式保留原始 Uri（B8）：预览用采样图，导出时重新解码原分辨率。 */
    private var sourceUri: Uri? = null
    /** 待确认的替换底图：已有笔迹时新位图先存这里，用户确认才换（审计 H3）。 */
    private var pendingReplace: Bitmap? = null

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decode(uri) }
            if (bitmap == null) {
                showToast(AnnotateR.string.annotate_image_load_failed)
                finish()
            } else {
                install(bitmap, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val id = intent.getLongExtra(EXTRA_SESSION, -1L)
        val handed = ImageAnnotateHandoff.take(id)
        if (handed != null && !handed.isRecycled) {
            install(handed)
        } else if (savedInstanceState == null) {
            if (intent.getStringExtra(EXTRA_FEATURE) == FEATURE_PICKER) {
                launchPicker()
            } else {
                // 交接对象丢失（进程回收）：直接收掉，不打开空画布。
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 复用：再次从识屏/OCR 页交接位图时换底图；
        // 已有笔迹则先确认，避免把正在编辑的内容静默丢掉（审计 H3）。
        val id = intent.getLongExtra(EXTRA_SESSION, -1L)
        val handed = ImageAnnotateHandoff.take(id)
        if (handed != null && !handed.isRecycled) {
            if (document?.state?.hasElements == true) {
                pendingReplace?.let { if (!it.isRecycled) it.recycle() }
                pendingReplace = handed
                dialog = Dialog.Replace
            } else {
                install(handed)
            }
        } else if (document == null && intent.getStringExtra(EXTRA_FEATURE) == FEATURE_PICKER) {
            launchPicker()
        }
    }

    /** 用户确认替换：装新图（旧文档由 install 丢弃）。 */
    private fun confirmReplace() {
        val next = pendingReplace ?: return
        pendingReplace = null
        dialog = Dialog.None
        install(next)
    }

    /** 取消替换 / 面板被其它路径关掉：暂存位图没人接走，回收避免泄漏大图。 */
    private fun discardPendingReplace() {
        val pending = pendingReplace ?: return
        pendingReplace = null
        if (!pending.isRecycled) pending.recycle()
    }

    private fun launchPicker() {
        if (pickerLaunched) return
        pickerLaunched = true
        runCatching { picker.launch("image/*") }.onFailure {
            showToast(AnnotateR.string.annotate_image_load_failed)
            finish()
        }
    }

    private fun install(source: Bitmap, uri: Uri? = null) {
        if (source.isRecycled) {
            finish()
            return
        }
        // 统一转 ARGB_8888 软件位图：马赛克采样要 getPixel，HARDWARE 位图不支持。
        val owned = runCatching {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }.getOrNull() ?: source.copy(Bitmap.Config.ARGB_8888, false)
        if (owned == null) {
            finish()
            return
        }
        if (owned !== source && !source.isRecycled) source.recycle()
        // 换图前把旧文档的偏好补一次落盘（防抖窗口内换图不丢），再取消旧观察协程。
        document?.let { flushAnnotateSettings(it, AnnotateSettingsCenter) }
        // 上一份底图交给 GC：可能还有一帧在 RenderThread 上绘制，立即回收会崩。
        settingsJob?.cancel()
        document = null
        base = null
        dialog = Dialog.None
        stylePanelTool = null
        // payload 模式没有可重解码的源：传 null 时导出直接用交接位图原尺寸。
        sourceUri = uri
        val doc = AnnotateDocument(AnnotateState.of(owned.width, owned.height))
        document = doc
        base = BaseImage(owned)
        setContent {
            SideGestureTheme {
                val currentDocument = document
                val currentBase = base
                if (currentDocument != null && currentBase != null) {
                    ImageAnnotateScreen(
                        document = currentDocument,
                        base = currentBase,
                        dialog = dialog ?: Dialog.None,
                        stylePanelTool = stylePanelTool,
                        busy = busy,
                        onDialog = { next ->
                            // 关掉替换确认 = 取消：暂存的新图没人接走，回收掉。
                            if (next == Dialog.None && dialog == Dialog.Replace) discardPendingReplace()
                            if (next != Dialog.None) stylePanelTool = null
                            dialog = next
                        },
                        onToolClick = { tool ->
                            // 点工具：选中由工具条完成，这里管理样式卡开合（与弹出卡互斥）。
                            if (dialog != Dialog.None) dialog = Dialog.None
                            stylePanelTool = if (stylePanelTool == tool) null else tool
                        },
                        onClose = { finish() },
                        onExport = { kind -> export(kind) },
                        onReplace = ::confirmReplace,
                    )
                }
            }
        }
        // 偏好异步读取：本宿主的工具条位置固定，只让样式生效（工具条位置对这里无意义）。
        // 读盘通常 <100ms；期间用户改样式被覆盖的概率可忽略。文档一致性保护避免
        // 换图后旧协程把旧文档的样式写进新文档。
        settingsJob = lifecycleScope.launch {
            restoreAnnotateSettings(doc, AnnotateSettingsCenter)
            if (document !== doc) return@launch
            observeAnnotateSettings(doc, AnnotateSettingsCenter)
        }
    }

    private fun export(kind: ExportKind) {
        val doc = document ?: return
        val currentBase = base ?: return
        if (busy) return
        // 导出前提交画布里正在编辑、尚未失焦的文字。
        doc.commitPendingText()
        val uri = sourceUri
        busy = true
        lifecycleScope.launch {
            try {
                // B8：picker 模式按原图全分辨率重解码，坐标从预览尺寸映射过去；
                // payload 模式交接位图就是原始尺寸，直接导出。
                val decodeResult = if (uri != null && !isPreviewFullResolution(uri)) {
                    withContext(Dispatchers.IO) { decodeFull(uri) }
                } else {
                    null
                }
                if (decodeResult != null && !decodeResult.bitmap.isRecycled) {
                    val full = decodeResult.bitmap
                    try {
                        val fullBase = BaseImage(full)
                        val scaled = AnnotateOps.scaleImage(doc.state, full.width, full.height)
                        if (decodeResult.capped) {
                            showToast(AnnotateR.string.annotate_export_capped)
                        }
                        exportAnnotated(scaled, fullBase, kind)
                    } finally {
                        // 导出已同步完成（render / 落盘都在 exportAnnotated 内等待），
                        // 原图副本不再有人引用，立即回收整张大图。
                        if (!full.isRecycled) full.recycle()
                    }
                } else if (uri != null) {
                    // 原图 ≤ 4096：预览位图就是全图（含 EXIF 方向），直接导出，省一次解码。
                    exportAnnotated(doc.state, currentBase, kind)
                } else {
                    // 原图解码失败：回退预览底图，至少能导出。
                    exportAnnotated(doc.state, currentBase, kind)
                }
            } finally {
                busy = false
            }
        }
    }

    override fun onDestroy() {
        // 不在 onDestroy 里 recycle 底图：窗口拆除与最后一帧绘制存在竞争，
        // 回收已显示位图会提升 "trying to use a recycled bitmap" 崩溃风险；交给 GC。
        settingsJob?.cancel()
        settingsJob = null
        // 防抖窗口内退出不丢偏好：立即补一次落盘。
        document?.let { flushAnnotateSettings(it, AnnotateSettingsCenter) }
        base = null
        document = null
        sourceUri = null
        super.onDestroy()
    }

    /**
     * 解码选中图片。
     *
     * 走 `ImageDecoder`：它按 EXIF 方向旋转（`BitmapFactory` 不会，竖拍照片会横着进编辑器），
     * 原生支持 HEIF/WebP，采样也只需一次解码。返回非位图（动图等）或抛错时回退老路径。
     */
    private fun decode(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > MAX_DECODE_PX) sample *= 2
            decoder.setTargetSampleSize(sample)
            // 恒用软件位图：与老路径一致，马赛克要 getPixel，HARDWARE 位图不支持。
            // （SDK 37 起常量扁平化为 ALLOCATOR_SOFTWARE=1，与 API 33 的 MEMORY=1 同义，
            //   static final int 编译期内联，运行时不存在字段兼容问题。）
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
        (drawable as? BitmapDrawable)?.bitmap ?: decodeWithBitmapFactory(uri)
    }.getOrElse { decodeWithBitmapFactory(uri) }

    /** 老路径兜底（不处理 EXIF）：仅在 ImageDecoder 不可用时保底出图。 */
    private fun decodeWithBitmapFactory(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_DECODE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /**
     * 原分辨率解码（B8）：导出时用。
     *
     * 与 [decode] 走同一套 ImageDecoder（EXIF 方向一致，坐标映射才对得上），
     * 失败再回退 BitmapFactory 全量解码。超过 [MAX_FULL_DECODE_PX] 长边时降采样，
     * 并置 [DecodedFull.capped] 供调用方提示。
     */
    private fun decodeFull(uri: Uri): DecodedFull? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        var capped = false
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > MAX_FULL_DECODE_PX) {
                sample *= 2
                capped = true
            }
            decoder.setTargetSampleSize(sample)
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
        (drawable as? BitmapDrawable)?.bitmap?.let { DecodedFull(it, capped) }
            ?: decodeFullWithBitmapFactory(uri)
    }.getOrElse { decodeFullWithBitmapFactory(uri) }

    private fun decodeFullWithBitmapFactory(uri: Uri): DecodedFull? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        var capped = false
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_FULL_DECODE_PX) {
            sample *= 2
            capped = true
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.let { DecodedFull(it, capped) }
    }.getOrNull()

    /**
     * 预览是否已是全分辨率：原图长边 ≤ [MAX_DECODE_PX] 时 [decode] 不采样，
     * 导出无需再解码（P1）。EXIF 旋转只交换长宽，不影响「≤ 上限」的判断。
     */
    private fun isPreviewFullResolution(uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        bounds.outWidth > 0 && bounds.outHeight > 0 &&
            maxOf(bounds.outWidth, bounds.outHeight) <= MAX_DECODE_PX
    }.getOrDefault(false)
}

private data class DecodedFull(val bitmap: Bitmap, val capped: Boolean)

internal enum class Dialog { None, Exit, Export, Replace }

@Composable
private fun ImageAnnotateScreen(
    document: AnnotateDocument,
    base: BaseImage,
    dialog: Dialog,
    stylePanelTool: ToolKind?,
    busy: Boolean,
    onDialog: (Dialog) -> Unit,
    onToolClick: (ToolKind) -> Unit,
    onClose: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onReplace: () -> Unit,
) {
    val requestClose = {
        document.commitPendingText()
        if (document.state.hasElements) onDialog(Dialog.Exit) else onClose()
    }
    BackHandler {
        // 面板优先：返回键先关样式卡/弹出卡，不直接弹放弃确认。
        when {
            stylePanelTool != null -> onToolClick(stylePanelTool!!)
            dialog != Dialog.None -> onDialog(Dialog.None)
            else -> requestClose()
        }
    }

    // 工具条位置只在本次会话内有效；初始底部居中，长按空白处可拖动。
    // 画布始终保持全屏，面板展开不会改变图片坐标或 fit。
    var toolbarOffset by remember { mutableStateOf(IntOffset.Zero) }
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var toolbarSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var cropResetToken by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AnnotateCanvas(
                document = document,
                base = base,
                modifier = Modifier
                    .fillMaxSize(),
            // 落笔即交互开始：收起样式卡/弹出卡。
            onPointerDown = {
                if (dialog != Dialog.None) onDialog(Dialog.None)
                stylePanelTool?.let { onToolClick(it) }
            },
            cropResetToken = cropResetToken,
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.8f)
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom)
                        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                )
                .offset { toolbarOffset },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                .onSizeChanged { toolbarSize = it }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = {},
                        onDragCancel = {},
                    ) { change, dragAmount ->
                        change.consume()
                        val maxX = ((rootSize.width - toolbarSize.width) / 2).coerceAtLeast(0)
                        val minY = -(rootSize.height - toolbarSize.height).coerceAtLeast(0)
                        toolbarOffset = IntOffset(
                            x = (toolbarOffset.x + dragAmount.x.roundToInt()).coerceIn(-maxX, maxX),
                            y = (toolbarOffset.y + dragAmount.y.roundToInt()).coerceIn(minY, 0),
                        )
                    }
                },
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 样式二层卡/退出/导出确认从工具卡上方弹出（无遮罩、无阴影）；
                // 替换图片是破坏性操作，仍走模态面板。样式卡与弹出卡互斥。
                when {
                    stylePanelTool != null -> AnnotateInlineCard {
                        StylePanel(
                            tool = stylePanelTool!!,
                            document = document,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            onCropReset = { cropResetToken++ },
                        )
                    }

                    dialog == Dialog.Exit -> AnnotateInlineCard {
                        ConfirmPanel(
                            title = stringResource(AnnotateR.string.annotate_discard_title),
                            onCancel = { onDialog(Dialog.None) },
                        ) {
                            PressTextButton(
                                onClick = onClose,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(AnnotateR.string.annotate_discard_confirm))
                            }
                        }
                    }

                    dialog == Dialog.Export -> AnnotateInlineCard {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ExportAction(
                                icon = Icons.Default.SaveAlt,
                                label = stringResource(AnnotateR.string.annotate_save_to_gallery),
                            ) {
                                onDialog(Dialog.None)
                                onExport(ExportKind.Save)
                            }
                            ExportAction(
                                icon = Icons.Default.ContentCopy,
                                label = stringResource(AnnotateR.string.annotate_copy),
                            ) {
                                onDialog(Dialog.None)
                                onExport(ExportKind.Copy)
                            }
                            ExportAction(
                                icon = Icons.Default.Share,
                                label = stringResource(AnnotateR.string.annotate_share),
                            ) {
                                onDialog(Dialog.None)
                                onExport(ExportKind.Share)
                            }
                            PressTextButton(onClick = { onDialog(Dialog.None) }) {
                                Text(stringResource(AnnotateR.string.annotate_cancel))
                            }
                        }
                    }
                }

                AnnotateToolbar(
                    document = document,
                    onExit = requestClose,
                    onExport = { onDialog(Dialog.Export) },
                    exportLabel = stringResource(AnnotateR.string.annotate_export),
                    modifier = Modifier.fillMaxWidth(),
                    onToolClick = onToolClick,
                    onCropReset = { cropResetToken++ },
                )
            }
        }

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

        if (dialog == Dialog.Replace) {
            ActionPanel(
                onDismiss = { onDialog(Dialog.None) },
                onReplace = onReplace,
            )
        }
    }
}

/**
 * 页内动作面板，而不是 `AlertDialog`：
 * 本 Activity 用平台 Material 主题（非 AppCompat/M3 XML 主题），Compose `Dialog`
 * 的窗口在这里会丢背景与遮罩；改用同层 Box + Surface，样式与宿主可控。
 */
/** 仅替换图片确认维持模态面板（破坏性操作）；导出/退出已改为工具卡上方内联卡。 */
@Composable
private fun ActionPanel(
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = LargeShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            // 全部去阴影（计划）：模态面板也不留投影，层次靠色差与遮罩。
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(AnnotateR.string.annotate_replace_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    text = stringResource(AnnotateR.string.annotate_replace_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                PanelAction(
                    label = stringResource(AnnotateR.string.annotate_replace_confirm),
                    destructive = true,
                ) {
                    onReplace()
                }
                PanelAction(stringResource(AnnotateR.string.annotate_cancel), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun PanelAction(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    PressTextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

/** 工具卡上方弹出的内联卡：无遮罩、无阴影，同层叠在工具条上方。 */
@Composable
private fun AnnotateInlineCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = LargeShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        // 无投影：弹出卡与工具卡同一视觉层次（全部去阴影计划）。
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}
