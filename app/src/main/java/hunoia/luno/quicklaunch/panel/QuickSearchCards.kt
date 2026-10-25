package hunoia.luno.quicklaunch.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.compose.AsyncImage
import hunoia.luno.R
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SETTINGS
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.SRC_TOGGLE
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchIcon
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.quicklaunch.query.SOURCE_CALC
import hunoia.luno.quicklaunch.query.SOURCE_ENGINE
import hunoia.luno.quicklaunch.query.SOURCE_URL
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.settings.search.sourceTitle
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SmallShape
import androidx.compose.material.icons.filled.PushPin

/** 卡内条数上限：排序靠前的胜出（Pin 已被置顶排在桶首）。 */
private const val CardItemLimit = 8

/** 卡片间的纵向间距：比平铺的行间 SegmentedGap 明显大，才读得出"一张卡"。 */
private val GroupedCardGap = 10.dp

/** 有图标的结果用竖版磁贴（图标上名字下）；其余源与历史用文本胶囊。 */
private val TileSources = setOf(
    SRC_APP, SRC_SHORTCUT, SRC_ACTION, SRC_SETTINGS, SRC_TOGGLE, SRC_CONTACT, SRC_MUSIC,
)

private fun sourceIcon(key: String): ImageVector = when (key) {
    SRC_APP -> Icons.Default.Apps
    SRC_SHORTCUT -> Icons.Default.Shortcut
    SRC_ACTION -> Icons.Default.PlayArrow
    SRC_SETTINGS -> Icons.Default.Settings
    SRC_CONTACT -> Icons.Default.Person
    SRC_MUSIC -> Icons.Default.MusicNote
    SRC_FILE -> Icons.Default.Description
    SRC_CALENDAR -> Icons.Default.DateRange
    SRC_SMS -> Icons.AutoMirrored.Filled.Message
    SRC_CALL -> Icons.Default.Call
    SRC_TOGGLE -> Icons.Default.Tune
    SRC_CLIP -> Icons.Default.ContentPaste
    else -> Icons.Default.Search
}

@Composable
private fun cardTitle(key: String): String = when (key) {
    SOURCE_CALC -> stringResource(R.string.search_card_calculator)
    SOURCE_URL -> stringResource(R.string.search_card_url)
    SOURCE_ENGINE -> stringResource(R.string.search_card_engine)
    else -> sourceTitle(key)
}

/**
 * 分桶：主点击是回填的结果（剪贴类）单独成桶，其余按 source 聚合；
 * 桶序 = 该源在排序结果里的首次出现位置（计算器置头、引擎垫底的排序语义自动保留）。
 * 桶内保持排序序（Pin 已被置顶），截到 [CardItemLimit]。
 */
private fun List<SearchResult>.bucketBySource(): List<Pair<String, List<SearchResult>>> {
    val order = mutableListOf<String>()
    val buckets = linkedMapOf<String, MutableList<SearchResult>>()
    for (result in this) {
        val key = if (result.primary is SearchAction.SetQuery) SRC_CLIP else result.source
        buckets.getOrPut(key) {
            order.add(key)
            mutableListOf()
        }.add(result)
    }
    return order.map { key -> key to buckets.getValue(key).take(CardItemLimit) }
}

/**
 * 大卡片风格的搜索结果列表：历史卡贴底（点即回填，优先级不变），随后剪贴卡与各内容源卡，
 * 引擎卡按排序语义自然垫顶。菜单/长按/回填交互与平铺完全同一套回调。
 */
@Composable
internal fun QuickSearchGroupedCards(
    results: List<SearchResult>,
    matches: List<String>,
    queryText: String,
    menuResult: SearchResult?,
    resultPinnedKeys: Set<String>,
    pinnedSet: Set<String>,
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
    val buckets = remember(results) { results.bucketBySource() }
    val listState = rememberLazyListState()
    // 受控滚动与平铺同口径：内容签名变化即钉回第 0 项（reverseLayout 下 = 最贴输入框）。
    val contentSig = signatureOf(
        results.firstOrNull()?.key, results.lastOrNull()?.key,
        buckets.size * 31 + matches.size,
    )
    LaunchedEffect(contentSig) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter)
            .listFade(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GroupedCardGap, Alignment.Bottom),
        reverseLayout = true,
    ) {
        if (matches.isNotEmpty()) {
            item(key = "card-history") {
                GroupCard(title = stringResource(R.string.search_card_history), icon = Icons.Default.Search) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(matches, key = { _, h -> h }) { _, h ->
                            HistoryChip(
                                text = h,
                                onClick = {
                                    onQueryChange(
                                        backfillText(h).let { TextFieldValue(it, TextRange(it.length)) },
                                    )
                                },
                                onLongClick = { onDeleteHistory(h) },
                            )
                        }
                    }
                }
            }
        }
        buckets.forEach { (source, items) ->
            item(key = "card-$source") {
                ResultGroupCard(
                    source = source,
                    results = items,
                    queryText = queryText,
                    menuResult = menuResult,
                    resultPinnedKeys = resultPinnedKeys,
                    pinnedSet = pinnedSet,
                    onQueryChange = onQueryChange,
                    onResultClick = onResultClick,
                    onMenuOpen = onMenuOpen,
                    onMenuDismiss = onMenuDismiss,
                    onResultAction = onResultAction,
                    onToggleResultPin = onToggleResultPin,
                    onTogglePin = onTogglePin,
                )
            }
        }
    }
}

