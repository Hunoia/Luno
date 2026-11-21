package hunoia.luno.quickaccess.query

import hunoia.luno.search.R
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.allEngines
import java.util.PriorityQueue
import kotlinx.coroutines.CancellationException

/** 裸标识列出该类近项的上限（应用走常用 8，不用这个）。 */
private const val BARE_LIST_LIMIT = 20

/** 全源合并后的展示上限；当前 query 的 Pin 结果旁路保留，不占这个名额。 */
const val MAX_TOTAL_RESULTS = 200

/**
 * 单来源命中上限。必须 >= [MAX_TOTAL_RESULTS]：全局榜只有 200 个位置，
 * 一个来源最多占满全榜，取到第 200 名之后的单源命中永远不可能进全局榜。
 * 这样截断纯粹是内存上界，不改变结果集。
 */
const val MAX_HITS_PER_SOURCE = MAX_TOTAL_RESULTS

    /** 把 query 拆成单字 token（只保留字母数字），复用 AppSearch 拼音引擎。 */
private fun QuickSearchIndex.tokenize(s: String): List<String> =
        s.lowercase().filter { it.isLetterOrDigit() }.map { it.toString() }

/** 复用 AppSearch 拼音引擎：精确档评定，foldedTokens 非空时补模糊降档。 */
fun tierOf(text: String, plan: SearchQueryPlan): TieredMatch =
    AppSearch.matchTextTier(text, plan)

/** 取最优命中（tier 大者胜，再比 offset 小者胜；连续块 span 恒定不参比）。 */
fun bestOf(a: TieredMatch, b: TieredMatch): TieredMatch =
    maxOf(a, b, compareBy({ it.tier }, { -it.offset }))

/**
 * 首字段已是理论最优（tier 2 / offset 0）时不再计算第二字段。
 * 应用名/联系人姓名主命中占多数，能省掉包名/号码的一次建索引与扫描。
 */
inline fun bestOfFirst(first: TieredMatch, second: () -> TieredMatch): TieredMatch =
    if (first.tier == 2 && first.offset == 0) first else bestOf(first, second())

    /** 空输入时的常用：按衰减使用分取前 N 个（禁用应用一并返回，由调用方标注）。 */
fun QuickSearchIndex.topUsed(usageStats: AppUsageStats, limit: Int = 8): List<SearchResult> {
        ensureLoaded()
        // 不再 refreshApps() 兜底：全量查包必须在 IO 线程由 Activity 预加载，
        // 这里只读已经发布的快照（未就绪时返回空，面板稍后由 appsTick 补搜）。
        val data = appData
        val usage = UsageScores(usageStats.launchCount, usageStats.recentLaunchTime, System.currentTimeMillis())
        return data.apps
            .map { app -> app to usage.score(app.packageName) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (app, _) -> app.toResult(app.packageName in data.disabledPkgs) }
}

