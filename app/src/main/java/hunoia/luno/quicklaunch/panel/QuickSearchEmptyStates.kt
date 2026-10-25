package hunoia.luno.quicklaunch.panel
import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.BLANK_RECENT_LIMIT
import hunoia.luno.config.model.MIN_HISTORY_CHARS
import hunoia.luno.config.model.QuickSearchSettings
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
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import hunoia.luno.quicklaunch.query.toSearchResult
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.settings.search.sourceTitle
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.permission.PermissionState

/**
 * 空输入态：剪贴板历史最贴胶囊（首条=当前剪贴），其次搜索历史（最近 3）、常用应用，
 * 各来源缺权限时给授权入口行（点申请，永久拒绝跳设置页）。锁屏只显剪贴占位行。
 */
@Composable
internal fun QuickSearchEmptyList(
    settings: QuickSearchSettings,
    clipboardText: String?,
    locked: Boolean,
    used: List<SearchResult>,
    queryText: String,
    menuResult: SearchResult?,
    resultPinnedKeys: Set<String>,
    grants: QuickSearchMediaGrants,
    contactsPerm: PermissionState,
    calendarPerm: PermissionState,
    smsPerm: PermissionState,
    callsPerm: PermissionState,
    mediaLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    permCtx: Context,
    pinnedSet: Set<String>,
    onQueryChange: (TextFieldValue) -> Unit,
    onResultClick: (SearchResult, String) -> Unit,
    onMenuOpen: (SearchResult) -> Unit,
    onMenuDismiss: () -> Unit,
    onResultAction: (SearchAction, String) -> Unit,
    onToggleResultPin: (String, String) -> Unit,
    onShowEngines: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = settings.recentQueries
        .filter { it.trim().length >= MIN_HISTORY_CHARS }
        .take(BLANK_RECENT_LIMIT)
    val clips = if (settings.searchClipboard && !locked) {
        // 当前剪贴优先（焦点刚读到的），后面跟历史去重，空页只展 3 条。
        (listOfNotNull(clipboardText) + settings.clipHistory).distinct().take(3)
    } else {
        emptyList()
    }
    // 锁屏占位：剪贴内容不展，只留一行提示。
    val showClipPlaceholder = settings.searchClipboard && locked
    val clipPlaceholderCount = if (showClipPlaceholder) 1 else 0
    val clipCount = clips.size
    // 锁屏占位占 clips 段的首位：后续 pos 统一按槽位（clips+占位）偏移。
    val clipSlotCount = clipCount + clipPlaceholderCount
    // 联系人未授权：顶部给一行授权入口（点申请，永久拒绝跳设置页）。
    val showGrant = settings.searchContacts && !contactsPerm.isGranted
    val grantCount = if (showGrant) 1 else 0
    // P3：媒体缺权限给一行（一次要三个），all-files 没开给一行（跳设置页）。
    val showMediaGrant = (settings.searchMusic || settings.searchFiles) &&
        !(grants.audio && grants.images && grants.video)
    val showFilesGrant = settings.searchFiles && !grants.files
    val mediaGrantCount = if (showMediaGrant) 1 else 0
    val filesGrantCount = if (showFilesGrant) 1 else 0
    // 日历/短信/通话：各给一行授权入口（不合并，用户定的）。
    val showCalendarGrant = settings.searchCalendar && !calendarPerm.isGranted
    val showSmsGrant = settings.searchSms && !smsPerm.isGranted
    val showCallsGrant = settings.searchCalls && !callsPerm.isGranted
    val calendarGrantCount = if (showCalendarGrant) 1 else 0
    val smsGrantCount = if (showSmsGrant) 1 else 0
    val callsGrantCount = if (showCallsGrant) 1 else 0
    if (history.isNotEmpty() || clips.isNotEmpty() || showClipPlaceholder || used.isNotEmpty() ||
        showGrant || showMediaGrant || showFilesGrant ||
        showCalendarGrant || showSmsGrant || showCallsGrant
    ) {
        // 整列共用一套连续圆角：剪贴板与上方内容之间用 ConnectionRadius 连在一起，
        // 只有整列最底/最顶才用 ContainerRadius。
        val contentCount = history.size + used.size +
            grantCount + mediaGrantCount + filesGrantCount +
            calendarGrantCount + smsGrantCount + callsGrantCount
        val total = clipSlotCount + contentCount
        val blankListState = rememberLazyListState()
        // 内容签名同搜索态：数量变不再抢滚动，只有内容身份变才钉回首项。
        val blankSig = signatureOf(
            listOf(clips.firstOrNull(), history.firstOrNull(), used.firstOrNull()),
            listOf(clips.lastOrNull(), history.lastOrNull(), used.lastOrNull()),
            total,
        )
        LaunchedEffect(blankSig) { blankListState.scrollToItem(0) }
        val usedKeys = remember(used) {
            stableKeys(used.map { it.key })
        }
        LazyColumn(
            state = blankListState,
            modifier = modifier.fillMaxWidth().padding(horizontal = PageGutter).listFade(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
            reverseLayout = true,
        ) {
            // 剪贴板历史最贴胶囊（首条=当前剪贴），其次搜索历史，最后常用；
            // 锁屏时 clips 为空，占位行顶在同一槽位。
            if (showClipPlaceholder) {
                item(key = "c-locked") {
                    HistoryRow(
                        text = stringResource(R.string.quick_search_clipboard_locked),
                        shape = segmentedShape(total - 1, total),
                        icon = Icons.Default.Lock,
                        onClick = {},
                    )
                }
            }
            itemsIndexed(clips, key = { _, c -> "c#$c" }) { pos, clipText ->
                val clipResult = QuickSearchResult.ClipEntry(clipText).toSearchResult()
                ClipboardRow(
                    text = clipText,
                    shape = segmentedShape(total - 1 - pos, total),
                    onClick = {
                        onQueryChange(backfillText(clipText).let { TextFieldValue(it, TextRange(it.length)) })
                    },
                    onLongClick = { onMenuOpen(clipResult) },
                    pinned = clipText in pinnedSet,
                    onTogglePin = { onTogglePin(clipText) },
                    menu = {
                        ResultActionMenu(
                            result = clipResult,
                            query = queryText.trim(),
                            pinned = clipResult.key in resultPinnedKeys,
                            expanded = menuResult?.key == clipResult.key,
                            onDismiss = onMenuDismiss,
                            onAction = onResultAction,
                            onTogglePin = onToggleResultPin,
                            onShowEngines = onShowEngines,
                        )
                    },
                )
            }
            itemsIndexed(history, key = { _, h -> "h#$h" }) { pos, h ->
                HistoryRow(
                    text = h,
                    // 历史段上方没有 clip，但下方有：算整列视觉下标时必须带上
                    // clipSlotCount，否则历史最后一行会误拿整列底部的大圆角。
                    shape = segmentedShape(total - 1 - clipSlotCount - pos, total),
                    onClick = {
                        onQueryChange(backfillText(h).let { TextFieldValue(it, TextRange(it.length)) })
                    },
                    onLongClick = { onDeleteHistory(h) },
                )
            }
                itemsIndexed(used, key = { idx, _ -> usedKeys[idx] }) { idx, app ->
                    val pos = clipSlotCount + history.size + idx
                    ResultRow(
                        result = app,
                        shape = segmentedShape(total - 1 - pos, total),
                        onClick = { onResultClick(app, "") },
                        onLongClick = { onMenuOpen(app) },
                        menu = {
                            ResultActionMenu(
                                result = app,
                                query = queryText.trim(),
                                pinned = app.key in resultPinnedKeys,
                                expanded = menuResult?.key == app.key,
                                onDismiss = onMenuDismiss,
                                onAction = onResultAction,
                                onTogglePin = onToggleResultPin,
                                onShowEngines = onShowEngines,
                            )
                        },
                    )
                }
            if (showGrant) {
                item(key = "grant-contacts") {
                    val pos = clipSlotCount + history.size + used.size
                    HistoryRow(
                        text = stringResource(R.string.quick_search_grant_contacts),
                        shape = segmentedShape(total - 1 - pos, total),
                        icon = Icons.Default.Person,
                        onClick = {
                            if (contactsPerm.deniedForever) onOpenAppSettings()
                            else contactsPerm.launchPermissionRequest()
                        },
                    )
                }
            }
            if (showMediaGrant) {
                item(key = "grant-media") {
                    val pos = clipSlotCount + history.size + used.size + grantCount
                    HistoryRow(
                        text = stringResource(R.string.quick_search_grant_media),
                        shape = segmentedShape(total - 1 - pos, total),
                        icon = Icons.Default.MusicNote,
                        onClick = {
                            if (grants.mediaDeniedForever) {
                                onOpenAppSettings()
                            } else {
                                mediaLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_MEDIA_AUDIO,
                                        android.Manifest.permission.READ_MEDIA_IMAGES,
                                        android.Manifest.permission.READ_MEDIA_VIDEO,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            if (showFilesGrant) {
                item(key = "grant-files") {
                    val pos = clipSlotCount + history.size + used.size +
                        grantCount + mediaGrantCount
                    HistoryRow(
                        text = stringResource(R.string.quick_search_grant_files),
                        shape = segmentedShape(total - 1 - pos, total),
                        icon = Icons.Default.Description,
                        onClick = {
                            runCatching {
                                permCtx.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        android.net.Uri.parse("package:${permCtx.packageName}"),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            if (showCalendarGrant) {
                item(key = "grant-calendar") {
                    val pos = clipSlotCount + history.size + used.size +
                        grantCount + mediaGrantCount + filesGrantCount
                    HistoryRow(
                        text = stringResource(R.string.quick_search_grant_calendar),
                        shape = segmentedShape(total - 1 - pos, total),
                        icon = Icons.Default.DateRange,
                        onClick = {
                            if (calendarPerm.deniedForever) onOpenAppSettings()
                            else calendarPerm.launchPermissionRequest()
                        },
                    )
                }
            }
            if (showSmsGrant) {
                item(key = "grant-sms") {
                    val pos = clipSlotCount + history.size + used.size +
                        grantCount + mediaGrantCount + filesGrantCount + calendarGrantCount
                    HistoryRow(
                        text = stringResource(R.string.quick_search_grant_sms),
                        shape = segmentedShape(total - 1 - pos, total),
                        icon = Icons.AutoMirrored.Filled.Message,
                        onClick = {
                            if (smsPerm.deniedForever) onOpenAppSettings()
                            else smsPerm.launchPermissionRequest()
                        },
                    )
                }
            }
            if (showCallsGrant) {
                item(key = "grant-calls") {
                    val pos = clipSlotCount + history.size + used.size +
                        grantCount + mediaGrantCount + filesGrantCount +
                        calendarGrantCount + smsGrantCount
                    HistoryRow(
                        text = stringResource(R.string.quick_search_grant_calls),
                        shape = segmentedShape(total - 1 - pos, total),
                        icon = Icons.Default.Call,
                        onClick = {
                            if (callsPerm.deniedForever) onOpenAppSettings()
                            else callsPerm.launchPermissionRequest()
                        },
                    )
                }
            }
        }
    } else {
        Spacer(modifier)
    }
}

/**
 * 全空格输入态：标识提示（图标 来源名：标识），点即填入标识。
 * 空串走空态；删掉空格回去。标识关闭的来源/引擎跳过。
 */
@Composable
internal fun QuickSearchTriggerHints(
    settings: QuickSearchSettings,
    onQueryChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hintSources = remember(settings) {
        val icons = mapOf(
            SRC_APP to Icons.Default.Apps,
            SRC_SHORTCUT to Icons.Default.Shortcut,
            SRC_ACTION to Icons.Default.PlayArrow,
            SRC_SETTINGS to Icons.Default.Settings,
            SRC_CONTACT to Icons.Default.Person,
            SRC_MUSIC to Icons.Default.MusicNote,
            SRC_FILE to Icons.Default.Description,
            SRC_CALENDAR to Icons.Default.DateRange,
            SRC_SMS to Icons.AutoMirrored.Filled.Message,
            SRC_CALL to Icons.Default.Call,
            SRC_TOGGLE to Icons.Default.Tune,
            SRC_CLIP to Icons.Default.ContentPaste,
        )
        SearchSourceRegistry.all.mapNotNull { source ->
            val trigger = settings.effectiveTrigger(source.key)
            if (trigger.isBlank()) null else Triple(
                source.key,
                trigger,
                icons[source.key] ?: Icons.Default.Search,
            )
        }
    }
    val hintEngines = remember(settings) {
        settings.effectiveEngines()
            .filter { it.trigger.isNotBlank() }
            .map { it.name to it.trigger.trim() }
    }
    val hintTotal = hintSources.size + hintEngines.size
    if (hintTotal > 0) {
        val hintListState = rememberLazyListState()
        LaunchedEffect(hintTotal) { hintListState.scrollToItem(0) }
        LazyColumn(
            state = hintListState,
            modifier = modifier.fillMaxWidth().padding(horizontal = PageGutter).listFade(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
            reverseLayout = true,
        ) {
            itemsIndexed(hintSources, key = { _, h -> "th-${h.first}" }) { idx, h ->
                val trigger = h.second
                HistoryRow(
                    text = "${sourceTitle(h.first)}：$trigger",
                    shape = segmentedShape(hintTotal - 1 - idx, hintTotal),
                    icon = h.third,
                    onClick = {
                        onQueryChange(TextFieldValue(trigger, TextRange(trigger.length)))
                    },
                )
            }
            itemsIndexed(hintEngines, key = { _, e -> "te-${e.second}" }) { idx, e ->
                val trigger = e.second
                HistoryRow(
                    text = "${e.first}：$trigger",
                    shape = segmentedShape(hintTotal - 1 - hintSources.size - idx, hintTotal),
                    textIcon = e.first.firstOrNull()?.toString()?.let { TextIcon(it) },
                    onClick = {
                        onQueryChange(TextFieldValue(trigger, TextRange(trigger.length)))
                    },
                )
            }
        }
    } else {
        Spacer(modifier)
    }
}
