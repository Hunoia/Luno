package hunoia.luno.ui.annotate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.R as AnnotateR
import hunoia.luno.annotate.render.AnnotateExporter
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.ui.AnnotateCanvas
import hunoia.luno.annotate.ui.AnnotateToolbar
import hunoia.luno.bridge.system.ImageOutput
import hunoia.luno.bridge.system.copyImage
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
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
                install(bitmap)
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
        // singleTask 复用：再次从识屏/OCR 页交接位图时直接换底图。
        val id = intent.getLongExtra(EXTRA_SESSION, -1L)
        val handed = ImageAnnotateHandoff.take(id)
        if (handed != null && !handed.isRecycled) {
            install(handed)
        } else if (document == null && intent.getStringExtra(EXTRA_FEATURE) == FEATURE_PICKER) {
            launchPicker()
        }
    }

    private fun launchPicker() {
        if (pickerLaunched) return
        pickerLaunched = true
        runCatching { picker.launch("image/*") }.onFailure {
            showToast(AnnotateR.string.annotate_image_load_failed)
            finish()
        }
    }

    private fun install(source: Bitmap) {
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
        // 上一份底图交给 GC：可能还有一帧在 RenderThread 上绘制，立即回收会崩。
        document = null
        base = null
        dialog = Dialog.None
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
                        onDialog = { dialog = it },
                        onClose = { finish() },
                        onExport = { kind -> export(kind) },
                    )
                }
            }
        }
    }

    private fun export(kind: ExportKind) {
        val doc = document ?: return
        val currentBase = base ?: return
        if (busy) return
        // 导出前提交画布里正在编辑、尚未失焦的文字。
        doc.commitPendingText()
        busy = true
        lifecycleScope.launch {
            val rendered = runCatching {
                withContext(Dispatchers.Default) {
                    AnnotateExporter.renderCropped(doc.state, currentBase)
                }
            }.getOrNull()
            if (rendered == null) {
                busy = false
                showToast(AnnotateR.string.annotate_export_failed)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                try {
                    when (kind) {
                        ExportKind.Save ->
                            ImageOutput.saveToGallery(this@ImageAnnotateActivity, rendered, "annotate") != null
                        ExportKind.Copy ->
                            ImageOutput.saveToGallery(this@ImageAnnotateActivity, rendered, "annotate")
                                ?.let { uri -> copyImage(this@ImageAnnotateActivity, "annotate", uri) }
                                ?: false
                        ExportKind.Share ->
                            ImageOutput.saveToCache(this@ImageAnnotateActivity, rendered, "annotate")
                                ?.let { uri -> ImageOutput.share(this@ImageAnnotateActivity, uri, "annotate") }
                                ?: false
                    }
                } finally {
                    if (!rendered.isRecycled) rendered.recycle()
                }
            }
            busy = false
            when {
                !ok -> showToast(AnnotateR.string.annotate_export_failed)
                kind == ExportKind.Save -> showToast(AnnotateR.string.annotate_saved)
                kind == ExportKind.Copy -> showToast(AnnotateR.string.annotate_copied)
            }
        }
    }

    override fun onDestroy() {
        // 不在 onDestroy 里 recycle 底图：窗口拆除与最后一帧绘制存在竞争，
        // 回收已显示位图会提升 "trying to use a recycled bitmap" 崩溃风险；交给 GC。
        base = null
        document = null
        super.onDestroy()
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_DECODE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

internal enum class ExportKind { Save, Copy, Share }

internal enum class Dialog { None, Exit, Export }

@Composable
private fun ImageAnnotateScreen(
    document: AnnotateDocument,
    base: BaseImage,
    dialog: Dialog,
    busy: Boolean,
    onDialog: (Dialog) -> Unit,
    onClose: () -> Unit,
    onExport: (ExportKind) -> Unit,
) {
    val requestClose = {
        document.commitPendingText()
        if (document.state.hasElements) onDialog(Dialog.Exit) else onClose()
    }
    BackHandler {
        // 面板优先：返回键先关面板，不直接弹放弃确认。
        if (dialog != Dialog.None) onDialog(Dialog.None) else requestClose()
    }

    // 工具条高度：只量工具条本体（不含 IME inset），画布据此让出底部，整图可见。
    // IME 弹起时工具条上移而高度不变，画布尺寸不变，正在落的文字不会跟着 fit 重算跳位。
    val density = LocalDensity.current
    var toolbarHeightPx by remember { mutableStateOf(0) }

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
                .padding(bottom = with(density) { toolbarHeightPx.toDp() }),
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
                ),
        ) {
            AnnotateToolbar(
                document = document,
                onExit = requestClose,
                onExport = { onDialog(Dialog.Export) },
                exportLabel = stringResource(AnnotateR.string.annotate_export),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { toolbarHeightPx = it.height }
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
                        Dialog.None -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (dialog == Dialog.Exit) {
                    Text(
                        text = stringResource(AnnotateR.string.annotate_discard_message),
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
