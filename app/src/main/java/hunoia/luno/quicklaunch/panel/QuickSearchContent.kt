package hunoia.luno.quicklaunch.panel
import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.mapLatest
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.quicklaunch.icon.SearchIconCache
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import hunoia.luno.BuildConfig
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.config.model.MAX_CLIP_HISTORY
import hunoia.luno.config.model.MIN_HISTORY_CHARS
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchIcon
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.quicklaunch.query.normalizePinQuery
import hunoia.luno.quicklaunch.query.searchOutcome
import hunoia.luno.quicklaunch.query.topUsed
import hunoia.luno.quicklaunch.query.toSearchAction
import hunoia.luno.quicklaunch.query.toSearchResult
import hunoia.luno.ui.permission.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/** 搜索输入快照：snapshotFlow 跟踪块内读到的 State，值变化即重搜。 */
/** 搜索流的原子包：结果 + 对应的 query + 是否命中标识指令。 */
private data class SearchPacket(
    val text: String,
    val results: List<SearchResult>,
    val directive: Boolean,
)

private data class SearchSnapshot(
    val text: String,
    val entries: List<hunoia.luno.action.model.ActionLibraryEntry>,
    val launcher: QuickAppLauncherSettings,
    val search: QuickSearchSettings,
    val contactsTick: Int,
    val mediaTick: Int,
    val calendarTick: Int,
    val smsTick: Int,
    val callsTick: Int,
    val torchOn: Boolean,
)
/** 影响搜索结果的设置子集（纯值投影）：直接拿 QuickSearchSettings 当依赖会让改外观/删历史都触发全量重搜；recentQueries 有意排除。 */
private data class SearchInputs(
    val customEngines: List<SearchEngine>,
    val sourceTriggers: Map<String, String>,
    val searchClipboard: Boolean,
    val searchContacts: Boolean,
    val searchMusic: Boolean,
    val searchFiles: Boolean,
    val searchCalendar: Boolean,
    val searchToggles: Boolean,
    val searchSms: Boolean,
    val searchCalls: Boolean,
    val searchApps: Boolean,
    val searchShortcuts: Boolean,
    val searchActions: Boolean,
    val searchSettingsPages: Boolean,
    val clipHistory: List<String>,
    val pinnedClips: List<String>,
    val engineOrder: List<String>,
    val pinnedResults: Map<String, List<String>>,
    val pinnedQueryOrder: List<String>,
) {
    /** 反向拼回引擎/匹配层认的完整设置；recentQueries 与 UI 无关字段留默认值。 */
    fun toSettings() = QuickSearchSettings(
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
        searchApps = searchApps,
        searchShortcuts = searchShortcuts,
        searchActions = searchActions,
        searchSettingsPages = searchSettingsPages,
        clipHistory = clipHistory,
        pinnedClips = pinnedClips,
        engineOrder = engineOrder,
        pinnedResults = pinnedResults,
        pinnedQueryOrder = pinnedQueryOrder,
    )

    companion object {
        fun of(s: QuickSearchSettings) = SearchInputs(
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
            searchApps = s.searchApps,
            searchShortcuts = s.searchShortcuts,
            searchActions = s.searchActions,
            searchSettingsPages = s.searchSettingsPages,
            clipHistory = s.clipHistory,
            pinnedClips = s.pinnedClips,
            engineOrder = s.engineOrder,
            pinnedResults = s.pinnedResults,
            pinnedQueryOrder = s.pinnedQueryOrder,
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
    onResult: (SearchResult, String) -> Unit,
    onAction: (SearchAction, String) -> Unit,
    onTogglePin: (String, String) -> Unit,
    onSaveDraft: (String) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val libraryFlow = remember { hunoia.luno.config.ConfigProvider.actionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = ActionLibrarySettings())
    val entries = libraryState.entries
    val launcherFlow = remember { hunoia.luno.config.ConfigProvider.quickAppLauncherSettings }
    val launcherSettings by launcherFlow.collectAsState(initial = QuickAppLauncherSettings())
    val searchSettingsFlow = remember { hunoia.luno.config.ConfigProvider.quickSearchSettings }
    val searchSettings by searchSettingsFlow.collectAsState(initial = QuickSearchSettings())
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var actionSheetResult by remember { mutableStateOf<SearchResult?>(null) }
    var actionSheetQuery by remember { mutableStateOf("") }
    var enginePickerQuery by remember { mutableStateOf<String?>(null) }
    var restoredDraft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 权限状态 + 媒体授权申请：数据装载效果（QuickSearchDataEffects）与空态授权行共用。
    val permCtx = LocalContext.current
    val act = permCtx as? Activity
    val contactsPerm = rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    val calendarPerm = rememberPermissionState(android.Manifest.permission.READ_CALENDAR)
    val smsPerm = rememberPermissionState(android.Manifest.permission.READ_SMS)
    val callsPerm = rememberPermissionState(android.Manifest.permission.READ_CALL_LOG)
    val ticks = remember { QuickSearchDataTicks() }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { ticks.permTick++ }
    val grants = rememberMediaGrants(permCtx, act, ticks.permTick)
    QuickSearchDataEffects(
        index = index,
        settings = searchSettings,
        ticks = ticks,
        grants = grants,
        contactsPerm = contactsPerm,
        calendarPerm = calendarPerm,
        smsPerm = smsPerm,
        callsPerm = callsPerm,
    )
    // 行图标边长（展示与 flow 预热同源）：flow 里 resolve 进同步缓存，
    // composition 只做同步读，首帧即有图。
    val iconPx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    val appCtx = permCtx.applicationContext
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
        QuickAppLauncherSettings(
            launchCount = launchCount,
            recentLaunchTime = recentLaunchTime,
        )
    }
    // 应用索引版本：普通 @Volatile 字段 Compose 看不见，必须经 StateFlow 收集。
    val indexVersion by index.indexVersion.collectAsState()
    // dataVersion：数据源 tick / 索引版本，任一变化就重建搜索流并重搜一次。
    // 值本身不进快照，否则同一变化会被算两次。
    val dataVersion = ticks.contactsTick + ticks.mediaTick + ticks.calendarTick +
        ticks.smsTick + ticks.callsTick + indexVersion + ticks.permCacheTick
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
                SearchPacket(text, emptyList(), directive = false)
            } else {
                withContext(Dispatchers.Default) {
                    val outcome = index.searchOutcome(text, s.entries, s.launcher, s.search, s.torchOn)
                    // 发射前把 App/快捷方式行的图标 resolve 进同步缓存：UI 线程读到即画，
                    // 结果原子到达。已缓存的零开销，只有新包才解码（自带 bitmap 的快捷方式跳过）。
                    val pkgs = HashSet<String>()
                    for (r in outcome.results) {
                        val icon = r.icon
                        if (icon is SearchIcon.AppIcon && icon.bitmap == null) {
                            pkgs.add(icon.packageName)
                        }
                    }
                    SearchIconCache.ensureAll(appCtx, pkgs, iconPx)
                    SearchPacket(text, outcome.results, outcome.directive)
                }
            }
        }
    }
    // 原子包：结果永远带着它对应的 query。到达前 results 留旧值，
    // 首字无旧值则继续显示空态——搜索分支只挂完整数据，不挂半成品。
    val packet by searchFlow.collectAsState(initial = SearchPacket("", emptyList(), directive = false))
    val results = packet.results
    val settled = packet.text == query.text
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
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    // 草稿：设置流首次到达后回填一次；空草稿不占用“已恢复”标记，避免默认空设置
    // 先到、真实草稿后到时被吞掉。
    LaunchedEffect(searchSettings.lastQuery, searchSettings.restoreLastQuery) {
        if (!restoredDraft &&
            searchSettings.restoreLastQuery &&
            searchSettings.lastQuery.isNotBlank() &&
            query.text.isBlank()
        ) {
            restoredDraft = true
            val text = backfillText(searchSettings.lastQuery)
            query = TextFieldValue(text, TextRange(text.length))
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            actionSheetResult = null
            enginePickerQuery = null
            onSaveDraft(query.text)
        }
    }
    // 空态常用应用：放在条件分支外，面板生命周期内只跑一次 —— 写进分支里的话分支一退出
    // 就取消，来回切 query 会反复重启 IO。
    var topUsedApps by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(effectiveLauncher, indexVersion) {
        topUsedApps = withContext(Dispatchers.IO) {
            index.topUsed(effectiveLauncher)
                .map { it.toSearchResult() }
        }
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
    val safeResult: (SearchResult, String) -> Unit = { r, q -> if (!closing) onResult(r, q) }
    val safeLongPress: (SearchResult) -> Unit = { result ->
        if (!closing) {
            actionSheetResult = result
            actionSheetQuery = query.text.trim()
        }
    }
    val safeAction: (SearchAction, String) -> Unit = { a, q -> if (!closing) onAction(a, q) }
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
        // Activity 已 enableEdgeToEdge，键盘跟随固定走 Compose 标准 imePadding。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // 分支按「数据完整」进：flow 发射 (queryText, results) 原子包，在途时不挂半成品 ——
            // 首字无旧数据继续显示空态，有旧数据先显示旧列表，到达后整批换。
            // 锁屏时结果里的剪贴行全部滤掉（展示隐藏，引擎侧不动）。
            val displayResults = remember(results, locked) {
                results.filterNot { locked && it.source == SRC_CLIP }
            }
            if (query.text.isNotBlank() && displayResults.isNotEmpty()) {
                // 搜索态：匹配的历史全部参展（点即回填，贴胶囊），结果排其上。
                // 标识指令（bb 新闻 / sms 138）下历史不参展：纯目标结果。
                val q = query.text.trim()
                // 指令态（bb 新闻 / sms 138）只回目标结果，历史行不参展：
                // 标志由搜索流回报，面板不再单独判一次（判一次要摸裸标识列表，可能触发全量查包）。
                val matches = if (packet.directive) {
                    emptyList()
                } else {
                    searchSettings.recentQueries.filter {
                        it.isNotBlank() && it.trim().length >= MIN_HISTORY_CHARS &&
                            !it.equals(q, ignoreCase = true) && it.contains(q, ignoreCase = true)
                    }
                }
                QuickSearchSearchList(
                    results = displayResults,
                    matches = matches,
                    pinnedSet = pinnedSet,
                    queryText = query.text,
                    onQueryChange = { query = it },
                    onResultClick = safeResult,
                    onLongPress = safeLongPress,
                    onShowEngines = { enginePickerQuery = it },
                    onTogglePin = togglePin,
                    onDeleteHistory = deleteHistory,
                    modifier = Modifier.weight(1f),
                )
            } else if (query.text.isEmpty() || (!settled && query.text.isNotBlank())) {
                // 空串，或首字在途（有字、无旧数据、结果没到）：继续显示空态。
                // 剪贴板由 Activity.onWindowFocusChanged 喂进来（首开 composition 早于焦点）。
                QuickSearchEmptyList(
                    settings = searchSettings,
                    clipboardText = clipboardText,
                    locked = locked,
                    used = topUsedApps,
                    grants = grants,
                    contactsPerm = contactsPerm,
                    calendarPerm = calendarPerm,
                    smsPerm = smsPerm,
                    callsPerm = callsPerm,
                    mediaLauncher = mediaLauncher,
                    permCtx = permCtx,
                    pinnedSet = pinnedSet,
                    onQueryChange = { query = it },
                    onResultClick = safeResult,
                    onLongPress = safeLongPress,
                    onTogglePin = togglePin,
                    onDeleteHistory = deleteHistory,
                    onOpenAppSettings = onOpenAppSettings,
                    modifier = Modifier.weight(1f),
                )
            } else if (query.text.isBlank()) {
                // 全空格输入：标识提示（图标 来源名：标识），点即填入标识。
                QuickSearchTriggerHints(
                    settings = searchSettings,
                    onQueryChange = { query = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            QuickSearchEngineBar(
                engines = searchSettings.effectiveEngines(),
                queryText = query.text,
                onEngineAction = safeAction,
            )
            // 回车确认 = 右侧 Check：打列表首位。引擎兜底 tier -1 垫底，计算器排表头，
            // 所以直接取 firstOrNull 即为确认目标，无需跳过特例。
            val confirmFirst: () -> Unit = {
                results.firstOrNull()?.let { safeResult(it, query.text.trim()) }
            }
            // 键盘动作对象 remember 住：每次重组新建 lambda 会让 OutlinedTextField 的
            // keyboardActions 参数恒变，破坏它的跳过。
            val keyboardActions = remember(results) {
                KeyboardActions(onSearch = { confirmFirst() })
            }
            QuickSearchField(
                value = query,
                onValueChange = { query = it },
                onConfirm = confirmFirst,
                keyboardActions = keyboardActions,
                focusRequester = focusRequester,
                asciiEnter = searchSettings.asciiEnterSearch,
            )
        }

        actionSheetResult?.let { result ->
            val pinned = result.key in searchSettings
                .pinnedResults[normalizePinQuery(actionSheetQuery)].orEmpty()
            SearchResultActionSheet(
                result = result,
                query = actionSheetQuery,
                pinned = pinned,
                onAction = { action, q ->
                    actionSheetResult = null
                    if (action is SearchAction.ShowEngines) {
                        enginePickerQuery = action.query
                    } else {
                        onAction(action, q)
                    }
                },
                onTogglePin = { q, key -> actionSheetResult = null; onTogglePin(q, key) },
                onShowEngines = { q -> actionSheetResult = null; enginePickerQuery = q },
                onDismiss = { actionSheetResult = null },
            )
        }
        enginePickerQuery?.let { pickerQuery ->
            val engines = searchSettings.effectiveEngines()
            if (engines.isNotEmpty()) {
                SearchEnginePickerSheet(
                    engines = engines,
                    onPick = { engine ->
                        enginePickerQuery = null
                        onAction(engine.effectiveLaunch().toSearchAction(pickerQuery), pickerQuery)
                    },
                    onDismiss = { enginePickerQuery = null },
                )
            }
        }
    }
}
