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
import kotlinx.coroutines.flow.filter
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
import hunoia.luno.quicklaunch.query.invalidateToggleState
import hunoia.luno.quicklaunch.query.isDirective
import hunoia.luno.quicklaunch.query.refreshPermissionCache
import hunoia.luno.quicklaunch.query.search
import hunoia.luno.quicklaunch.query.stableId
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
    val entries: List<hunoia.luno.action.model.ActionLibraryEntry>,
    val launcher: hunoia.luno.config.model.QuickAppLauncherSettings,
    val search: hunoia.luno.config.model.QuickSearchSettings,
    val contactsTick: Int,
    val mediaTick: Int,
    val calendarTick: Int,
    val smsTick: Int,
    val callsTick: Int,
    val torchOn: Boolean,
)
/** 影响搜索结果的设置子集（纯值投影）：直接拿 QuickSearchSettings 当依赖会让改外观/删历史都触发全量重搜；recentQueries 有意排除。 */
private data class SearchInputs(
    val defaultEngineId: String,
    val customEngines: List<hunoia.luno.config.model.SearchEngine>,
    val sourceTriggers: Map<String, String>,
    val searchClipboard: Boolean,
    val searchContacts: Boolean,
    val searchMusic: Boolean,
    val searchFiles: Boolean,
    val searchCalendar: Boolean,
    val searchToggles: Boolean,
    val searchSms: Boolean,
    val searchCalls: Boolean,
    val clipHistory: List<String>,
    val pinnedClips: List<String>,
) {
    /** 反向拼回引擎/匹配层认的完整设置；recentQueries 与 UI 无关字段留默认值。 */
    fun toSettings() = hunoia.luno.config.model.QuickSearchSettings(
        defaultEngineId = defaultEngineId,
        customEngines = customEngines,
        sourceTriggers = sourceTriggers,
        searchClipboard = searchClipboard,
        searchContacts = searchContacts,
        searchMusic = searchMusic,
        searchFiles = searchFiles,
        searchCalendar = searchCalendar,
        searchToggles = searchToggles,
        searchSms = searchSms,
        searchCalls = searchCalls,
        clipHistory = clipHistory,
        pinnedClips = pinnedClips,
    )

    companion object {
        fun of(s: hunoia.luno.config.model.QuickSearchSettings) = SearchInputs(
            defaultEngineId = s.defaultEngineId,
            customEngines = s.customEngines,
            sourceTriggers = s.sourceTriggers,
            searchClipboard = s.searchClipboard,
            searchContacts = s.searchContacts,
            searchMusic = s.searchMusic,
            searchFiles = s.searchFiles,
            searchCalendar = s.searchCalendar,
            searchToggles = s.searchToggles,
            searchSms = s.searchSms,
            searchCalls = s.searchCalls,
            clipHistory = s.clipHistory,
            pinnedClips = s.pinnedClips,
        )
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    val libraryFlow = remember { hunoia.luno.config.ConfigProvider.actionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = hunoia.luno.action.model.ActionLibrarySettings())
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
    // 搜索搬出主线程：query 即时更新，结果流式回来；mapLatest 取消过期搜索，空串短路。
    // 依赖只留不会每帧变的东西：query 不进 key（块内 snapshotFlow 读），设置走 SearchInputs，tick 合成 dataVersion。
    val searchInputs = remember(searchSettings) { SearchInputs.of(searchSettings) }
    val launchCount = remember(launcherSettings) { launcherSettings.launchCount }
    val recentLaunchTime = remember(launcherSettings) { launcherSettings.recentLaunchTime }
    // 引擎/开关匹配只吃这两个（SearchInputs 已含其余），拼回完整设置只为沿用旧签名。
    val effectiveSearchSettings = remember(searchInputs) { searchInputs.toSettings() }
    // 启动频率/最近启动变了不必重建流：排序分是搜索时现算的，下一次搜索自然带上新值。
    // 所以这里把 launcher 只留「搜索侧用得到」的两个 map，引用由 remember 抄平。
    val effectiveLauncher = remember(launchCount, recentLaunchTime) {
        hunoia.luno.config.model.QuickAppLauncherSettings(
            launchCount = launchCount,
            recentLaunchTime = recentLaunchTime,
        )
    }
    // 应用索引版本：普通 @Volatile 字段 Compose 看不见，必须经 StateFlow 收集。
    val indexVersion by index.indexVersion.collectAsState()
    // 权限快照刷新的版本号：快照是 @Volatile 普通字段，靠这个 State 把「已刷新」带进重组。
    var permCacheTick by remember { mutableIntStateOf(0) }
    // dataVersion：数据源 tick / 索引版本，任一变化就重建搜索流并重搜一次。
    // 值本身不进快照，否则同一变化会被算两次。
    val dataVersion = contactsTick + mediaTick + calendarTick + smsTick + callsTick + indexVersion + permCacheTick
    val searchFlow = remember(
        appCtx, iconPx, searchInputs, effectiveLauncher, effectiveSearchSettings, dataVersion, torchOn,
    ) {
        snapshotFlow {
            // 块内只读快照里没装但会影响结果的 State：query（打字即时触发）、
            // entries（动作库条目参与匹配）；tick 已由 remember 的 key 带进闭包。
            SearchSnapshot(
                query.text, entries, effectiveLauncher, effectiveSearchSettings,
                0, 0, 0, 0, 0, torchOn,
            )
        }.mapLatest { s ->
            val text = s.text
            if (text.isBlank()) {
                text to emptyList<QuickSearchResult>()
            } else {
                withContext(Dispatchers.Default) {
                    val out = index.search(text, s.entries, s.launcher, s.search, s.torchOn).also { res ->
                        // 发射前把 App/快捷方式行的图标 resolve 进同步缓存：UI 线程读到即画，
                        // 结果原子到达。已缓存的零开销，只有新包才解码（自带 bitmap 的快捷方式跳过）。
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
                    text to out
                }
            }
        }
    }
    // 原子包：结果永远带着它对应的 query。到达前 results 留旧值，
    // 首字无旧值则继续显示空态——搜索分支只挂完整数据，不挂半成品。
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
    // 权限快照 + 开关状态缓存：面板每次打开都重新读一次真实值（打开期间各 tick 变化也刷）。
    LaunchedEffect(permTick, contactsPerm.isGranted, calendarPerm.isGranted, smsPerm.isGranted, callsPerm.isGranted) {
        withContext(Dispatchers.IO) {
            index.refreshPermissionCache()
            invalidateToggleState()
        }
        permCacheTick++
    }
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
    // 空态常用应用：放在条件分支外，面板生命周期内只跑一次 —— 写进分支里的话分支一退出
    // 就取消，来回切 query 会反复重启 IO。
    var topUsedApps by remember { mutableStateOf(emptyList<QuickSearchResult.App>()) }
    LaunchedEffect(effectiveLauncher, indexVersion) {
        topUsedApps = withContext(Dispatchers.IO) { index.topUsed(effectiveLauncher) }
    }
    // 进出场动画状态：进场从 0.92/0 起播到 1，closing 置位后播到 0.95/0。
    val panelScale = remember { Animatable(0.92f) }
    val panelAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { panelScale.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
        launch { panelAlpha.animateTo(1f, tween(300)) }
    }
    LaunchedEffect(closing) {
        // 退场纯淡出 200ms（缩放重采样尾段是糊的）。播完才调 onExitFinished 拆窗：提前拆会掐断结尾。
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
        // statusBarsPadding：内容从状态栏下方开始，白卡压白图标会看不清，必须避让。
        // imePadding 放整列而不是输入框：键盘弹起时列表跟着收缩，否则底部行被键盘盖住。
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // 分支按「数据完整」进：flow 发射 (queryText, results) 原子包，在途时不挂半成品 ——
            // 首字无旧数据继续显示空态，有旧数据先显示旧列表，到达后整批换。
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
                // 反转列表内容超高时会把首项顶出可视区：受控滚动，内容一变钉回第 0 项。
                // 触发用内容签名而非 total：按数量滚会拽回用户上滑看的结果。
                val searchListState = rememberLazyListState()
                val contentSig = signatureOf(
                    displayResults.firstOrNull(), displayResults.lastOrNull(),
                    total * 31 + matches.size,
                )
                LaunchedEffect(contentSig) { searchListState.scrollToItem(0) }
                // key 只由内容决定、不带 idx：带 idx 时重排会让整列 key 失效、全部重建，
                // 打字时肉眼可见闪。重复项由 stableKeys 补 ~n 保唯一。
                val resultKeys = remember(displayResults) {
                    stableKeys(displayResults.map { it.stableId() })
                }
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    // 反转只管顺序不管小内容锚定，显式 Bottom 后单条目也必贴胶囊
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                    reverseLayout = true,
                ) {
                    itemsIndexed(matches, key = { _, h -> "m#$h" }) { pos, h ->
                        HistoryRow(
                            text = h,
                            shape = segmentedShape(total - 1 - pos, total),
                            onClick = { query = backfillText(h).let { TextFieldValue(it, TextRange(it.length)) } },
                            onLongClick = { deleteHistory(h) },
                        )
                    }
                    itemsIndexed(displayResults, key = { idx, _ -> "r#${resultKeys[idx]}" }) { index, item ->
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
                // 空态常用：数据由上方（分支外的 LaunchedEffect）供，这里只读。
                val used = topUsedApps
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
                        stableKeys(used.map { "u#${it.appInfo.packageName}/${it.appInfo.className}" })
                    }
                    LazyColumn(
                        state = blankListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
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
                        itemsIndexed(history, key = { _, h -> "h#$h" }) { pos, h ->
                            HistoryRow(
                                text = h,
                                // 历史段上方没有 clip，但下方有：算整列视觉下标时必须带上
                                // clipSlotCount，否则历史最后一行会误拿整列底部的大圆角。
                                shape = segmentedShape(total - 1 - clipSlotCount - pos, total),
                                onClick = {
                                    query = backfillText(h).let { TextFieldValue(it, TextRange(it.length)) }
                                },
                                onLongClick = { deleteHistory(h) },
                            )
                        }
                        itemsIndexed(used, key = { idx, _ -> usedKeys[idx] }) { idx, app ->
                            val pos = clipSlotCount + history.size + idx
                            ResultRow(
                                result = app,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = { safeResult(app, "") },
                                onLongClick = { safeLongPress(app) },
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
            // 回车确认 = 右侧 Check：打列表首位。引擎兜底 tier -1 垫底，计算器排表头，
            // 所以直接取 firstOrNull 即为确认目标，无需跳过特例。
            val confirmFirst: () -> Unit = {
                results.firstOrNull()?.let { safeResult(it, query.text.trim()) }
            }
            // 键盘动作对象 remember 住：每次重组新建 lambda 会让 OutlinedTextField 的
            // keyboardActions 参数恒变，破坏它的跳过。
            val keyboardActions = remember(results) {
                KeyboardActions(onSearch = {
                    results.firstOrNull()?.let { safeResult(it, query.text.trim()) }
                })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { if (it.text.length <= MAX_QUERY_CHARS) query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter)
                    .padding(bottom = 8.dp)
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
                // 默认英文键盘 + 回车即确认（否则回车只是收键盘）。Ascii 会让输入法
                // 锁死英文、切不出中文，Text 则完全交给输入法；没有中间态，这里选 Ascii。
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
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
    }
}