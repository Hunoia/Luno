package hunoia.luno.ui.ocr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as selectedSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import hunoia.luno.R
import hunoia.luno.bridge.system.ImageOutput
import hunoia.luno.bridge.system.copyImage
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.capability.ocr.OcrLine
import hunoia.luno.capability.ocr.OcrTokenizer
import hunoia.luno.capability.ocr.OcrWord
import hunoia.luno.quicklaunch.QuickSearchActivity
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.MotionPanelIn
import hunoia.luno.ui.theme.MotionPanelOut
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 框选 / 长按 OCR 的结果页：透明 Activity + 底部卡片。
 *
 * 住在 `:service`，与 `ScreenPickOverlay` / OCR 引擎同进程；结果通过 [OcrResultHandoff]
 * 交接，不把大文本或位图塞进 Intent。Activity 只做展示、选择与动作发起，不直接持 OCR 引擎。
 *
 * 三种内容同屏可选：a11y 文本、OCR 文本、裁剪图，各自来自同一次识屏。
 * 文本命中时 OCR 可延迟到用户切到 OCR 来源，默认先展示无障碍文本。
 */
class OcrResultActivity : ComponentActivity() {

    private var payload by mutableStateOf(OcrResultPayload())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (!applyIntent(intent)) {
            finish()
            return
        }
        setContent {
            SideGestureTheme {
                // 新结果换掉 payload 时整页状态重置（singleTask 复用同一 Activity）。
                key(payload) {
                OcrResultScreen(
                    payload = payload,
                    onClose = { finish() },
                    onCopy = { value ->
                        if (value.isBlank()) {
                            showToast(R.string.ocr_result_empty)
                        } else {
                            copyText(this, "ocr_result", value)
                            showToast(R.string.ocr_result_copied)
                        }
                    },
                    // 搜索 / 分享成功返回 true，由结果页播退场动画后再 finish。
                    onSearch = { value ->
                        when {
                            value.isBlank() -> {
                                showToast(R.string.ocr_result_empty)
                                false
                            }
                            QuickSearchActivity.start(this, value) -> true
                            else -> {
                                showToast(R.string.ocr_result_failed)
                                false
                            }
                        }
                    },
                    onShare = { value ->
                        when {
                            value.isBlank() -> {
                                showToast(R.string.ocr_result_empty)
                                false
                            }
                            share(value) -> true
                            else -> {
                                showToast(R.string.ocr_result_failed)
                                false
                            }
                        }
                    },
                    onImageAction = ::onImageAction,
                )
                }
            }
        }
    }

    override fun onDestroy() {
        payload.image?.let { if (!it.isRecycled) it.recycle() }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!applyIntent(intent)) finish()
    }

    private fun applyIntent(intent: Intent?): Boolean {
        val id = intent?.getLongExtra(EXTRA_SESSION, -1L) ?: -1L
        val next = OcrResultHandoff.take(id) ?: return false
        if (next.isEmpty) {
            next.image?.recycle()
            return false
        }
        payload = next
        return true
    }

    /** 图片动作：与识屏拾取共用 [ImageOutput]，不另写一套落盘/授权逻辑。 */
    private fun onImageAction(action: OcrImageAction) {
        val bitmap = payload.image ?: return
        if (bitmap.isRecycled) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (action) {
                    // 复制要的是一个可被粘贴方读取的 URI，所以先落图库再写剪贴板。
                    OcrImageAction.Copy ->
                        ImageOutput.saveToGallery(this@OcrResultActivity, bitmap, "ocr_result")
                            ?.let { uri ->
                                copyImage(this@OcrResultActivity, "ocr_result", uri)
                            } ?: false
                    // 保存只写图库：不额外改剪贴板，避免「保存」带出看不见的副作用。
                    OcrImageAction.Save ->
                        ImageOutput.saveToGallery(this@OcrResultActivity, bitmap, "ocr_result") != null
                    OcrImageAction.Share ->
                        ImageOutput.saveToCache(this@OcrResultActivity, bitmap, "ocr_result")
                            ?.let { uri -> ImageOutput.share(this@OcrResultActivity, uri, "ocr_result") }
                            ?: false
                }
            }
            when {
                !ok -> showToast(R.string.ocr_result_failed)
                action == OcrImageAction.Save -> showToast(R.string.ocr_result_image_saved)
                action == OcrImageAction.Copy -> showToast(R.string.ocr_result_image_copied)
            }
        }
    }

    /** 系统分享面板；失败返回 false，由结果页提示。 */
    private fun share(text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { startActivity(chooser) }.isSuccess
    }

    companion object {
        internal const val EXTRA_SESSION = "hunoia.luno.ocr.extra.SESSION"

        /**
         * 从识屏浮窗拉起结果页。返回 false = 启动失败，调用方提示并收层。
         *
         * BAL：应用有 SYSTEM_ALERT_WINDOW 且当前有可见 overlay；项目内已有同样路径
         *（QuickSearchActivity / ActionSelectHostActivity）。真机被拦时由调用方兜底。
         */
        fun start(context: Context, payload: OcrResultPayload): Boolean {
            if (payload.isEmpty) return false
            val id = OcrResultHandoff.put(payload)
            val intent = Intent(context, OcrResultActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_SESSION, id)
            }
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (!ok) OcrResultHandoff.discard(id)
            return ok
        }
    }
}

