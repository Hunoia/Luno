package hunoia.luno.ui.ocr

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.capability.ocr.OcrLine
import hunoia.luno.capability.ocr.OcrTokenizer
import hunoia.luno.capability.ocr.OcrWord
import hunoia.luno.quicklaunch.QuickSearchActivity
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.ExtraLargeShape
import kotlin.math.abs

/**
 * 框选 OCR 的结果页：透明 Activity + 底部卡片。
 *
 * 住在 `:service`，与 `ScreenPickOverlay` / OCR 引擎同进程；结果通过 [OcrResultHandoff]
 * 交接，不把大文本塞进 Intent。Activity 只做展示、选择与动作发起，不跑 OCR。
 */
class OcrResultActivity : ComponentActivity() {

    private var text by mutableStateOf("")

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
                OcrResultScreen(
                    text = text,
                    onTextChange = { text = it },
                    onClose = { finish() },
                    onCopy = { value ->
                        if (value.isBlank()) {
                            showToast(R.string.ocr_result_empty)
                        } else {
                            copyText(this, "ocr_result", value)
                            showToast(R.string.ocr_result_copied)
                        }
                    },
                    onSearch = { value ->
                        if (value.isBlank()) {
                            showToast(R.string.ocr_result_empty)
                        } else if (QuickSearchActivity.start(this, value)) {
                            finish()
                        } else {
                            showToast(R.string.ocr_result_failed)
                        }
                    },
                    onShare = { value ->
                        if (value.isBlank()) {
                            showToast(R.string.ocr_result_empty)
                        } else if (share(value)) {
                            finish()
                        } else {
                            showToast(R.string.ocr_result_failed)
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!applyIntent(intent)) finish()
    }

    private fun applyIntent(intent: Intent?): Boolean {
        val id = intent?.getLongExtra(EXTRA_SESSION, -1L) ?: -1L
        val next = OcrResultHandoff.take(id) ?: return false
        text = next
        return true
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
         * 从识屏浮窗拉起结果页。返回 false = 启动失败，调用方回退旧面板。
         *
         * BAL：应用有 SYSTEM_ALERT_WINDOW 且当前有可见 overlay；项目内已有同样路径
         *（QuickSearchActivity / ActionSelectHostActivity）。真机被拦时由调用方兜底。
         */
        fun start(context: Context, text: String): Boolean {
            if (text.isBlank()) return false
            val id = OcrResultHandoff.put(text)
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

private data class WordSelection(val line: Int, val word: OcrWord)

@Composable
private fun OcrResultScreen(
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var segmented by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    // 进入编辑即聚焦并弹出 IME，不用用户再点一次输入框。
    val editorFocus = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) editorFocus.requestFocus()
    }
    // 编辑时不做分词：避免每敲一个字都在主线程跑一遍 ICU；切回只读时再算。
    val lines = remember(text, editing) {
        if (editing) emptyList() else OcrTokenizer.lines(text)
    }
    val selectedText = selection?.word?.text ?: text

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 弹层高度内容驱动（ui-guidelines §4.4）：短文本不撑满屏，长文本封顶后内部滚动。
            val cardMaxHeight = maxHeight * 0.85f
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .heightIn(min = 200.dp, max = cardMaxHeight)
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
                    ResultHeader(
                        editing = editing,
                        segmented = segmented,
                        onToggleEdit = {
                            editing = !editing
                            selection = null
                        },
                        onToggleSegment = {
                            segmented = !segmented
                            selection = null
                        },
                        onClose = onClose,
                    )

                    if (editing) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                onTextChange(it)
                                selection = null
                            },
                            // fill=false：内容短时按内容高度，长时最多占满剩余空间。
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .weight(1f, fill = false)
                                .focusRequester(editorFocus),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text(stringResource(R.string.ocr_result_placeholder)) },
                        )
                    } else {
                        LazyColumn(
                            // fill=false：短文本卡片随内容收缩；长文本封顶后滚动，动作区不被挤走。
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(lines) { index, line ->
                                if (segmented) {
                                    WordChips(
                                        line = line,
                                        selected = selection?.takeIf { it.line == index }?.word,
                                        onSelect = { word ->
                                            selection = WordSelection(index, word)
                                        },
                                    )
                                } else {
                                    SelectableLine(
                                        line = line,
                                        selected = selection?.takeIf { it.line == index }?.word,
                                        onSelect = { word ->
                                            selection = if (word == null) null else WordSelection(index, word)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 长文本滚动区与动作区之间的分隔；避免文字看起来被“切掉”。
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ResultActions(
                        onCopy = { onCopy(selectedText) },
                        onSearch = { onSearch(selectedText) },
                        onShare = { onShare(selectedText) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(
    editing: Boolean,
    segmented: Boolean,
    onToggleEdit: () -> Unit,
    onToggleSegment: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ocr_result_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (!editing) {
                IconButton(onClick = onToggleSegment) {
                    Icon(
                        imageVector = if (segmented) {
                            Icons.Default.TextFields
                        } else {
                            Icons.AutoMirrored.Filled.FormatListBulleted
                        },
                        contentDescription = stringResource(
                            if (segmented) R.string.ocr_result_show_text else R.string.ocr_result_show_words,
                        ),
                    )
                }
            }
            IconButton(onClick = onToggleEdit) {
                Icon(
                    imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = stringResource(
                        if (editing) R.string.ocr_result_finish_edit else R.string.ocr_result_edit,
                    ),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.ocr_result_close),
                )
            }
        }
        // 提示单独占一行，不和图标挤在同一行被截断。
        Text(
            text = stringResource(
                if (editing) R.string.ocr_result_edit_hint else R.string.ocr_result_hint,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(
    line: OcrLine,
    selected: OcrWord?,
    onSelect: (OcrWord) -> Unit,
) {
    if (line.words.isEmpty()) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        line.words.forEach { word ->
            Surface(
                onClick = { onSelect(word) },
                shape = RoundedCornerShape(8.dp),
                color = if (word == selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (word == selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                // 视觉可以小，命中区必须 ≥ 48dp（ui-guidelines §1.5 / §4.6）。
                modifier = Modifier.heightIn(min = MinInteractiveSize),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = word.text,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableLine(
    line: OcrLine,
    selected: OcrWord?,
    onSelect: (OcrWord?) -> Unit,
) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    BasicText(
        text = line.text,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val textLayout = layout ?: return@drawBehind
                val word = selected ?: return@drawBehind
                if (word.start >= 0 && word.end <= textLayout.layoutInput.text.length) {
                    drawPath(
                        path = textLayout.getPathForRange(word.start, word.end),
                        color = highlight,
                    )
                }
            }
            .pointerInput(line.text, line.words) {
                detectTapGestures(
                    onTap = { onSelect(null) },
                    onDoubleTap = { position ->
                        layout?.let { onSelect(wordAt(it, position, line.words)) }
                    },
                )
            }
            .pointerInput(line.text, line.words) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        layout?.let { onSelect(wordAt(it, position, line.words)) }
                    },
                    onDrag = { change, _ ->
                        layout?.let { onSelect(wordAt(it, change.position, line.words)) }
                    },
                )
            },
    )
}

private fun wordAt(
    layout: TextLayoutResult,
    position: Offset,
    words: List<OcrWord>,
): OcrWord? {
    if (words.isEmpty()) return null
    val offset = layout
        .getOffsetForPosition(position)
        .coerceIn(0, layout.layoutInput.text.length)
    return words.firstOrNull { offset in it.start until it.end }
        ?: words.minByOrNull { minOf(abs(it.start - offset), abs(it.end - offset)) }
}

@Composable
private fun ResultActions(
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultAction(
            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            label = stringResource(R.string.ocr_result_copy),
            onClick = onCopy,
            modifier = Modifier.weight(1f),
        )
        ResultAction(
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = stringResource(R.string.ocr_result_search),
            onClick = onSearch,
            modifier = Modifier.weight(1f),
        )
        ResultAction(
            icon = { Icon(Icons.Default.Share, contentDescription = null) },
            label = stringResource(R.string.ocr_result_share),
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 动作名是语义关键信息，不能靠省略号截断：图标在上、文字在下，长文案换行而非截断。
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinInteractiveSize),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