fun QuickSearchIndex.search(
        query: String,
        libraryEntries: List<ActionLibraryEntry>,
        usageStats: AppUsageStats = AppUsageStats(),
        searchSettings: QuickSearchSettings = QuickSearchSettings(),
        clips: ClipPools = ClipPools(),
        isActive: () -> Boolean = { true },
    ): SearchOutcome {
        ensureLoaded()
        // 这里不再 refreshApps() 兜底：全量查包几十~几百毫秒，会把首字搜索堵在 Dispatchers.Default
        // 上。应用索引由 Activity 在 IO 线程预加载，就绪后 appsLoaded 翻转 → 面板 appsTick 补搜。
        // 引擎列表一次解析：标识直达、默认兜底、引擎行都复用同一份顺序，避免每次现拼。
        val engines = searchSettings.allEngines()
        // 标识指令优先（bb 新闻 / sms 138 / 裸 sms）：命中只回目标类，
        // 纯目标结果（无引擎兜底、无计算器、无历史参展）。null = 无指令，走全源。
        parseDirective(query.trim(), libraryEntries, usageStats, searchSettings, engines, clips)?.let {
            return SearchOutcome(it, directive = true)
        }
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return SearchOutcome(emptyList(), directive = false)
        // 模糊音输入：分词前先整体折叠（adjacency 丢了就没法折），降档用。
        val foldedTokens = tokenize(AppSearch.foldPinyin(query))
        val now = System.currentTimeMillis()
        val plan = AppSearch.queryPlan(tokens, foldedTokens)
        val pinnedKeys = searchSettings.pinnedKeyList(query).toSet()

        val context = SearchContext(
            index = this,
            settings = searchSettings,
            libraryEntries = libraryEntries,
            plan = plan,
            now = now,
            usage = usageStats,
            clips = clips,
        )
        val results = mutableListOf<RankedHit>()
        // 来源匹配统一走注册表；新增来源不再改这里。
        for (source in SearchSourceRegistry.all) {
            if (!isActive()) throw CancellationException("quick search cancelled")
            val sourceHits = SearchSourceRegistry.match(source.key, context)
            // 每个来源先截断：全局排序只可能取到各来源前若干名，没必要把整张表搬进内存。
            results.addAll(capHits(sourceHits, MAX_HITS_PER_SOURCE, pinnedKeys))
        }

        val urlHit = urlResult(query)?.let { RankedHit(it, 3, 0, 0, 0.0) }
        if (urlHit != null) results.add(urlHit)

        val sorted = sortHits(capHits(results, MAX_TOTAL_RESULTS, pinnedKeys))

        // 引擎兜底行不进 [capHits]：它恒为 tier -1，满榜时会被裁掉。固定垫底，
        // 保证任何查询都能回车直接搜。只有非 URL 关键词才有；标识直达优先选引擎，
        // 否则默认引擎；一个引擎都没有时不出引擎行（回车只剩内容源结果）。
        val withEngine = if (urlHit == null) {
            engineTriple(query.trim(), engines)?.let { sorted + it } ?: sorted
        } else {
            sorted
        }

        // 计算器排表头（精确意图，回车直达）；引擎行永远最后，不二次计算。
        val calc = calculatorResult(query.trim())
        val grouped = groupCloneApps(withEngine)
        val finalResults = (calc?.let { listOf(RankedHit(it, -1, 0, 0, 0.0)) } ?: emptyList()) + grouped
        return SearchOutcome(finalResults.map { it.result }, directive = false)
    }

    /** 分身与主应用标题相同：搜索结果里把副本拉回主应用后面，避免看起来像重复项。 */
private fun groupCloneApps(results: List<RankedHit>): List<RankedHit> {
    if (results.none { it.groupKey != null && !it.groupLeader }) return results
    val clonesByPkg = results.filter { it.groupKey != null && !it.groupLeader }.groupBy { it.groupKey!! }
    val emitted = HashSet<String>()
    val grouped = ArrayList<RankedHit>(results.size)
    for (result in results) {
        if (result.groupKey != null && !result.groupLeader) continue
        grouped.add(result)
        val key = result.groupKey
        if (key != null && emitted.add(key)) {
            clonesByPkg[key]?.let(grouped::addAll)
        }
    }
    for (result in results) {
        val key = result.groupKey
        if (key != null && !result.groupLeader && emitted.add(key)) {
            grouped.addAll(clonesByPkg[key].orEmpty())
        }
    }
    return grouped
}

/**
 * 排序单元：结果 + 档位 + 紧密度 + 使用分；排序串在构造时取一次，避免比较器反复调 getter。
 * [groupKey] / [groupLeader] 仅由应用匹配（matchApps）设置：分身与主应用共用包名键，
 * 展示前由 [groupCloneApps] 把分身拉到主应用后面；其余来源恒为默认值。
 */
data class RankedHit(
    val result: SearchResult,
    override val tier: Int,
    val span: Int,
    override val offset: Int,
    override val usage: Double,
    override val sortTitle: String = result.title,
    override val sortTitleLen: Int = sortTitle.length,
    val groupKey: String? = null,
    val groupLeader: Boolean = false,
) : RankedResult