/** 单张结果卡：按源决定磁贴/胶囊形态；剪贴桶全是回填条目（迷你图钉收进条目尾）。 */
@Composable
private fun ResultGroupCard(
    source: String,
    results: List<SearchResult>,
    queryText: String,
    menuResult: SearchResult?,
    resultPinnedKeys: Set<String>,
    pinnedSet: Set<String>,
    onQueryChange: (TextFieldValue) -> Unit,
    onResultClick: (SearchResult, String) -> Unit,
    onMenuOpen: (SearchResult) -> Unit,
    onMenuDismiss: () -> Unit,
    onResultAction: (SearchAction, String) -> Unit,
    onToggleResultPin: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    GroupCard(title = cardTitle(source), icon = sourceIcon(source)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(results, key = { _, r -> r.key }) { _, result ->
                Box {
                    val setQuery = (result.primary as? SearchAction.SetQuery)?.text
                    if (setQuery != null) {
                        ClipChip(
                            text = setQuery,
                            pinned = setQuery in pinnedSet,
                            onTogglePin = { onTogglePin(setQuery) },
                            onClick = {
                                onQueryChange(
                                    backfillText(setQuery).let { TextFieldValue(it, TextRange(it.length)) },
                                )
                            },
                            onLongClick = { onMenuOpen(result) },
                        )
                    } else if (source in TileSources) {
                        ResultTile(
                            result = result,
                            onClick = { onResultClick(result, queryText.trim()) },
                            onLongClick = { onMenuOpen(result) },
                        )
                    } else {
                        ResultChip(
                            result = result,
                            onClick = { onResultClick(result, queryText.trim()) },
                            onLongClick = { onMenuOpen(result) },
                        )
                    }
                    // 长按条目弹的菜单沿用 ResultActionMenu；锚在条目上，内容与平铺一致。
                    ResultActionMenu(
                        result = result,
                        query = queryText.trim(),
                        pinned = result.key in resultPinnedKeys,
                        expanded = menuResult?.key == result.key,
                        onDismiss = onMenuDismiss,
                        onAction = onResultAction,
                        onTogglePin = onToggleResultPin,
                    )
                }
            }
        }
    }
}

/** 卡片骨架：整块半透明底（透出系统模糊）+ 类目标题行 + 横向内容槽。 */
@Composable
private fun GroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ContainerRadius))
            .background(searchRowContainer()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

/** 历史条目（点击回填、长按删历史）。 */
@Composable
private fun HistoryChip(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

/** 剪贴条目：点击回填，尾部迷你图钉（卡片化后剪贴行没有行尾按钮，Pin 入口收进这里）。 */
@Composable
private fun ClipChip(
    text: String,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onTogglePin),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = stringResource(
                    if (pinned) R.string.quick_search_unpin else R.string.quick_search_pin,
                ),
                modifier = Modifier.size(14.dp),
                tint = if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

/** 结果胶囊：图标 + 单行文字（无图标源用占位搜索图标）。 */
@Composable
private fun ResultChip(
    result: SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ResultIconView(result = result, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = result.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

/** 结果磁贴：图标上、名字下（两行省略），横向滚动的主形态。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultTile(
    result: SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ResultIconView(result = result, size = 40.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 结果图标的统一渲染：位图/应用图标（缓存 + Coil 兜底）/请求模型/矢量/文字，与行组件同口径。 */
@Composable
private fun ResultIconView(
    result: SearchResult,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    when (val icon = result.icon) {
        is SearchIcon.Vector -> Icon(
            imageVector = icon.image,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
        is SearchIcon.Bitmap -> Image(
            bitmap = icon.image,
            contentDescription = null,
            modifier = modifier.size(size).clip(SmallShape),
            contentScale = ContentScale.Crop,
        )
        is SearchIcon.AppIcon -> {
            val sizePx = with(LocalDensity.current) { size.toPx().toInt() }
            val cached = icon.bitmap ?: SearchIconCache.get(icon.packageName, sizePx)
            if (cached != null) {
                Image(
                    bitmap = cached,
                    contentDescription = null,
                    modifier = modifier.size(size).clip(SmallShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AsyncImage(
                    model = QuickLaunchFacade.appIconRequest(context, icon.packageName, sizePx),
                    contentDescription = null,
                    modifier = modifier.size(size).clip(SmallShape),
                    imageLoader = context.imageLoader,
                    contentScale = ContentScale.Crop,
                )
            }
        }
        is SearchIcon.Request -> AsyncImage(
            model = icon.model,
            contentDescription = null,
            modifier = modifier.size(size).clip(SmallShape),
            imageLoader = context.imageLoader,
            contentScale = ContentScale.Crop,
        )
        is SearchIcon.Text -> TextIconGlyph(text = icon.text, size = size, color = tint)
        SearchIcon.None -> Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
    }
}
