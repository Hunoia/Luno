package hunoia.luno.quicklaunch.panel
import android.app.Activity
import android.os.SystemClock
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
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.input.TextFieldValue
import hunoia.luno.BuildConfig
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.AppUsageStats
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/** 搜索输入快照：snapshotFlow 跟踪块内读到的 State，值变化即重搜。 */
/** 搜索流的原子包：结果 + 对应的 query + 是否命中标识指令。 */
private data class SearchPacket(
    val text: String,
    val results: List<SearchResult>,
    val directive: Boolean,
)

/** 被 ACTION_OUTSIDE 收起的菜单快照：用于吞掉同一次点击里锚点按钮随后触发的重开。 */
private data class DismissedMenu(val key: String, val at: Long)

/** 覆盖一次按压 down→up 的时长（含慢速点按），又不至于吞掉刻意的再次点击。 */
private const val MENU_REOPEN_SUPPRESS_MS = 400L

/** 首屏图标预热上限：只解码当前可能露出的前几行，长尾交给列表可见时 Coil 按需加载。 */
private const val ICON_PREWARM_LIMIT = 24

private data class SearchSnapshot(
    val text: String,
    val entries: List<hunoia.luno.action.model.ActionLibraryEntry>,
    val usage: AppUsageStats,
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
    onOpenAppSettings: () -> Unit,
) {
    val libraryFlow = remember { hunoia.luno.config.ConfigProvider.actionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = ActionLibrarySettings())
    val entries = libraryState.entries
    val usageFlow = remember { hunoia.luno.config.ConfigProvider.appUsageStats }
    val usageStats by usageFlow.collectAsState(initial = AppUsageStats())
    val searchSettingsFlow = remember { hunoia.luno.config.ConfigProvider.quickSearchSettings }
    val searchSettings by searchSettingsFlow.collectAsState(initial = QuickSearchSettings())
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var menuResult by remember { mutableStateOf<SearchResult?>(null) }
    var enginePickerQuery by remember { mutableStateOf<String?>(null) }
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
    // 引擎/开关匹配只吃这两个（SearchInputs 已含其余），拼回完整设置只为沿用旧签名。
    val effectiveSearchSettings = remember(searchInputs) { searchInputs.toSettings() }
    // 启动统计是搜索输入但不重建流：用 rememberUpdatedState 包一层，由块内 snapshotFlow
    // 读取，统计变了会自动重发一次快照，下一次搜索自然带上新分，不用重建整条管道。
    val usageState = rememberUpdatedState(usageStats)
    // 应用索引版本：普通 @Volatile 字段 Compose 看不见，必须经 StateFlow 收集。
    val indexVersion by index.indexVersion.collectAsState()
    // dataVersion：数据源 tick / 索引版本，任一变化就重建搜索流并重搜一次。
    // 值本身不进快照，否则同一变化会被算两次。
    val dataVersion = ticks.contactsTick + ticks.mediaTick + ticks.calendarTick +
        ticks.smsTick + ticks.callsTick + indexVersion + ticks.permCacheTick
    val searchFlow = remember(
        appCtx, iconPx, searchInputs, effectiveSearchSettings, dataVersion, torchOn,
    ) {
        snapshotFlow {
            // 块内只读快照里没装但会影响结果的 State：query（打字即时触发）、
            // entries（动作库条目参与匹配）、usage（启动统计影响使用分）；
            // tick 已由 remember 的 key 带进闭包。
            SearchSnapshot(
                query.text, entries, usageState.value, effectiveSearchSettings,
                0, 0, 0, 0, 0, torchOn,
            )
        }.mapLatest { s ->
            val text = s.text
            if (text.isBlank()) {
                SearchPacket(text, emptyList(), directive = false)
            } else {
                withContext(Dispatchers.Default) {
                    val job = currentCoroutineContext()[Job]
                    val outcome = index.searchOutcome(
                        text, s.entries, s.usage, s.search, s.torchOn,
                        isActive = { job?.isActive != false },
                    )
                    // 发射前把 App/快捷方式行的图标 resolve 进同步缓存：UI 线程读到即画，
                    // 结果原子到达。已缓存的零开销，只有新包才解码；只预热首屏上限，
                    // 避免单字母查询把几百个包的图标串行解码后才发射结果。
                    val pkgs = HashSet<String>()
                    for (r in outcome.results.asSequence().take(ICON_PREWARM_LIMIT)) {
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
    LaunchedEffect(closing) {
        if (closing) {
            menuResult = null
            enginePickerQuery = null
        }
    }
    // 菜单 popup 非 focusable（见 ResultActionMenu）：收不到 back、也不随键盘联动，这两处收口。
    // 键盘 inset 一开始收缩就先收菜单：键盘收起（返回键/下滑收键盘）会让 imePadding 塌缩、
    // 锚点下移，菜单必须赶在布局移动前消失，否则又是跳动。previousIme 挡掉开场键盘还没弹出
    // （inset 恒 0）时对刚打开菜单的误清。
    val imeInsets = WindowInsets.ime
    val imeDensity = LocalDensity.current
    LaunchedEffect(imeInsets, imeDensity) {
        var previousIme = 0
        snapshotFlow { imeInsets.getBottom(imeDensity) }.collect { ime ->
            if (menuResult != null && ime < previousIme) menuResult = null
            previousIme = ime
        }
    }
    BackHandler(enabled = menuResult != null && !closing) { menuResult = null }
    // 空态常用应用：放在条件分支外，面板生命周期内只跑一次 —— 写进分支里的话分支一退出
    // 就取消，来回切 query 会反复重启 IO。
    var topUsedApps by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(usageStats, indexVersion) {
        topUsedApps = withContext(Dispatchers.IO) {
            index.topUsed(usageStats)
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
    // 菜单 popup 非 focusable 后，锚点按钮上的一次点击 = down 时 ACTION_OUTSIDE 先收菜单、
    // up 时按钮 onClick 再开菜单，表现为"点三点永远重新打开而不是关闭"。记录刚被外部
    // 点击收起的 key + 时间戳，窗口期内同 key 的打开请求吞掉（三点即成开关）；换行/长按
    // 打开不受影响。一次性：任何打开尝试都会消费掉这条记录。
    var menuDismissal by remember { mutableStateOf<DismissedMenu?>(null) }
    val safeMenuOpen: (SearchResult) -> Unit = { result ->
        if (!closing) {
            val dismissal = menuDismissal
            val suppress = dismissal != null &&
                dismissal.key == result.key &&
                SystemClock.uptimeMillis() - dismissal.at < MENU_REOPEN_SUPPRESS_MS
            menuDismissal = null
            if (!suppress) menuResult = result
        }
    }
    // 结果菜单动作收口：先收菜单；ShowEngines 转引擎选择（引擎结果的动作里内嵌它），其余上抛。
    val handleResultAction: (SearchAction, String) -> Unit = { action, q ->
        menuResult = null
        if (action is SearchAction.ShowEngines) {
            enginePickerQuery = action.query
        } else {
            onAction(action, q)
        }
    }
    val onMenuDismiss: () -> Unit = {
        menuResult?.let { menuDismissal = DismissedMenu(it.key, SystemClock.uptimeMillis()) }
        menuResult = null
    }
    // 当前 query 的结果级 Pin key 集（trim+lowercase，与索引层同口径）。
    val resultPinnedKeys = remember(searchSettings.pinnedResults, query.text) {
        searchSettings.pinnedResults[normalizePinQuery(query.text.trim())].orEmpty().toSet()
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
                    menuResult = menuResult,
                    resultPinnedKeys = resultPinnedKeys,
                    onQueryChange = { query = it },
                    onResultClick = safeResult,
                    onMenuOpen = safeMenuOpen,
                    onMenuDismiss = onMenuDismiss,
                    onResultAction = handleResultAction,
                    onToggleResultPin = onTogglePin,
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
                    queryText = query.text,
                    menuResult = menuResult,
                    resultPinnedKeys = resultPinnedKeys,
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
                    onMenuOpen = safeMenuOpen,
                    onMenuDismiss = onMenuDismiss,
                    onResultAction = handleResultAction,
                    onToggleResultPin = onTogglePin,
                    onShowEngines = { enginePickerQuery = it },
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