/** 文本来源：同一次识屏的 a11y 取词与模型识别各一份。 */
internal enum class ResultSource { A11y, Ocr }

/**
 * 结果页默认来源：有 a11y 且 OCR 还是懒加载时先展示无障碍，避免开场等待模型；
 * 已经拿到 OCR 文本（框选等路径）时保持原来的默认 OCR 行为。
 */
internal fun defaultResultSource(
    ocrText: String,
    hasLazyOcr: Boolean,
    a11yText: String?,
): ResultSource {
    val ocrAvailable = ocrText.isNotBlank() || hasLazyOcr
    val a11yAvailable = !a11yText.isNullOrBlank()
    return when {
        a11yAvailable && ocrText.isBlank() -> ResultSource.A11y
        ocrAvailable -> ResultSource.Ocr
        a11yAvailable -> ResultSource.A11y
        else -> ResultSource.Ocr
    }
}

/** 内容形态：文本（分词 chip）或原图裁剪。 */
private enum class ResultPane { Text, Image }

internal enum class OcrImageAction { Copy, Share, Save }

/** 卡片高度上限：分词页面最多占半屏，长文本内部滚动。 */
private const val CardMaxHeightFraction = 0.5f

/** 文本区三种形态：互斥切换时做一次短促的淡入淡出 + 高度过渡。 */
private enum class ContentKind { Chips, Editor, Image }

