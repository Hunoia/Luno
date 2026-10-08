package hunoia.luno.quicklaunch.panel
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.mapLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.quicklaunch.icon.SearchIconCache
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.config.model.BLANK_RECENT_LIMIT
import hunoia.luno.config.model.MIN_HISTORY_CHARS
import hunoia.luno.config.model.MAX_CLIP_HISTORY
import hunoia.luno.config.model.MAX_QUERY_CHARS
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
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.quicklaunch.query.ensureCalendar
import hunoia.luno.quicklaunch.query.ensureCalls
import hunoia.luno.quicklaunch.query.ensureContacts
import hunoia.luno.quicklaunch.query.ensureMedia
import hunoia.luno.quicklaunch.query.ensureSms
import hunoia.luno.quicklaunch.query.isDirective
import hunoia.luno.quicklaunch.query.recentFiles
import hunoia.luno.quicklaunch.query.search
import hunoia.luno.quicklaunch.query.topUsed
import hunoia.luno.ui.permission.rememberPermissionState
import hunoia.luno.ui.settings.search.sourceTitle
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.row.segmentedShape
/** 搜索输入快照：snapshotFlow 跟踪块内读到的 State，值变化即重搜。 */
private data class SearchSnapshot(
    val text: String,
    val entries: List<hunoia.luno.action.model.NewActionLibraryEntry>,
    val launcher: hunoia.luno.config.model.QuickAppLauncherSettings,
    val search: hunoia.luno.config.model.QuickSearchSettings,
    val contactsTick: Int,
    val mediaTick: Int,
    val calendarTick: Int,
    val smsTick: Int,
    val callsTick: Int,
    val torchOn: Boolean,
)
@Composable
internal fun QuickSearchContent(
    index: QuickSearchIndex,
    clipboardText: String?,
    torchOn: Boolean,
    closing: Boolean,
    locked: Boolean,
    onClose: () -> Unit,
    onExitFinished: () -> Unit,
    onResult: (QuickSearchResult, String) -> Unit,
    onLongPress: (QuickSearchResult) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val libraryFlow = remember { hunoia.luno.config.ConfigProvider.newActionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = hunoia.luno.action.model.NewActionLibrarySettings())
    val entries = libraryState.entries
    val launcherFlow = remember { hunoia.luno.config.ConfigProvider.quickAppLauncherSettings }
    val launcherSettings by launcherFlow.collectAsState(initial = hunoia.luno.config.model.QuickAppLauncherSettings())
    val searchSettingsFlow = remember { hunoia.luno.config.ConfigProvider.quickSearchSettings }
    val searchSettings by searchSettingsFlow.collectAsState(initial = hunoia.luno.config.model.QuickSearchSettings())
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    // 联系人权限 + 后台加载：授权后 IO 线程一次性进内存，打字只查内存；
    // 加载完成 bump tick 触发 results 重算。
    val contactsPerm = rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    var contactsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(contactsPerm.isGranted, searchSettings.searchContacts) {
        if (contactsPerm.isGranted && searchSettings.searchContacts) {
            withContext(Dispatchers.IO) { index.ensureContacts() }
            contactsTick++
        }
    }
    // P3 媒体权限：三个一次要（部分授权也认，有啥搜啥）；all-files 不是运行时权限，
    // 单独查 isExternalStorageManager。resume tick 覆盖从设置页回来不重组的问题。
    var permTick by remember { mutableIntStateOf(0) }
    val mediaLifecycle = LocalLifecycleOwner.current
    DisposableEffect(mediaLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        mediaLifecycle.lifecycle.addObserver(observer)
        onDispose { mediaLifecycle.lifecycle.removeObserver(observer) }
    }
    val permCtx = LocalContext.current
    val act = permCtx as? Activity
    @Composable
    fun permGranted(p: String) = remember(permTick) {
        ContextCompat.checkSelfPermission(permCtx, p) == PackageManager.PERMISSION_GRANTED
    }
    val audioGranted = permGranted(android.Manifest.permission.READ_MEDIA_AUDIO)
    val imagesGranted = permGranted(android.Manifest.permission.READ_MEDIA_IMAGES)
    val videoGranted = permGranted(android.Manifest.permission.READ_MEDIA_VIDEO)
    val filesGranted = remember(permTick) { android.os.Environment.isExternalStorageManager() }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permTick++ }
    // 三个全灭且都不再弹系统框 = 永久拒绝，授权行改跳设置页。
    fun permRationale(p: String) =
        act?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, p) } ?: false
    val mediaDeniedForever = !audioGranted && !imagesGranted && !videoGranted &&
        !permRationale(android.Manifest.permission.READ_MEDIA_AUDIO) &&
        !permRationale(android.Manifest.permission.READ_MEDIA_IMAGES) &&
        !permRationale(android.Manifest.permission.READ_MEDIA_VIDEO)
    var mediaTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(
        audioGranted, imagesGranted, videoGranted, filesGranted,
        searchSettings.searchMusic, searchSettings.searchFiles,
    ) {
        if ((searchSettings.searchMusic || searchSettings.searchFiles) &&
            (audioGranted || imagesGranted || videoGranted || filesGranted)
        ) {
            withContext(Dispatchers.IO) { index.ensureMedia() }
            mediaTick++
        }
    }
    // 日历/短信/通话：单权限 + IO 一次加载 + tick 重算（联系人同款）。
    val calendarPerm = rememberPermissionState(android.Manifest.permission.READ_CALENDAR)
    val smsPerm = rememberPermissionState(android.Manifest.permission.READ_SMS)
    val callsPerm = rememberPermissionState(android.Manifest.permission.READ_CALL_LOG)
    var calendarTick by remember { mutableIntStateOf(0) }
    var smsTick by remember { mutableIntStateOf(0) }
    var callsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(calendarPerm.isGranted, searchSettings.searchCalendar) {
        if (calendarPerm.isGranted && searchSettings.searchCalendar) {
            withContext(Dispatchers.IO) { index.ensureCalendar() }
            calendarTick++
        }
    }
    LaunchedEffect(smsPerm.isGranted, searchSettings.searchSms) {
        if (smsPerm.isGranted && searchSettings.searchSms) {
            withContext(Dispatchers.IO) { index.ensureSms() }
            smsTick++
        }
    }
    LaunchedEffect(callsPerm.isGranted, searchSettings.searchCalls) {
        if (callsPerm.isGranted && searchSettings.searchCalls) {
            withContext(Dispatchers.IO) { index.ensureCalls() }
            callsTick++
        }
    }
    // 行图标边长（展示与 flow 预热同源）：flow 里 resolve 进同步缓存，
    // composition 只做同步读，首帧即有图。
    val iconPx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    val appCtx = LocalContext.current.applicationContext
    // 搜索搬出主线程：query 照常即时更新（字先上屏），结果流式回来；
    // mapLatest 自动取消过期搜索，快打不堆积。空串短路不进搜索。
    // snapshotFlow 跟踪块内读到的全部 State（query/设置/tick），值变化即重搜。
    val searchFlow = remember(
        entries, launcherSettings, searchSettings,
        contactsTick, mediaTick, calendarTick, smsTick, callsTick, torchOn, appCtx, iconPx,
    ) {
        snapshotFlow {
            SearchSnapshot(
                query.text, entries, launcherSettings, searchSettings,
                contactsTick, mediaTick, calendarTick, smsTick, callsTick, torchOn,
            )
        }.mapLatest { s ->
            if (s.text.isBlank()) {
                s.text to emptyList<QuickSearchResult>()
            } else {
                withContext(Dispatchers.Default) {
                    val out = index.search(s.text, s.entries, s.launcher, s.search, s.torchOn).also { res ->
                        // 发射前把 App/快捷方式行的图标 resolve 进同步缓存：
                        // UI 线程读到即画，无异步空隙，结果原子到达。已缓存的零开销，
                        // 只有新包才解码（自带 bitmap 的快捷方式跳过）。
                        val pkgs = HashSet<String>()
                        for (r in res) {
                            when (r) {
                                is QuickSearchResult.App -> pkgs.add(r.appInfo.packageName)
                                is QuickSearchResult.Shortcut ->
                                    if (r.shortcut.iconBitmap == null) pkgs.add(r.shortcut.packageName)
                                else -> {}
                            }
                        }
                        SearchIconCache.ensureAll(appCtx, pkgs, iconPx)
                    }
                    s.text to out
                }
            }
        }
    }
    // 原子包：结果永远带着它对应的 query。到达前 results 留旧值（后续字），
    // 首字无旧值则由分支继续显示空态——搜索分支只挂完整数据，不挂半成品。
    val packet by searchFlow.collectAsState(initial = "" to emptyList<QuickSearchResult>())
    val results = packet.second
    val settled = packet.first == query.text
    // 历史行共用：长按删单条（搜索态/空态同一语义）。
    val deleteHistory: (String) -> Unit = { h ->
        scope.launch {
            runCatching {
                hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                    it.copy(recentQueries = it.recentQueries.filterNot { q -> q == h })
                }
            }
        }
    }
    // 固定切换：pin 时从历史摘除（不占 50 名额），unpin 写回历史头部；固定无上限存全文。
    val togglePin: (String) -> Unit = { clip ->
        scope.launch {
            runCatching {
                hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                    if (clip in it.pinnedClips) {
                        it.copy(
                            pinnedClips = it.pinnedClips.filterNot { c -> c == clip },
                            clipHistory = (listOf(clip) + it.clipHistory)
                                .distinct()
                                .take(MAX_CLIP_HISTORY),
                        )
                    } else {
                        it.copy(
                            pinnedClips = listOf(clip) + it.pinnedClips.filterNot { c -> c == clip },
                            clipHistory = it.clipHistory.filterNot { c -> c == clip },
                        )
                    }
                }
            }
        }
    }
    val pinnedSet = remember(searchSettings.pinnedClips) { searchSettings.pinnedClips.toSet() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    // 语音输入：系统识别器回填 query；没识别器就不显示按钮（不报错）。
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS,
            )?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { heard ->
                query = TextFieldValue(heard, TextRange(heard.length))
            }
        }
    }
    val voiceAvailable = remember(permCtx) {
        Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(
            permCtx.packageManager,
        ) != null
    }
    // 进出场动画状态：进场从 0.92/0 起播到 1，closing 置位后播到 0.95/0。
    val panelScale = remember { Animatable(0.92f) }
    val panelAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { panelScale.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
        launch { panelAlpha.animateTo(1f, tween(300)) }
    }
    LaunchedEffect(closing) {
        // 退场纯淡出 200ms：缩放的重采样尾段全是糊的（字和图标都糊），删掉只留 alpha。
        // 键盘跟淡出并行收（之前等 finish 后才收，拖一条 200ms+ 的尾巴）。
        // 播完（animateTo 返回）才调 onExitFinished 拆窗：提前拆会掐断结尾，
        // 播完才拆又会晾纯 blur——精确交接点就是播完这一刻。
        if (closing) {
            keyboard?.hide()
            panelAlpha.animateTo(0f, tween(200))
            if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "fadeDone=${System.currentTimeMillis()}")
            onExitFinished()
        }
    }
    // 退场中吞掉行点击：requestClose 幂等但 dispatch 不是，守一层。
    val safeResult: (QuickSearchResult, String) -> Unit = { r, q -> if (!closing) onResult(r, q) }
    val safeLongPress: (QuickSearchResult) -> Unit = { if (!closing) onLongPress(it) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 澎湃风进出场：底部轴心缩放 + 淡入淡出。进场 0.92→1/300ms 减速，
            // 退场 1→0.95/200ms 加速；退场中点击全部吞掉（closing 门控）。
            .graphicsLayer {
                scaleX = panelScale.value
                scaleY = panelScale.value
                alpha = panelAlpha.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !closing,
                onClick = onClose,
            ),
    ) {
        // statusBarsPadding：内容从状态栏下方开始，不垫在透明状态栏图标后面
        //（白卡压白图标会导致图标看不清，跟 blur 无关，必须避让）。
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 分支按“数据完整”进：flow 发射 (queryText, results) 原子包，
            // 在途（packet.text != query.text）时不挂半成品——首字无旧数据就继续显示空态，
            // 到达后一次切完整搜索列表（和空→提示同性质，原子出现不闪）。
            // 有旧数据时继续显示旧列表，到达即整批换。
            // 锁屏时结果里的剪贴行全部滤掉（展示隐藏，引擎侧不动）。
            val displayResults = if (locked) results.filterNot { it is QuickSearchResult.ClipEntry } else results
            if (query.text.isNotBlank() && displayResults.isNotEmpty()) {
                // 搜索态：匹配的历史全部参展（点即回填，贴胶囊），结果排其上。
                // 标识指令（bb 新闻 / sms 138）下历史不参展：纯目标结果。
                val q = query.text.trim()
                val matches = if (index.isDirective(q, searchSettings, launcherSettings)) {
                    emptyList()
                } else {
                    searchSettings.recentQueries.filter {
                        it.isNotBlank() && it.trim().length >= MIN_HISTORY_CHARS &&
                            !it.equals(q, ignoreCase = true) && it.contains(q, ignoreCase = true)
                    }
                }
                val total = matches.size + displayResults.size
                // 反转列表内容超高时会把首项顶出可视区之下：受控滚动，内容一变钉回
                // 第 0 项（最贴胶囊那行）。API 级保证可见，不依赖布局锚定行为。
                val searchListState = rememberLazyListState()
                LaunchedEffect(total) { searchListState.scrollToItem(0) }
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    // 反转只管顺序不管小内容锚定，显式 Bottom 后单条目也必贴胶囊
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                    reverseLayout = true,
                ) {
                    itemsIndexed(matches, key = { idx, h -> "m-$idx-${h.hashCode()}" }) { pos, h ->
                        HistoryRow(
                            text = h,
                            shape = segmentedShape(total - 1 - pos, total),
                            onClick = { query = backfillText(h).let { TextFieldValue(it, TextRange(it.length)) } },
                            onLongClick = { deleteHistory(h) },
                        )
                    }
                    itemsIndexed(displayResults, key = { idx, item -> "${item::class.simpleName}-$idx-${item.title.hashCode()}" }) { index, item ->
                        val pos = matches.size + index
                        val shape = segmentedShape(total - 1 - pos, total)
                        if (item is QuickSearchResult.ClipEntry) {
                            ClipboardRow(
                                text = item.text,
                                shape = shape,
                                onClick = { query = backfillText(item.text).let { TextFieldValue(it, TextRange(it.length)) } },
                                onLongClick = { safeLongPress(item) },
                                pinned = item.text in pinnedSet,
                                onTogglePin = { togglePin(item.text) },
                            )
                        } else {
                            ResultRow(
                                result = item,
                                shape = shape,
                                onClick = { safeResult(item, query.text.trim()) },
                                onLongClick = { safeLongPress(item) },
                            )
                        }
                    }
                }
            } else if (query.text.isEmpty() || (!settled && query.text.isNotBlank())) {
                // 空串，或首字在途（有字、无旧数据、结果没到）：继续显示空态。
                // 空输入：剪贴板历史最贴胶囊，其次搜索历史（最近 3），常用、最近文件在上。
                // 剪贴板由 Activity.onWindowFocusChanged 喂进来（首开 composition 早于焦点）。
                val history = searchSettings.recentQueries
                    .filter { it.trim().length >= MIN_HISTORY_CHARS }
                    .take(BLANK_RECENT_LIMIT)
                val clips = if (searchSettings.searchClipboard && !locked) {
                    // 当前剪贴优先（焦点刚读到的），后面跟历史去重，空页只展 3 条。
                    (listOfNotNull(clipboardText) + searchSettings.clipHistory).distinct().take(3)
                } else {
                    emptyList()
                }
                // 锁屏占位：剪贴内容不展，只留一行提示。
                val showClipPlaceholder = searchSettings.searchClipboard && locked
                val clipPlaceholderCount = if (showClipPlaceholder) 1 else 0
                // 空态常用/最近文件放 IO 线程：包查询 + MediaStore 在 composition 里同步跑
                // 会堵主线程（与巨型剪贴同症状的 ANR）。首帧先展剪贴/历史，到达后补齐。
                var used by remember { mutableStateOf(emptyList<QuickSearchResult.App>()) }
                LaunchedEffect(launcherSettings) {
                    used = withContext(Dispatchers.IO) { index.topUsed(launcherSettings) }
                }
                var recent by remember { mutableStateOf(emptyList<QuickSearchResult>()) }
                LaunchedEffect(mediaTick, searchSettings.searchFiles) {
                    recent =
                        if (searchSettings.searchFiles) withContext(Dispatchers.IO) { index.recentFiles() }
                        else emptyList()
                }
                val clipCount = clips.size
                // 锁屏占位占 clips 段的首位：后续 pos 统一按槽位（clips+占位）偏移。
                val clipSlotCount = clipCount + clipPlaceholderCount
                // 联系人未授权：顶部给一行授权入口（点申请，永久拒绝跳设置页）。
                val showGrant = searchSettings.searchContacts && !contactsPerm.isGranted
                val grantCount = if (showGrant) 1 else 0
                // P3：媒体缺权限给一行（一次要三个），all-files 没开给一行（跳设置页）。
                val showMediaGrant = (searchSettings.searchMusic || searchSettings.searchFiles) &&
                    !(audioGranted && imagesGranted && videoGranted)
                val showFilesGrant = searchSettings.searchFiles && !filesGranted
                val mediaGrantCount = if (showMediaGrant) 1 else 0
                val filesGrantCount = if (showFilesGrant) 1 else 0
                // 日历/短信/通话：各给一行授权入口（不合并，用户定的）。
                val showCalendarGrant = searchSettings.searchCalendar && !calendarPerm.isGranted
                val showSmsGrant = searchSettings.searchSms && !smsPerm.isGranted
                val showCallsGrant = searchSettings.searchCalls && !callsPerm.isGranted
                val calendarGrantCount = if (showCalendarGrant) 1 else 0
                val smsGrantCount = if (showSmsGrant) 1 else 0
                val callsGrantCount = if (showCallsGrant) 1 else 0
                if (history.isNotEmpty() || clips.isNotEmpty() || showClipPlaceholder || used.isNotEmpty() || recent.isNotEmpty() ||
                    showGrant || showMediaGrant || showFilesGrant ||
                    showCalendarGrant || showSmsGrant || showCallsGrant
                ) {
                    val total = history.size + clipCount + clipPlaceholderCount + used.size + recent.size +
                        grantCount + mediaGrantCount + filesGrantCount +
                        calendarGrantCount + smsGrantCount + callsGrantCount
                    val blankListState = rememberLazyListState()
                    LaunchedEffect(total) { blankListState.scrollToItem(0) }
                    LazyColumn(
                        state = blankListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                        reverseLayout = true,
                    ) {
                        // 剪贴板历史最贴胶囊（首条=当前剪贴），其次搜索历史，最后常用/最近文件。
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
                        itemsIndexed(clips, key = { idx, c -> "c-$idx-${c.hashCode()}" }) { pos, clipText ->
                            ClipboardRow(
                                text = clipText,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = {
                                    query = backfillText(clipText).let { TextFieldValue(it, TextRange(it.length)) }
                                },
                                onLongClick = { safeLongPress(QuickSearchResult.ClipEntry(clipText)) },
                                pinned = clipText in pinnedSet,
                                onTogglePin = { togglePin(clipText) },
                            )
                        }
                        itemsIndexed(history, key = { idx, h -> "h-$idx-${h.hashCode()}" }) { pos, h ->
                            HistoryRow(
                                text = h,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = {
                                    query = backfillText(h).let { TextFieldValue(it, TextRange(it.length)) }
                                },
                                onLongClick = { deleteHistory(h) },
                            )
                        }
                        itemsIndexed(used, key = { idx, app -> "u-$idx-${app.appInfo.packageName}" }) { idx, app ->
                            val pos = clipSlotCount + history.size + idx
                            ResultRow(
                                result = app,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = { safeResult(app, "") },
                                onLongClick = { safeLongPress(app) },
                            )
                        }
                        // 最近文件（图库 + 文档按修改时间，不搜只展，点开/长按分享走 ResultRow）。
                        itemsIndexed(recent, key = { idx, item -> "r-$idx-${item.title}" }) { idx, item ->
                            val pos = clipSlotCount + history.size + used.size + idx
                            ResultRow(
                                result = item,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = { safeResult(item, "") },
                                onLongClick = { safeLongPress(item) },
                            )
                        }
                        if (showGrant) {
                            item(key = "grant-contacts") {
                                val pos = history.size + clipSlotCount + used.size + recent.size
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
                                val pos = history.size + clipSlotCount + used.size + recent.size + grantCount
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_media),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.Default.MusicNote,
                                    onClick = {
                                        if (mediaDeniedForever) {
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
                                val pos = history.size + clipSlotCount + used.size + recent.size + grantCount + mediaGrantCount
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
                                val pos = history.size + clipSlotCount + used.size + recent.size +
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
                                val pos = history.size + clipSlotCount + used.size + recent.size +
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
                                val pos = history.size + clipSlotCount + used.size + recent.size +
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
                    Spacer(Modifier.weight(1f))
                }
            } else if (query.text.isBlank()) {
                // 全空格输入：标识提示（图标 来源名：标识），点即填入标识。
                // 空串走老空态；删掉空格回去。标识关闭的来源/引擎跳过。
                val hintSources = remember(searchSettings) {
                    listOf(
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
                    ).mapNotNull { (key, icon) ->
                        val t = searchSettings.effectiveTrigger(key)
                        if (t.isBlank()) null else Triple(key, t, icon)
                    }
                }
                val hintEngines = remember(searchSettings) {
                    searchSettings.effectiveEngines()
                        .filter { it.trigger.isNotBlank() }
                        .map { it.name to it.trigger.trim() }
                }
                val hintTotal = hintSources.size + hintEngines.size
                if (hintTotal > 0) {
                    val hintListState = rememberLazyListState()
                    LaunchedEffect(hintTotal) { hintListState.scrollToItem(0) }
                    LazyColumn(
                        state = hintListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
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
                                    query = TextFieldValue(trigger, TextRange(trigger.length))
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
                                    query = TextFieldValue(trigger, TextRange(trigger.length))
                                },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            // 回车确认 = 右侧 Check：打列表首位。引擎兜底 tier -1 垫底，
            // 只有它是唯一行时才会被确认（不再需要跳过特例）。
            // 计算器排表头，天然成为确认目标。
            val confirmFirst: () -> Unit = {
                results.firstOrNull()?.let { safeResult(it, query.text.trim()) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { if (it.text.length <= MAX_QUERY_CHARS) query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter)
                    // 顶部 0：列表底边直接坐到输入框上沿，中间 0 间隙。
                    .padding(bottom = 8.dp)
                    .imePadding()
                    .focusRequester(focusRequester),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.text.isNotBlank()) {
                        androidx.compose.material3.IconButton(
                            // 注意列表是反转的：firstOrNull 是视觉底部（离胶囊最近），与显示顺序相反。
                            onClick = confirmFirst,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    } else if (voiceAvailable) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                voiceLauncher.launch(
                                    Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                        )
                                        putExtra(
                                            android.speech.RecognizerIntent.EXTRA_PROMPT,
                                            query.text,
                                        )
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                // 默认英文键盘 + 回车即确认（否则回车只是收键盘）。
                // Ascii 会锁死英文切不出中文；Text 则完全交给输入法。
                // 中间态（默认英文但可切换）API 层面不存在，二选一后定 Ascii。
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { confirmFirst() }),
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
    }
}