package hunoia.luno.quicklaunch.panel
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import hunoia.luno.quicklaunch.R
import hunoia.luno.config.model.MAX_QUERY_CHARS
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.badgeText
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.toSearchAction
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter

/**
 * 引擎条：有查询词时给快捷换引擎入口；存在图片引擎时空态也显示（图片引擎不依赖查询词）。
 * 默认引擎 selected 高亮，点即用该引擎执行当前查询。
 */
@Composable
internal fun QuickSearchEngineBar(
    engines: List<SearchEngine>,
    queryText: String,
    onEngineAction: (SearchAction, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImageEngine = engines.any {
        it.effectiveLaunch() is SearchLaunch.ShareImage
    }
    if ((queryText.isNotBlank() || hasImageEngine) && engines.size > 1) {
        val searchQuery = queryText.trim()
        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            itemsIndexed(engines, key = { _, engine -> engine.id }) { index, engine ->
                val launch = engine.effectiveLaunch()
                val imageEngine = launch is SearchLaunch.ShareImage
                FilterChip(
                    // 主引擎 = 列表第一位，不再单独选默认。
                    selected = index == 0,
                    enabled = searchQuery.isNotBlank() || imageEngine,
                    onClick = { onEngineAction(launch.toSearchAction(searchQuery), searchQuery) },
                    // 引擎徽标与半透明底色：与下方搜索框/结果行的玻璃质感同一套。
                    leadingIcon = {
                        TextIconGlyph(
                            text = engine.badgeText,
                            size = 16.dp,
                        )
                    },
                    label = { Text(engine.name) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f),
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    ),
                    border = null,
                )
            }
        }
    }
}

/**
 * 搜索框：Capsule 输入 + 右侧动作（有词 = 清空 + 确认 Check，空词 = 语音）。
 * 确认目标与键盘动作由根组合给（confirmFirst 读实时 query，不能在 remember 里捕获组合期快照）。
 * [asciiEnter] 开（默认）= 英文键盘且回车直达；关 = 中文输入法可用，确认走 Check。
 */
@Composable
internal fun QuickSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit,
    keyboardActions: KeyboardActions,
    focusRequester: FocusRequester,
    asciiEnter: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 语音输入：系统识别器回填 query；没识别器就不显示按钮（不报错）。
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS,
            )?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { heard ->
                onValueChange(TextFieldValue(heard, TextRange(heard.length)))
            }
        }
    }
    val voiceAvailable = remember(context) {
        Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(
            context.packageManager,
        ) != null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.text.length <= MAX_QUERY_CHARS) onValueChange(it) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter)
            .padding(bottom = CardInnerSpacing)
            .focusRequester(focusRequester),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.text.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 框内 ×：只清空 query（ui-guidelines §8 语义）。
                    LunoIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.quick_search_clear),
                        onClick = { onValueChange(TextFieldValue("")) },
                    )
                    // 注意列表是反转的：firstOrNull 是视觉底部（离胶囊最近），与显示顺序相反。
                    LunoIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = stringResource(R.string.confirm),
                        onClick = onConfirm,
                    )
                }
            } else if (voiceAvailable) {
                LunoIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.quick_search_voice),
                    onClick = {
                        voiceLauncher.launch(
                            Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_PROMPT,
                                    value.text,
                                )
                            },
                        )
                    },
                )
            }
        },
        singleLine = true,
        shape = CircleShape,
        // 开（默认）= Ascii：锁英文键盘换「回车即确认」；
        // 关 = Text：中文输入法可用，回车交给输入法、确认走右侧 Check。见设置「英文键盘」开关。
        keyboardOptions = KeyboardOptions(
            keyboardType = if (asciiEnter) KeyboardType.Ascii else KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            // 半透明底色：透出窗口背后的系统模糊，不支持的设备直接呈现半透明。
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
            focusedBorderColor = ComposeColor.Transparent,
            unfocusedBorderColor = ComposeColor.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