/**
 * 有界取优：未 Pin 的命中按统一排序保留前 [limit] 条，Pin 命中旁路全留。
 * 先用小顶堆筛，避免单字母查询把几千条命中全排一遍。
 * Pin 口径与展示层 [applyResultPins] 一致：都用 [QuickSearchSettings.pinnedKeyList]
 * 截断后的 key，且只对允许 Pin 的结果生效。
 */
fun capHits(
    hits: List<RankedHit>,
    limit: Int,
    pinnedKeys: Set<String>,
): List<RankedHit> {
    if (hits.isEmpty()) return hits
    if (pinnedKeys.isEmpty() && hits.size <= limit) return hits

    fun isPinned(hit: RankedHit) =
        hit.result.isResultOverrideAllowed() && hit.result.key in pinnedKeys

    val pinned = if (pinnedKeys.isEmpty()) emptyList() else hits.filter(::isPinned)
    val keepLimit = limit.coerceAtLeast(0)
    val unpinned = if (pinnedKeys.isEmpty()) hits else hits.filterNot(::isPinned)
    val top = when {
        keepLimit == 0 -> emptyList()
        unpinned.size <= keepLimit -> unpinned
        else -> {
            val heap = PriorityQueue<RankedHit>(keepLimit + 1, RANK_COMPARATOR.reversed())
            for (hit in unpinned) {
                heap.add(hit)
                if (heap.size > keepLimit) heap.remove()
            }
            heap.toList()
        }
    }
    return if (pinned.isEmpty()) top else pinned + top
}

/**
 * 全源/定向共用的同构匹配骨架：逐项算命中档位，tier>=0 才收录。
 * [score] 决定匹配字段（多个字段用 [bestOf]），[usage] 提供使用分（默认 0），
 * [group] 给应用行的分身聚合键（其余来源用默认 null）。
 * plan 在调用方构造一次，任务内所有条目复用同一份 masks，不再逐条编译。
 */
inline fun <T> matchEach(
    items: Iterable<T>,
    plan: SearchQueryPlan,
    result: (T) -> SearchResult,
    usage: (T) -> Double = { 0.0 },
    group: (T) -> Pair<String, Boolean>? = { null },
    score: (T) -> TieredMatch,
): List<RankedHit> {
    val out = mutableListOf<RankedHit>()
    for (item in items) {
        val m = score(item)
        if (m.tier >= 0) {
            val (groupKey, groupLeader) = group(item) ?: (null to false)
            out.add(RankedHit(result(item), m.tier, m.span, m.offset, usage(item), groupKey = groupKey, groupLeader = groupLeader))
        }
    }
    return out
}

fun QuickSearchIndex.matchActions(
    plan: SearchQueryPlan,
): List<RankedHit> {
    return matchEach(actions, plan, result = { it.toResult() }) { tierOf(it.label, plan) }
}

fun QuickSearchIndex.matchLibrary(
    libraryEntries: List<ActionLibraryEntry>,
    plan: SearchQueryPlan,
): List<RankedHit> {
    val out = mutableListOf<RankedHit>()
    val source = hunoia.luno.search.SearchCapabilities.actionSearch
    for (entry in libraryEntries) {
        val fallback = source.definitionName(entry.typeId) ?: entry.typeId
        val m = bestOfFirst(tierOf(entry.name, plan)) { tierOf(fallback, plan) }
        if (m.tier >= 0) {
            out.add(
                RankedHit(
                    libraryResult(entry, fallback, source.resolveLibraryEntry(entry)?.icon),
                    m.tier, m.span, m.offset, 0.0,
                ),
            )
        }
    }
    return out
}