@Composable
private fun OcrResultScreen(
    payload: OcrResultPayload,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onSearch: (String) -> Boolean,
    onShare: (String) -> Boolean,
    onImageAction: (OcrImageAction) -> Unit,
) {
    val a11yAvailable = !payload.a11yText.isNullOrBlank()
    val ocrLoader = payload.ocrLoader
    val ocrAvailable = payload.hasOcrSource
    val image = payload.image
    val scope = rememberCoroutineScope()

    var source by rememberSaveable {
        mutableStateOf(
            defaultResultSource(
                ocrText = payload.ocrText,
                hasLazyOcr = ocrLoader != null,
                a11yText = payload.a11yText,
            ),
        )
    }
    var pane by rememberSaveable { mutableStateOf(ResultPane.Text) }
    var editing by rememberSaveable { mutableStateOf(false) }
    // 两份文本各自的当前值：编辑只改「当前来源」那一份，切回来还在。
    var ocrText by rememberSaveable { mutableStateOf(payload.ocrText) }
    var a11yText by rememberSaveable { mutableStateOf(payload.a11yText.orEmpty()) }
    // 懒加载 OCR 只在切到 OCR 来源时跑一次；加载中不阻塞无障碍文本。
    var ocrLoading by remember { mutableStateOf(false) }
    var ocrLoaded by remember { mutableStateOf(payload.ocrText.isNotBlank()) }
    val loadOcr: () -> Unit = {
        if (!ocrLoading && !ocrLoaded && ocrLoader != null) {
            ocrLoading = true
            scope.launch {
                val value = runCatching { ocrLoader() }.getOrNull().orEmpty()
                ocrText = value
                ocrLoaded = true
                ocrLoading = false
            }
        }
    }
    val text = if (source == ResultSource.Ocr) ocrText else a11yText

    // 多选：全局词下标集合。动作作用于“选中词按原文顺序拼接”，未选中时作用于全文。
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // chip 在根坐标下的命中区；拖动选择要跨行、跨 FlowRow，所以命中共用一张表。
    val chipBounds = remember { mutableStateMapOf<Int, Rect>() }
    val listCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentSelected by rememberUpdatedState(selected)

    // 进场：先挂载再置位，让 AnimatedVisibility 真正播一次；退场播完再 finish。
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    var closing by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            visible.targetState = false
        }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (closing && visible.isIdle && !visible.currentState) onClose()
    }
    BackHandler { requestClose() }

    // 进入编辑即聚焦并弹出 IME，不用用户再点一次输入框。
    val editorFocus = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) editorFocus.requestFocus()
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    // 派生状态：IME inset 在键盘动画期间逐帧变化，只在「可见/不可见」翻转时触发组合，
    // 否则整页会跟着键盘动画逐帧重组。
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    // 退出编辑 = 先收键盘，等 IME 真正收起再换内容。
    // 三件事挤在同一段动画里会互相踩：键盘下滑（逐帧改 inset）、TextField → 分词列表换行、
    // 列表重新测量，表现为点「完成」时卡一下（同 ActionSelectBottomBar 的收起口径）。
    var pendingExitEdit by remember { mutableStateOf(false) }
    // 编辑中途切到图片：先按同一口径收键盘，收完再切形态，避免"内容切换 + 键盘下滑"叠在一起。
    var pendingPane by remember { mutableStateOf<ResultPane?>(null) }
    fun applyEditingDone() {
        editing = false
        selected = emptySet()
        pendingPane?.let {
            pane = it
            pendingPane = null
        }
    }
    val exitEdit: () -> Unit = {
        if (imeVisible) {
            pendingExitEdit = true
            keyboard?.hide()
        } else {
            pendingExitEdit = false
            applyEditingDone()
        }
    }
    LaunchedEffect(pendingExitEdit, imeVisible) {
        if (pendingExitEdit && !imeVisible) {
            pendingExitEdit = false
            applyEditingDone()
        }
    }
    // 编辑时不做分词：避免每敲一个字都在主线程跑一遍 ICU；切回只读时再算。
    val lines = remember(text, editing, pane) {
        if (editing || pane == ResultPane.Image) emptyList() else OcrTokenizer.lines(text)
    }
    // 每行第一个词的全局下标；选择用全局下标，跨行也能共用一套状态。
    val lineStarts = remember(lines) { lineStartsOf(lines) }
    val selectedText = remember(lines, selected, text) {
        if (selected.isEmpty()) text else joinSelected(lines, lineStarts, selected)
    }
    // 全选口径：所有行的所有词（空行不占下标）。
    val allWords = remember(lines) { lines.sumOf { it.words.size } }
    val allSelected = allWords > 0 && selected.size == allWords

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(MotionPanelIn)),
            exit = fadeOut(tween(MotionPanelOut)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = requestClose,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 封顶半屏，基准用屏幕高度（只有旋转/分屏才变）。
            // 不能用 BoxWithConstraints.maxHeight：键盘动画期间它逐帧变小，卡片高度跟着
            // 每帧重算，chip 会跟着重新换行。键盘占用由 imePadding 的父约束自然夹住。
            val cardMaxHeight = LocalConfiguration.current.screenHeightDp.dp *
                CardMaxHeightFraction
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.fillMaxWidth(),
                enter = slideInVertically(tween(MotionPanelIn)) { it } +
                    fadeIn(tween(MotionPanelIn)),
                exit = slideOutVertically(tween(MotionPanelOut)) { it } +
                    fadeOut(tween(MotionPanelOut)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .heightIn(min = 160.dp, max = cardMaxHeight)
                        .padding(horizontal = PageGutter, vertical = ContentBottom),
                    shape = ExtraLargeShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageGutter, vertical = PageGutter)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (a11yAvailable && ocrAvailable || image != null) {
                            SourceAndPaneBar(
                                showSource = a11yAvailable && ocrAvailable,
                                source = source,
                                onSource = {
                                    if (it != source) {
                                        source = it
                                        selected = emptySet()
                                        if (it == ResultSource.Ocr) loadOcr()
                                    }
                                },
                                showPane = image != null,
                                pane = pane,
                                onPane = {
                                    if (it != pane) {
                                        if (editing) {
                                            pendingPane = it
                                            exitEdit()
                                        } else {
                                            pane = it
                                            selected = emptySet()
                                        }
                                    }
                                },
                            )
                        }

                        AnimatedContent(
                            targetState = when {
                                pane == ResultPane.Image && image != null -> ContentKind.Image
                                editing -> ContentKind.Editor
                                else -> ContentKind.Chips
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            transitionSpec = {
                                // 换内容时高度跟着 animate，避免编辑框与列表高度差一下跳过去。
                                (fadeIn(tween(MotionSwapIn)) +
                                    slideInVertically(tween(MotionSwapIn)) { it / 12 })
                                    .togetherWith(fadeOut(tween(MotionSwapOut)))
                                    .using(SizeTransform(clip = false) { _, _ -> tween(MotionSubtle) })
                            },
                            label = "ocr-content",
                        ) { kind ->
                            when (kind) {
                                ContentKind.Image -> ImagePane(
                                    bitmap = image ?: return@AnimatedContent,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ContentKind.Editor -> OutlinedTextField(
                                    value = text,
                                    onValueChange = {
                                        if (source == ResultSource.Ocr) ocrText = it else a11yText = it
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp)
                                        .focusRequester(editorFocus),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    placeholder = {
                                        Text(stringResource(R.string.ocr_result_placeholder))
                                    },
                                )
                                ContentKind.Chips -> if (ocrLoading && source == ResultSource.Ocr) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 80.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                } else if (
                                    source == ResultSource.Ocr &&
                                    ocrLoaded &&
                                    ocrText.isBlank()
                                ) {
                                    Text(
                                        text = stringResource(R.string.ocr_result_empty),
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { listCoords.value = it }
                                            .pointerInput(Unit) {
                                                detectChipSelection(
                                                    chipBounds = chipBounds,
                                                    listCoords = listCoords,
                                                    currentSelected = { currentSelected },
                                                    onSelection = { selected = it },
                                                )
                                            },
                                        contentPadding = PaddingValues(vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                    ) {
                                        itemsIndexed(lines) { index, line ->
                                            WordChips(
                                                line = line,
                                                startIndex = lineStarts[index],
                                                selected = selected,
                                                bounds = chipBounds,
                                                onToggle = { selected = toggle(selected, it) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ResultActions(
                            imageMode = pane == ResultPane.Image && image != null,
                            editing = editing,
                            onCopy = { onCopy(selectedText) },
                            onSearch = { if (onSearch(selectedText)) requestClose() },
                            onShare = { if (onShare(selectedText)) requestClose() },
                            pendingEdit = pendingExitEdit,
                            onToggleEdit = {
                                if (editing) exitEdit() else editing = true
                            },
                            onSelectAll = {
                                selected = if (allSelected) {
                                    emptySet()
                                } else {
                                    (0 until allWords).toSet()
                                }
                            },
                            allSelected = allSelected,
                            onImageAction = onImageAction,
                        )
                    }
                }
            }
        }
    }
}

/** 顶部两个紧凑分段：内容来源（无障碍/OCR）与形态（文本/图片）。都没有时整行不渲染。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceAndPaneBar(
    showSource: Boolean,
    source: ResultSource,
    onSource: (ResultSource) -> Unit,
    showPane: Boolean,
    pane: ResultPane,
    onPane: (ResultPane) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSource) {
            MiniSegmented(
                labels = listOf(
                    stringResource(R.string.ocr_result_source_a11y),
                    stringResource(R.string.ocr_result_source_ocr),
                ),
                selectedIndex = if (source == ResultSource.A11y) 0 else 1,
                onSelect = { onSource(if (it == 0) ResultSource.A11y else ResultSource.Ocr) },
            )
        }
        if (showSource && showPane) {
            Box(modifier = Modifier.weight(1f))
        }
        if (showPane) {
            if (!showSource) Box(modifier = Modifier.weight(1f))
            MiniSegmented(
                labels = listOf(
                    stringResource(R.string.ocr_result_view_text),
                    stringResource(R.string.ocr_result_view_image),
                ),
                selectedIndex = if (pane == ResultPane.Text) 0 else 1,
                onSelect = { onPane(if (it == 0) ResultPane.Text else ResultPane.Image) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                // 去掉勾选图标：40dp 高的小分段放不下图标 + 文字。
                icon = {},
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** 原图视图：双指缩放 + 单指平移，缩放回弹到 1x。 */
@Composable
private fun ImagePane(bitmap: Bitmap, modifier: Modifier = Modifier) {
    var scale by remember(bitmap) { mutableStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.ocr_result_view_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
    }
}

/**
 * chip 选择手势。
 *
 * - 按下命中 chip 且**首个移动是横向**才进入选择；竖向直接交还给列表滚动。
 * - 进入选择后任意方向（含竖向跨行）都继续扩选；从已选词起拖是取消这一段。
 * - 抬手无滑动 = 点选（再点同一个词取消）；按在空白处抬手 = 清空选择。
 */
private suspend fun PointerInputScope.detectChipSelection(
    chipBounds: Map<Int, Rect>,
    listCoords: State<LayoutCoordinates?>,
    currentSelected: () -> Set<Int>,
    onSelection: (Set<Int>) -> Unit,
) {
    awaitEachGesture {
        // 不消费 down：是否接管手势要等首个移动的方向定下来。
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val anchor = hitChip(chipBounds, toRootPoint(listCoords.value, down.position))
        var base = emptySet<Int>()
        var deselect = false
        var selecting = false
        var cancelled = false
        // 进入选择后锚点必非空；单独存一份，免去每轮的空判断。
        var activeAnchor = -1
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) break
            // 列表先消费了（竖向滚动）→ 本次不再选择，也别当成点选。
            if (change.isConsumed) {
                cancelled = true
                break
            }
            val delta = change.position - down.position
            if (!selecting) {
                if (delta.getDistance() <= viewConfiguration.touchSlop) continue
                if (anchor == null || abs(delta.x) <= abs(delta.y)) {
                    cancelled = true
                    break
                }
                selecting = true
                activeAnchor = anchor
                base = currentSelected()
                deselect = anchor in base
                onSelection(if (deselect) base - anchor else base + anchor)
            }
            change.consume()
            val point = toRootPoint(listCoords.value, change.position)
            // 手指滑到 chip 之间的缝隙时按最近 chip 兜底，竖向跨行不会断。
            val hit = hitChipNear(chipBounds, point) ?: continue
            val range = wordRange(activeAnchor, hit)
            onSelection(if (deselect) base - range else base + range)
        }
        if (cancelled) return@awaitEachGesture
        if (selecting) return@awaitEachGesture
        // 原地抬手：chip 上点选 toggle，空白处清空。
        onSelection(if (anchor != null) toggle(currentSelected(), anchor) else emptySet())
    }
}

/** 视图坐标 → 根坐标；列表尚未布局时按原值兜底。 */
private fun toRootPoint(coords: LayoutCoordinates?, point: Offset): Offset =
    coords?.localToRoot(point) ?: point

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(
    line: OcrLine,
    startIndex: Int,
    selected: Set<Int>,
    bounds: MutableMap<Int, Rect>,
    onToggle: (Int) -> Unit,
) {
    if (line.words.isEmpty()) return
    val selectLabel = stringResource(R.string.ocr_result_select_word)
    val unselectLabel = stringResource(R.string.ocr_result_unselect_word)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        line.words.forEachIndexed { local, word ->
            val index = startIndex + local
            val isSelected = index in selected
            // 命中区要在长按/拖动时按根坐标查找；离开组合就摘掉，避免懒加载留下旧位置。
            DisposableEffect(index) {
                onDispose { bounds.remove(index) }
            }
            // 外层 48dp = 命中区，内层 32dp = 视觉高度：chip 看着小，手指仍然点得中。
            Box(
                modifier = Modifier
                    .heightIn(min = MinInteractiveSize)
                    .onGloballyPositioned { bounds[index] = it.boundsInRoot() }
                    .semantics {
                        selectedSemantics = isSelected
                        role = Role.Tab
                        onClick(label = if (isSelected) unselectLabel else selectLabel) {
                            onToggle(index)
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.heightIn(min = 32.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    // 卡底色与容器色太接近时纯填充看不出边界，靠一圈细描边立住轮廓。
                    border = if (isSelected) {
                        null
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = word.text,
                            style = MaterialTheme.typography.labelMedium,
                            // 长链接在分词里是一个完整 chip：允许换行显示，
                            // 但固定高度会裁掉第二行；用 maxLines + 省略号兜底。
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 根坐标下命中的词（全局下标）。chip 之间/行之间有缝隙，
 * 落空返回 null，由调用方决定保留还是兜底。
 */
internal fun hitChip(bounds: Map<Int, Rect>, point: Offset): Int? =
    bounds.entries.firstOrNull { it.value.contains(point) }?.key

/** 命中不到时取 [maxDistancePx] 内最近的 chip：竖向跨行拖到缝隙里也不会断线。 */
internal fun hitChipNear(
    bounds: Map<Int, Rect>,
    point: Offset,
    maxDistancePx: Float = 72f,
): Int? {
    hitChip(bounds, point)?.let { return it }
    var best: Int? = null
    var bestDistance = maxDistancePx
    bounds.forEach { (index, rect) ->
        val dx = maxOf(rect.left - point.x, 0f, point.x - rect.right)
        val dy = maxOf(rect.top - point.y, 0f, point.y - rect.bottom)
        val distance = hypot(dx, dy)
        if (distance <= bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}

/** 每行第一个词的全局下标。 */
internal fun lineStartsOf(lines: List<OcrLine>): IntArray =
    IntArray(lines.size).also { starts ->
        var acc = 0
        lines.forEachIndexed { index, line ->
            starts[index] = acc
            acc += line.words.size
        }
    }

internal fun toggle(selected: Set<Int>, index: Int): Set<Int> =
    if (index in selected) selected - index else selected + index

internal fun wordRange(a: Int, b: Int): IntRange = if (a <= b) a..b else b..a

/** 选中词在本行内的字符区间（[start, end)），相邻/重叠先合并，便于一次绘制。 */
internal fun selectedRanges(
    line: OcrLine,
    startIndex: Int,
    selected: Set<Int>,
): List<Pair<Int, Int>> {
    val ranges = ArrayList<Pair<Int, Int>>()
    line.words.forEachIndexed { local, word ->
        if (startIndex + local !in selected) return@forEachIndexed
        val last = ranges.lastOrNull()
        if (last != null && word.start <= last.second) {
            ranges[ranges.lastIndex] = last.first to maxOf(last.second, word.end)
        } else {
            ranges += word.start to word.end
        }
    }
    return ranges
}

/** 选中词按原文顺序拼接：行内取原文子串；相邻选中段之间原文只有空白就原样保留，
 *  否则补一个空格隔开未选中的内容；行间换行。 */
internal fun joinSelected(
    lines: List<OcrLine>,
    lineStarts: IntArray,
    selected: Set<Int>,
): String {
    val out = StringBuilder()
    lines.forEachIndexed { index, line ->
        val ranges = selectedRanges(line, lineStarts[index], selected)
        if (ranges.isEmpty()) return@forEachIndexed
        if (out.isNotEmpty()) out.append('\n')
        ranges.forEachIndexed { rangeIndex, (start, end) ->
            if (rangeIndex > 0) {
                // 两个选中段之间的原文间隔：只有空白就原样保留（全选 == 原文）；
                // 若是未选中的内容，用一个空格隔开，不能把没选的字也拼进来。
                val gap = line.text.substring(ranges[rangeIndex - 1].second, start)
                if (gap.isBlank()) out.append(gap) else out.append(' ')
            }
            out.append(line.text, start, end)
        }
    }
    return out.toString()
}

@Composable
private fun ResultActions(
    imageMode: Boolean,
    editing: Boolean,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onToggleEdit: () -> Unit,
    pendingEdit: Boolean,
    onSelectAll: () -> Unit,
    allSelected: Boolean,
    onImageAction: (OcrImageAction) -> Unit,
) {
    // 动作全部收进底部一条：图标即操作；独立 tonal 容器替代原来的分隔线。
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageMode) {
                ResultAction(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.ocr_result_image_copy),
                    onClick = { onImageAction(OcrImageAction.Copy) },
                )
                ResultAction(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.ocr_result_image_share),
                    onClick = { onImageAction(OcrImageAction.Share) },
                )
                ResultAction(
                    icon = Icons.Default.SaveAlt,
                    label = stringResource(R.string.ocr_result_image_save),
                    onClick = { onImageAction(OcrImageAction.Save) },
                )
            } else {
                // 编辑态没有分词结果，全选无意义。
                if (!editing) {
                    ResultAction(
                        icon = Icons.Default.SelectAll,
                        label = stringResource(
                            if (allSelected) {
                                R.string.ocr_result_select_none
                            } else {
                                R.string.ocr_result_select_all
                            },
                        ),
                        onClick = onSelectAll,
                    )
                }
                ResultAction(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.ocr_result_copy),
                    onClick = onCopy,
                )
                ResultAction(
                    icon = Icons.Default.Search,
                    label = stringResource(R.string.ocr_result_search),
                    onClick = onSearch,
                )
                ResultAction(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.ocr_result_share),
                    onClick = onShare,
                )
                EditAction(
                    editing = editing,
                    pending = pendingEdit,
                    label = stringResource(
                        if (editing) R.string.ocr_result_finish_edit else R.string.ocr_result_edit,
                    ),
                    onClick = onToggleEdit,
                )
            }
        }
    }
}

/**
 * 编辑/完成按钮。
 *
 * 收键盘期间（[pending]）换成小转圈并禁用：内容要等 IME 收完才切，
 * 不给出反馈就会像“点了没反应”，连点还会重复触发。
 */
@Composable
private fun EditAction(
    editing: Boolean,
    pending: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = !pending,
        modifier = Modifier.size(MinInteractiveSize),
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    fadeIn(tween(MotionSubtle)) togetherWith fadeOut(tween(MotionSubtle))
                },
                label = "ocr-edit-icon",
            ) { isEditing ->
                Icon(
                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // 图标按钮必须有 contentDescription；动作名同时作为无障碍标签。
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(MinInteractiveSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
        )
    }
}
