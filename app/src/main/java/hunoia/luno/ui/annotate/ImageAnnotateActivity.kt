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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.R as AnnotateR
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.ExportKind
import hunoia.luno.annotate.render.exportAnnotated
import hunoia.luno.annotate.store.flushAnnotateSettings
import hunoia.luno.annotate.store.observeAnnotateSettings
import hunoia.luno.annotate.store.restoreAnnotateSettings
import hunoia.luno.annotate.ui.AnnotateCanvas
import hunoia.luno.annotate.ui.AnnotateToolbar
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.domain.annotate.AnnotateSettingsCenter
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                        busy = busy,
                        onDialog = { next ->
                            // 关掉替换确认 = 取消：暂存的新图没人接走，回收掉。
                            if (next == Dialog.None && dialog == Dialog.Replace) discardPendingReplace()
                            dialog = next
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
                val full = if (uri != null) {
                    withContext(Dispatchers.IO) { decodeFull(uri) }
                } else {
                    null
                }
                if (full != null && !full.isRecycled) {
                    try {
                        val fullBase = BaseImage(full)
                        val scaled = AnnotateOps.scaleImage(doc.state, full.width, full.height)
                        exportAnnotated(scaled, fullBase, kind)
                    } finally {
                        // 导出已同步完成（render / 落盘都在 exportAnnotated 内等待），
                        // 原图副本不再有人引用，立即回收整张大图。
                        if (!full.isRecycled) full.recycle()
                    }
                } else {
                    // 原图解码失败 / payload 模式：回退预览底图，至少能导出。
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
     * 原分辨率解码（B8）：导出时用，不采样。
     *
     * 与 [decode] 走同一套 ImageDecoder（EXIF 方向一致，坐标映射才对得上），
     * 失败再回退 BitmapFactory 全量解码。调用方负责在 IO 线程执行并回收。
     */
    private fun decodeFull(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
        (drawable as? BitmapDrawable)?.bitmap ?: decodeFullWithBitmapFactory(uri)
    }.getOrElse { decodeFullWithBitmapFactory(uri) }

    private fun decodeFullWithBitmapFactory(uri: Uri): Bitmap? = runCatching {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

internal enum class Dialog { None, Exit, Export, Replace }

@Composable
private fun ImageAnnotateScreen(
    document: AnnotateDocument,
    base: BaseImage,
    dialog: Dialog,
    busy: Boolean,
    onDialog: (Dialog) -> Unit,
    onClose: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onReplace: () -> Unit,
) {
    val requestClose = {
        document.commitPendingText()
        if (document.state.hasElements) onDialog(Dialog.Exit) else onClose()
    }
    BackHandler {
        // 面板优先：返回键先关面板，不直接弹放弃确认。
        if (dialog != Dialog.None) onDialog(Dialog.None) else requestClose()
    }

    // 工具条区域：量「容器」而不是工具条本体——容器还叠了导航栏 inset，只量工具条
    // 会让画布底部少让出一个导航栏高度，竖图底部被工具条盖住（审计 H4）。
    // IME 弹起时容器会长出键盘高度，那一刻冻结旧值：画布尺寸不随键盘重排，
    // 正在落的文字才不会跟着 fit 重新计算跳位。
    val density = LocalDensity.current
    var toolbarAreaPx by remember { mutableStateOf(0) }
    val imeBottomPx by rememberUpdatedState(WindowInsets.ime.getBottom(density))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AnnotateCanvas(
            document = document,
            base = base,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = with(density) { toolbarAreaPx.toDp() }),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 导航栏 ∪ 键盘：IME 可见时 inset 已含导航栏，相加会多顶一截。
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom)
                        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                )
                .onSizeChanged { if (imeBottomPx == 0) toolbarAreaPx = it.height },
        ) {
            AnnotateToolbar(
                document = document,
                onExit = requestClose,
                onExport = { onDialog(Dialog.Export) },
                exportLabel = stringResource(AnnotateR.string.annotate_export),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
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

        if (dialog != Dialog.None) {
            ActionPanel(
                dialog = dialog,
                onDismiss = { onDialog(Dialog.None) },
                onClose = onClose,
                onReplace = onReplace,
                onExport = { kind ->
                    onDialog(Dialog.None)
                    onExport(kind)
                },
            )
        }
    }
}

/**
 * 页内动作面板，而不是 `AlertDialog`：
 * 本 Activity 用平台 Material 主题（非 AppCompat/M3 XML 主题），Compose `Dialog`
 * 的窗口在这里会丢背景与遮罩；改用同层 Box + Surface，样式与宿主可控。
 */
@Composable
private fun ActionPanel(
    dialog: Dialog,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onReplace: () -> Unit,
    onExport: (ExportKind) -> Unit,
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
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = when (dialog) {
                        Dialog.Exit -> stringResource(AnnotateR.string.annotate_discard_title)
                        Dialog.Export -> stringResource(AnnotateR.string.annotate_export)
                        Dialog.Replace -> stringResource(AnnotateR.string.annotate_replace_title)
                        Dialog.None -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (dialog == Dialog.Exit || dialog == Dialog.Replace) {
                    Text(
                        text = stringResource(
                            if (dialog == Dialog.Exit) {
                                AnnotateR.string.annotate_discard_message
                            } else {
                                AnnotateR.string.annotate_replace_message
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                when (dialog) {
                    Dialog.Export -> {
                        PanelAction(stringResource(AnnotateR.string.annotate_save_to_gallery)) {
                            onExport(ExportKind.Save)
                        }
                        PanelAction(stringResource(AnnotateR.string.annotate_copy)) {
                            onExport(ExportKind.Copy)
                        }
                        PanelAction(stringResource(AnnotateR.string.annotate_share)) {
                            onExport(ExportKind.Share)
                        }
                    }

                    Dialog.Exit -> {
                        PanelAction(
                            label = stringResource(AnnotateR.string.annotate_discard_confirm),
                            destructive = true,
                        ) {
                            onClose()
                        }
                    }

                    Dialog.Replace -> {
                        PanelAction(
                            label = stringResource(AnnotateR.string.annotate_replace_confirm),
                            destructive = true,
                        ) {
                            onReplace()
                        }
                    }

                    Dialog.None -> Unit
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
    TextButton(
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