fun QuickSearchIndex.matchApps(
    plan: SearchQueryPlan,
    usageStats: AppUsageStats,
    now: Long,
): List<RankedHit> {
    val usageScores = UsageScores(usageStats.launchCount, usageStats.recentLaunchTime, now)
    val data = appData
    return matchEach(data.apps, plan, result = { app ->
        app.toResult(app.packageName in data.disabledPkgs)
    }, usage = { usageScores.score(it.packageName) }, group = { app ->
        // 分身聚合：主应用与分身共用包名键，主应用是 leader（见 [groupCloneApps]）。
        app.packageName to !app.isClone
    }) { app ->
        // 包名也参与匹配：行副标题本来就显示包名，搜 com.tencent 应该有结果。
        bestOfFirst(tierOf(app.label, plan)) { tierOf(app.packageName, plan) }
    }
}

fun QuickSearchIndex.matchShortcuts(
    plan: SearchQueryPlan,
    pkgScore: Map<String, Double>,
): List<RankedHit> {
    val data = appData
    return matchEach(data.shortcuts, plan, result = { it.toResult() }, usage = { pkgScore[it.packageName] ?: 0.0 }) { sc ->
        // 包名也参与匹配：与 matchApps 同构，行副标题显示包名，搜 com.tencent 应有结果。
        bestOfFirst(tierOf(sc.label, plan)) { tierOf(sc.packageName, plan) }
    }
}

fun QuickSearchIndex.matchContacts(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchContacts || !contactsReady()) return emptyList()
    return matchEach(contacts, plan, result = { it.toResult() }) { c ->
        bestOfFirst(tierOf(c.name, plan)) { tierOf(c.number, plan) }
    }
}

fun QuickSearchIndex.matchTracks(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchMusic ||
        !mediaPermissionCached(android.Manifest.permission.READ_MEDIA_AUDIO)
    ) {
        return emptyList()
    }
    return matchEach(tracks, plan, result = { it.toResult() }) { t ->
        bestOfFirst(tierOf(t.title, plan)) { tierOf(t.artist, plan) }
    }
}

fun QuickSearchIndex.matchGallery(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchFiles) return emptyList()
    val galleryOk = mediaPermissionCached(android.Manifest.permission.READ_MEDIA_IMAGES) ||
        mediaPermissionCached(android.Manifest.permission.READ_MEDIA_VIDEO)
    if (!galleryOk) return emptyList()
    return matchEach(mediaFiles, plan, result = { it.toResult() }) { tierOf(it.name, plan) }
}

fun QuickSearchIndex.matchDocs(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchFiles || !permSnapshot.allFiles) return emptyList()
    return matchEach(docs, plan, result = { it.toResult() }) { tierOf(it.name, plan) }
}

fun QuickSearchIndex.matchCalendar(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchCalendar || !calendarReady()) return emptyList()
    return matchEach(events, plan, result = { it.toResult() }) { e ->
        bestOfFirst(tierOf(e.title, plan)) { tierOf(e.location, plan) }
    }
}

fun QuickSearchIndex.matchSms(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchSms || !smsReady()) return emptyList()
    return matchEach(smsList, plan, result = { it.toResult() }) { m ->
                bestOfFirst(tierOf(m.address, plan)) { tierOf(m.body, plan) }
    }
}

fun QuickSearchIndex.matchCalls(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
): List<RankedHit> {
    if (!searchSettings.searchCalls || !callsReady()) return emptyList()
    return matchEach(callList, plan, result = { it.toResult() }) { c ->
        bestOfFirst(tierOf(c.name, plan)) { tierOf(c.number, plan) }
    }
}

fun QuickSearchIndex.matchClips(
    plan: SearchQueryPlan,
    searchSettings: QuickSearchSettings,
    clips: ClipPools,
): List<RankedHit> {
    if (!searchSettings.searchClipboard) return emptyList()
    return matchClipPool(clips.pinned, clips.history, plan)
}

