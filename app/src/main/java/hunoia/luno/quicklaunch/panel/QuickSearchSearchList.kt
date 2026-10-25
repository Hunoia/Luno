package hunoia.luno.quicklaunch.panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap

/**
 * 搜索态列表：匹配的历史全部参展（点即回填，贴胶囊），结果排其上；
 * 单列分段缝合。反转布局：结果贴底部输入框。
 */
@Composable
internal fun QuickSearchSearchList(
    results: List<SearchResult>,
    matches: List<String>,
    pinnedSet: Set<String>,
    queryText: String,
    menuResult: SearchResult?,
    resultPinnedKeys: Set<String>,
    onQueryChange: (TextFieldValue) -> Unit,
    onResultClick: (SearchResult, String) -> Unit,
    onMenuOpen: (SearchResult) -> Unit,
    onMenuDismiss: () -> Unit,
    onResultAction: (SearchAction, String) -> Unit,
    onToggleResultPin: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = matches.size + results.size
    // 反转列表内容超高时会把首项顶出可视区：受控滚动，内容一变钉回第 0 项。
    // 触发用内容签名而非 total：按数量滚会拽回用户上滑看的结果。
    val listState = rememberLazyListState()
    val contentSig = signatureOf(
        results.firstOrNull()?.key, results.lastOrNull()?.key,
        total * 31 + matches.size,
    )
    LaunchedEffect(contentSig) { listState.scrollToItem(0) }
    // key 只由内容决定、不带 idx：带 idx 时重排会让整列 key 失效、全部重建，
    // 打字时肉眼可见闪。重复项由 stableKeys 补 ~n 保唯一。
    val resultKeys = remember(results) { stableKeys(results.map { it.key }) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = PageGutter).listFade(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
        // 反转只管顺序不管小内容锚定，显式 Bottom 后单条目也必贴胶囊
        verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
        reverseLayout = true,
    ) {
        itemsIndexed(matches, key = { _, h -> "m#$h" }) { pos, h ->
            HistoryRow(
                text = h,
                shape = segmentedShape(total - 1 - pos, total),
                onClick = {
                    onQueryChange(backfillText(h).let { TextFieldValue(it, TextRange(it.length)) })
                },
                onLongClick = { onDeleteHistory(h) },
            )
        }
            itemsIndexed(results, key = { idx, _ -> "r#${resultKeys[idx]}" }) { index, item ->
                val pos = matches.size + index
                val shape = segmentedShape(total - 1 - pos, total)
                val setQuery = (item.primary as? SearchAction.SetQuery)?.text
                val menu: @Composable () -> Unit = {
                    ResultActionMenu(
                        result = item,
                        query = queryText.trim(),
                        pinned = item.key in resultPinnedKeys,
                        expanded = menuResult?.key == item.key,
                        onDismiss = onMenuDismiss,
                        onAction = onResultAction,
                        onTogglePin = onToggleResultPin,
                    )
                }
                if (setQuery != null) {
                    ClipboardRow(
                        text = setQuery,
                        shape = shape,
                        onClick = {
                            onQueryChange(
                                backfillText(setQuery).let { TextFieldValue(it, TextRange(it.length)) },
                            )
                        },
                        onLongClick = { onMenuOpen(item) },
                        pinned = setQuery in pinnedSet,
                        onTogglePin = { onTogglePin(setQuery) },
                        menu = menu,
                    )
                } else {
                    ResultRow(
                        result = item,
                        shape = shape,
                        onClick = { onResultClick(item, queryText.trim()) },
                        onLongClick = { onMenuOpen(item) },
                        menu = menu,
                    )
                }
            }
    }
}