/** 剪贴匹配核心：不依赖索引实例（池=固定+历史），供 matchClips 与单测共用。 */
fun matchClipPool(
    pinned: List<String>,
    history: List<String>,
    tokens: List<String>,
    foldedTokens: List<String>,
): List<RankedHit> = matchClipPool(pinned, history, AppSearch.queryPlan(tokens, foldedTokens))

fun matchClipPool(
    pinned: List<String>,
    history: List<String>,
    plan: SearchQueryPlan,
): List<RankedHit> {
    if (pinned.isEmpty() && history.isEmpty()) return emptyList()
    // 固定源内置顶：usage=1.0 > 历史的 0.0，同 tier/offset 下固定排前。
    val pinnedHits = matchEach(pinned, plan, result = { clipResult(it) }, usage = { 1.0 }) { clip ->
        tierOf(clip.take(64), plan)
    }
    val historyHits = matchEach(history, plan, result = { clipResult(it) }) { clip ->
        tierOf(clip.take(64), plan)
    }
    // 空白剪贴命不中（空文本 vs 非空 token 必 NO_MATCH），无需额外过滤。
    val hits = pinnedHits + historyHits
    if (hits.isEmpty()) return emptyList()
    return hits.sortedWith(RANK_COMPARATOR).take(10)
}

/** 同一套排序：连续块语义下 tier → offset → 使用分 → 长度 → 字典序。
 *  不同类型只比匹配度，稀疏子序列不出局外无命中。 */
private fun QuickSearchIndex.sortHits(results: List<RankedHit>): List<RankedHit> =
    results.sortedWith(RANK_COMPARATOR)

/**
 * 标识指令解析（bb 新闻 / sms 138 / 裸 sms）：命中返回纯目标结果，无指令返回 null 走全源。
 * 大小写不敏感。三种出口：引擎标识 → 只回引擎行；来源标识+内容 → 只在该源内搜；
 * 裸标识（无空格或内容空）→ 列出该类近项。开关关闭/无数据 → null 回落全源。
 * 命中与否由 [search] 以 SearchOutcome.directive 回报，面板不再单独判一次。
 */
private fun QuickSearchIndex.parseDirective(
        trimmed: String,
        libraryEntries: List<ActionLibraryEntry>,
        usageStats: AppUsageStats,
        searchSettings: QuickSearchSettings,
        engines: List<SearchEngine>,
        clips: ClipPools,
    ): List<SearchResult>? {
        if (trimmed.isEmpty()) return null
        val hasSpace = trimmed.contains(' ')
        val head = if (hasSpace) trimmed.substringBefore(' ') else trimmed
        val rest = if (hasSpace) trimmed.substringAfter(' ').trim() else ""
        val headLower = head.lowercase()
        // 引擎标识 + 内容：只回引擎行（纯目标结果，不跑全源）。
        if (hasSpace && rest.isNotEmpty()) {
            directedEngine(trimmed, engines)?.let { return listOf(it) }
        }
        // 来源标识：开关关了、标识被清空（关闭）都回落全源。
        val source = SearchSourceRegistry.byTrigger(headLower, searchSettings)?.key ?: return null
        if (!SearchSourceRegistry.enabled(source, searchSettings)) return null
        val context = contextOf(usageStats, searchSettings, clips = clips)
        if (rest.isEmpty()) return SearchSourceRegistry.bare(source, context)
        return searchInSource(source, rest, libraryEntries, usageStats, searchSettings, clips)
    }

    private fun QuickSearchIndex.contextOf(
        usageStats: AppUsageStats,
        searchSettings: QuickSearchSettings,
        raw: String = "",
        clips: ClipPools = ClipPools(),
    ): SearchContext = SearchContext(
        index = this,
        settings = searchSettings,
        libraryEntries = emptyList(),
        plan = AppSearch.queryPlan(
            tokens = tokenize(raw),
            foldedTokens = tokenize(AppSearch.foldPinyin(raw)),
        ),
        now = System.currentTimeMillis(),
        usage = usageStats,
        clips = clips,
    )

    /** 定向搜：只在该源内匹配内容，排序与全源同一套。 */
private fun QuickSearchIndex.searchInSource(
        source: String,
        rest: String,
        libraryEntries: List<ActionLibraryEntry>,
        usageStats: AppUsageStats,
        searchSettings: QuickSearchSettings,
        clips: ClipPools,
    ): List<SearchResult> {
        val tokens = tokenize(rest)
        if (tokens.isEmpty()) return SearchSourceRegistry.bare(source, contextOf(usageStats, searchSettings, clips = clips)) ?: emptyList()
        val context = contextOf(usageStats, searchSettings, raw = rest, clips = clips).copy(
            libraryEntries = libraryEntries,
        )
        // 开关关了但标识还在（极少）：注册表已 gate，空结果直接回（不回落全源，
        // 用户明确点了类，结果为空就是空）。定向搜索也套用与全源相同的单源上限。
        return sortHits(capHits(SearchSourceRegistry.match(source, context), MAX_HITS_PER_SOURCE, emptySet())).map { it.result }
    }

    /** 裸标识：列出该类近项（cap 20，应用走常用 8）。无数据回 null → 全源。 */
fun QuickSearchIndex.bareSource(
        source: String,
        usageStats: AppUsageStats,
        searchSettings: QuickSearchSettings,
        clips: ClipPools,
    ): List<SearchResult>? {
        val out: List<SearchResult> = when (source) {
            SRC_APP -> topUsed(usageStats)
            SRC_SHORTCUT -> appData.shortcuts.map { it.toResult() }.take(BARE_LIST_LIMIT)
            SRC_ACTION -> actions.take(BARE_LIST_LIMIT).map { it.toResult() }
            SRC_CONTACT -> if (contactsReady()) contacts.take(BARE_LIST_LIMIT).map { it.toResult() } else return null
            SRC_MUSIC -> if (mediaPermissionCached(android.Manifest.permission.READ_MEDIA_AUDIO)) {
                tracks.take(BARE_LIST_LIMIT).map { it.toResult() }
            } else {
                return null
            }
            SRC_FILE -> recentFiles(BARE_LIST_LIMIT).takeIf { it.isNotEmpty() } ?: return null
            SRC_CALENDAR -> {
                if (!calendarReady() || events.isEmpty()) return null
                val now = System.currentTimeMillis()
                val (upcoming, past) = events.partition { it.beginMillis >= now - 86400000L }
                (upcoming + past).take(BARE_LIST_LIMIT).map { it.toResult() }
            }
            SRC_SMS -> if (smsReady() && smsList.isNotEmpty()) smsList.take(BARE_LIST_LIMIT).map { it.toResult() } else return null
            SRC_CALL -> if (callsReady() && callList.isNotEmpty()) callList.take(BARE_LIST_LIMIT).map { it.toResult() } else return null
            SRC_CLIP -> {
                // 标识页：固定全列打头，历史去重补到上限。
                val pinned = clips.pinned.map { clipResult(it) }
                val rest = clips.history.filter { it !in clips.pinned }
                    .take((BARE_LIST_LIMIT - pinned.size).coerceAtLeast(0))
                    .map { clipResult(it) }
                pinned + rest
            }
            else -> return null
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** 快捷方式包分聚合：全源与定向共用，抽出来。 */
fun QuickSearchIndex.pkgScores(
        usageStats: AppUsageStats,
        now: Long,
    ): Map<String, Double> = UsageScores(
        usageStats.launchCount,
        usageStats.recentLaunchTime,
        now,
    ).pkgScores

    /**
     * 搜索引擎 Triple：tier -1 保证在同一排序管线里垫底。
     * 标识直达优先选引擎，否则默认引擎（选择优先，位置不特殊）。
     */
private fun QuickSearchIndex.engineTriple(
        query: String,
        engines: List<SearchEngine>,
    ): RankedHit? {
        val directed = directedEngine(query, engines)
        if (directed != null) return RankedHit(directed, -1, 0, 0, 0.0)
        val default = engines.firstOrNull() ?: return null
        return RankedHit(
            engineResultRow(
                title = context.getString(R.string.quick_search_engine_search, default.name),
                query = query,
                engine = default,
            ),
            -1,
            0,
            0,
            0.0,
        )
    }

    /**
     * 标识直达：“标识 + 空格 + 内容”（如 bing 新闻）用标识对应的引擎。
     * 大小写不敏感；无空格分隔、标识未知、有名无实（内容空）都返回 null 走默认兜底。
     */
private fun QuickSearchIndex.directedEngine(
        query: String,
        engines: List<SearchEngine>,
    ): SearchResult? {
        val rest = query.substringAfter(' ', "")
        if (rest.isBlank()) return null
        val head = query.substringBefore(' ')
        // 历史脏数据可能有多条同标识引擎：取最后一条（最新添加），与
        // withUniqueEngineTriggers() 的保留策略一致，避免旧引擎永远抢先。
        val target = engines
            .lastOrNull { it.trigger.isNotBlank() && it.trigger.equals(head, ignoreCase = true) }
            ?: return null
        return engineResultRow(
            title = context.getString(R.string.quick_search_engine_search, target.name),
            query = rest.trim(),
            engine = target,
        )
    }

    /**
     * 计算器：纯算式（数字加减乘除取余括号空格）即算即显。要求至少一个数字和一个
     * 运算符，裸数字不触发；解析失败、除零无穷都返回 null（无行）。
     */

private val calcChars = Regex("^[0-9+\\-*/().%\\s]+$")
private val DOMAIN_REGEX = Regex("^[^\\s]+\\.[^\\s]{2,}.*$")

private fun QuickSearchIndex.calculatorResult(query: String): SearchResult? {
        if (query.length < 3 || !calcChars.matches(query)) return null
        if (!query.any { it.isDigit() } || !query.any { it in "+-*/%" }) return null
        val value = runCatching { evalExpr(query) }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        val text = if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        return calcResultRow(query, text)
    }

    /** 递归下降四则运算：expr → term → factor（成员函数互递归，用小类装位置状态）。 */
private fun QuickSearchIndex.evalExpr(s: String): Double = ExprParser(s.filterNot { it.isWhitespace() }).parse()

private class ExprParser(val t: String) {
    var pos = 0

    fun parse(): Double {
        val v = parseExpr()
        require(pos == t.length)
        return v
    }

    fun parseExpr(): Double {
        var v = parseTerm()
        while (pos < t.length) {
            val c = t[pos]
            if (c != '+' && c != '-') break
            pos++
            val r = parseTerm()
            v = if (c == '+') v + r else v - r
        }
        return v
    }

    fun parseTerm(): Double {
        var v = parseFactor()
        while (pos < t.length) {
            val c = t[pos]
            if (c != '*' && c != '/' && c != '%') break
            pos++
            val r = parseFactor()
            v = when (c) {
                '*' -> v * r
                '/' -> v / r
                else -> v % r
            }
        }
        return v
    }

    fun parseFactor(): Double {
        if (pos < t.length && (t[pos] == '+' || t[pos] == '-')) {
            val neg = t[pos] == '-'
            pos++
            val v = parseFactor()
            return if (neg) -v else v
        }
        if (pos < t.length && t[pos] == '(') {
            pos++
            val v = parseExpr()
            require(pos < t.length && t[pos] == ')')
            pos++
            return v
        }
        val start = pos
        while (pos < t.length && (t[pos].isDigit() || t[pos] == '.')) pos++
        require(start < pos)
        return t.substring(start, pos).toDouble()
    }
}

private fun QuickSearchIndex.urlResult(query: String): SearchResult? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.contains("://")
        val looksLikeDomain = trimmed.contains('.') && !trimmed.contains(' ')
            && DOMAIN_REGEX.matches(trimmed)
        return if (hasScheme || looksLikeDomain) urlResultRow(trimmed) else null
    }
