package hunoia.luno.quicklaunch.query

import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.model.DefaultSourceTriggers
import hunoia.luno.config.model.QuickAppLauncherSettings
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
import hunoia.luno.config.model.defaultEngine
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.quicklaunch.query.AppSearch.key
import hunoia.luno.ui.component.resolvedIcon

/** 裸标识列出该类近项的上限（应用走常用 8，不用这个）。 */
private const val BARE_LIST_LIMIT = 20

    /** 把 query 拆成单字 token（只保留字母数字），复用 AppSearch 拼音引擎。 */
private fun QuickSearchIndex.tokenize(s: String): List<String> =
        s.lowercase().filter { it.isLetterOrDigit() }.map { it.toString() }

private fun QuickSearchIndex.tierOf(text: String, tokens: List<String>, foldedTokens: List<String>): TieredMatch =
        AppSearch.matchTextTier(text, tokens, foldedTokens)

    /** 取最优命中（tier 大者胜，再比 offset 小者胜；连续块 span 恒定不参比）。 */
private fun QuickSearchIndex.bestOf(a: TieredMatch, b: TieredMatch): TieredMatch =
        maxOf(a, b, compareBy({ it.tier }, { -it.offset }))

    /** 空输入时的常用：按衰减使用分取前 N 个（禁用应用一并返回，由调用方标注）。 */
fun QuickSearchIndex.topUsed(settings: QuickAppLauncherSettings, limit: Int = 8): List<QuickSearchResult.App> {
        ensureLoaded()
        if (!appsLoaded) refreshApps()
        val now = System.currentTimeMillis()
        return apps
            .map { app -> app to usageScore(app.key(), settings, now) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (app, _) -> QuickSearchResult.App(app, app.packageName in disabledPkgs) }
    }

    /** 与 AppSearch.sortApps 同款的使用频率分（对数压制 + 3 天半衰），key 与记录侧一致。 */
private fun QuickSearchIndex.usageScore(key: String, settings: QuickAppLauncherSettings, now: Long): Double {
        val count = settings.launchCount[key] ?: 0L
        val recent = settings.recentLaunchTime[key] ?: 0L
        if (count <= 0L || recent <= 0L) return 0.0
        return AppSearch.decayedUsage(count, (now - recent) / 86400000.0)
    }

fun QuickSearchIndex.search(
        query: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings = QuickAppLauncherSettings(),
        searchSettings: QuickSearchSettings = QuickSearchSettings(),
        torchOn: Boolean = false,
    ): List<QuickSearchResult> {
        ensureLoaded()
        if (!appsLoaded) refreshApps()
        this.torchOn = torchOn
        // 标识指令优先（bb 新闻 / sms 138 / 裸 sms）：命中只回目标类，
        // 纯目标结果（无引擎兜底、无计算器、无历史参展）。null = 无指令，走全源。
        parseDirective(query.trim(), libraryEntries, launcherSettings, searchSettings)?.let { return it }
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        // 模糊音输入：分词前先整体折叠（adjacency 丢了就没法折），降档用。
        val foldedTokens = tokenize(AppSearch.foldPinyin(query))
        val now = System.currentTimeMillis()
        // 快捷方式按所属包聚合该包的最大分，一次算好供 O(1) 查询。
        val pkgScore = pkgScores(launcherSettings, now)

        val results = mutableListOf<RankedHit>()
        results.addAll(matchActions(tokens, foldedTokens))
        results.addAll(matchLibrary(libraryEntries, tokens, foldedTokens))
        results.addAll(matchApps(tokens, foldedTokens, launcherSettings, now))
        results.addAll(matchShortcuts(tokens, foldedTokens, pkgScore))
        // 联系人：姓名拼音免费（matchTextTier 内建），号码数字直匹配；只查内存。
        results.addAll(matchContacts(tokens, foldedTokens, searchSettings))
        // P3：音乐搜标题/艺术家，文件搜文件名；只查内存，无 usage；权限每次实时复查
        // （设置里关掉权限后 stale 缓存不再参展）。
        results.addAll(matchMedia(tokens, foldedTokens, searchSettings))
        // 快捷开关：标题 + 关键词一起匹配（如 wifi/蓝牙/手电），设置页同权重。
        results.addAll(matchToggles(tokens, foldedTokens, searchSettings))
        // 日历：标题/地点；只查内存。
        results.addAll(matchCalendar(tokens, foldedTokens, searchSettings))
        // 短信：发件人/正文；只查内存。
        results.addAll(matchSms(tokens, foldedTokens, searchSettings))
        // 通话记录：姓名/号码；只查内存。
        results.addAll(matchCalls(tokens, foldedTokens, searchSettings))
        // 剪贴记录：只看前 64 字（长文本逐字拼音每敲一字算一次，截断控成本）；
        // 池=clipHistory（聚焦读到的新文本已记入），命中上限 10 条。
        results.addAll(matchClips(tokens, foldedTokens, searchSettings))
        results.addAll(matchSettingsPages(tokens, foldedTokens))

        val urlResult = urlResult(query)
        if (urlResult != null) results.add(RankedHit(urlResult, 3, 0, 0, 0.0))

        // 搜索引擎兜底：tier -1 进同一管线自然垫底（不再追加特例）。
        // 只有非 URL 关键词才有；标识直达优先选引擎（如 bb 新闻），否则默认引擎。
        if (urlResult == null) {
            results.add(engineTriple(query.trim(), searchSettings))
        }

        val sorted = sortTriples(results)

        // 计算器排表头（精确意图，回车直达）。
        // 引擎兜底已由 engineTriple 进管线自然垫底，这里不再二次计算（之前重复算过一次）。
        val calc = calculatorResult(query.trim())
        return if (calc != null) listOf<QuickSearchResult>(calc) + sorted else sorted
    }

    /** 排序单元：结果 + 档位 + 紧密度 + 使用分。 */
private data class RankedHit(
    val result: QuickSearchResult,
    val tier: Int,
    val span: Int,
    val offset: Int,
    val usage: Double,
)

    /** 同一套排序：连续块语义下 tier → offset → 使用分 → 长度 → 字典序。
     * 类型不再分档（typerank 已删）：不同类型只比匹配度；稀疏子序列不出局外无命中。 */
private fun QuickSearchIndex.sortTriples(results: List<RankedHit>): List<QuickSearchResult> =
        results.sortedWith(
            compareByDescending<RankedHit> { it.tier }
                .thenBy { it.offset }
                .thenByDescending { it.usage }
                .thenBy { it.result.title.length }
                .thenBy { it.result.title },
        ).map { it.result }

private fun QuickSearchIndex.matchActions(
        tokens: List<String>,
        foldedTokens: List<String>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (a in actions) {
            val m = tierOf(a.title, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(a, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchLibrary(
        libraryEntries: List<NewActionLibraryEntry>,
        tokens: List<String>,
        foldedTokens: List<String>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (entry in libraryEntries) {
            val def = ActionDefinitions.byTypeId(entry.typeId)
            val fallback = def?.name ?: entry.typeId
            val m = bestOf(tierOf(entry.name, tokens, foldedTokens), tierOf(fallback, tokens, foldedTokens))
            if (m.tier >= 0) {
                out.add(
                    RankedHit(
                        QuickSearchResult.LibraryEntry(entry, fallback, entry.resolvedIcon()),
                        m.tier,
                        m.span,
                        m.offset,
                        0.0,
                    ),
                )
            }
        }
        return out
    }

private fun QuickSearchIndex.matchApps(
        tokens: List<String>,
        foldedTokens: List<String>,
        launcherSettings: QuickAppLauncherSettings,
        now: Long,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (app in apps) {
            // 包名也参与匹配：行副标题本来就显示包名，搜 com.tencent 应该有结果。
            val m = bestOf(tierOf(app.label, tokens, foldedTokens), tierOf(app.packageName, tokens, foldedTokens))
            if (m.tier >= 0) {
                out.add(
                    RankedHit(
                        QuickSearchResult.App(app, app.packageName in disabledPkgs),
                        m.tier,
                        m.span,
                        m.offset,
                        usageScore(app.key(), launcherSettings, now),
                    ),
                )
            }
        }
        return out
    }

private fun QuickSearchIndex.matchShortcuts(
        tokens: List<String>,
        foldedTokens: List<String>,
        pkgScore: Map<String, Double>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (sc in shortcuts) {
            // 包名也参与匹配：与 matchApps 同构，行副标题显示包名，搜 com.tencent 应有结果。
            val m = bestOf(tierOf(sc.label, tokens, foldedTokens), tierOf(sc.packageName, tokens, foldedTokens))
            if (m.tier >= 0) {
                out.add(RankedHit(QuickSearchResult.Shortcut(sc), m.tier, m.span, m.offset, pkgScore[sc.packageName] ?: 0.0))
            }
        }
        return out
    }

private fun QuickSearchIndex.matchContacts(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchContacts || !contactsReady()) return out
        for (c in contacts) {
            val m = bestOf(tierOf(c.name, tokens, foldedTokens), tierOf(c.number, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(c, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchMedia(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        if (!mediaReady()) return emptyList()
        return matchTracks(tokens, foldedTokens, searchSettings) +
            matchGallery(tokens, foldedTokens, searchSettings) +
            matchDocs(tokens, foldedTokens, searchSettings)
    }

private fun QuickSearchIndex.matchTracks(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchMusic ||
            !hasMediaPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
        ) {
            return out
        }
        for (t in tracks) {
            val m = bestOf(tierOf(t.title, tokens, foldedTokens), tierOf(t.artist, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(t, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchGallery(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchFiles) return out
        val galleryOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ||
            hasMediaPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
        if (!galleryOk) return out
        for (f in mediaFiles) {
            val m = tierOf(f.name, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(f, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchDocs(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchFiles || !hasAllFilesAccess()) return out
        for (d in docs) {
            val m = tierOf(d.name, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(d, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchToggles(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchToggles) return out
        for (t in toggleRows()) {
            val m = bestOf(tierOf(t.title, tokens, foldedTokens), tierOf(t.keywords, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(t, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchCalendar(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchCalendar || !calendarReady()) return out
        for (e in events) {
            val m = bestOf(tierOf(e.title_, tokens, foldedTokens), tierOf(e.location, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(e, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchSms(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchSms || !smsReady()) return out
        for (m in smsList) {
            val t = bestOf(tierOf(m.address, tokens, foldedTokens), tierOf(m.body, tokens, foldedTokens))
            if (t.tier >= 0) out.add(RankedHit(m, t.tier, t.span, t.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchCalls(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchCalls || !callsReady()) return out
        for (c in callList) {
            val m = bestOf(tierOf(c.name, tokens, foldedTokens), tierOf(c.number, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(c, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

private fun QuickSearchIndex.matchClips(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchClipboard) return out
        if (searchSettings.pinnedClips.isEmpty() && searchSettings.clipHistory.isEmpty()) return out
        val hits = mutableListOf<RankedHit>()
        // 固定源内置顶：usage=1.0 > 历史的 0.0，同 tier/offset 下固定排前。
        for (clip in searchSettings.pinnedClips) {
            if (clip.isBlank()) continue
            val head = if (clip.length > 64) clip.take(64) else clip
            val m = tierOf(head, tokens, foldedTokens)
            if (m.tier >= 0) hits.add(RankedHit(QuickSearchResult.ClipEntry(clip), m.tier, m.span, m.offset, 1.0))
        }
        for (clip in searchSettings.clipHistory) {
            if (clip.isBlank()) continue
            val head = if (clip.length > 64) clip.take(64) else clip
            val m = tierOf(head, tokens, foldedTokens)
            if (m.tier >= 0) hits.add(RankedHit(QuickSearchResult.ClipEntry(clip), m.tier, m.span, m.offset, 0.0))
        }
        if (hits.isEmpty()) return out
        hits.sortWith(
            compareByDescending<RankedHit> { it.tier }
                .thenBy { it.offset }
                .thenByDescending { it.usage }
                .thenBy { it.result.title.length }
                .thenBy { it.result.title },
        )
        return hits.take(10)
    }

private fun QuickSearchIndex.matchSettingsPages(
        tokens: List<String>,
        foldedTokens: List<String>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (sp in settingsPages) {
            val m = tierOf(sp.title, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(sp, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    /** 面板用：该 query 是否命中标识指令（命中则历史匹配不参展，保持纯目标结果）。 */
fun QuickSearchIndex.isDirective(
        query: String,
        settings: QuickSearchSettings,
        launcherSettings: QuickAppLauncherSettings = QuickAppLauncherSettings(),
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val hasSpace = trimmed.contains(' ')
        val head = (if (hasSpace) trimmed.substringBefore(' ') else trimmed).lowercase()
        if (hasSpace && trimmed.substringAfter(' ').isNotBlank()) {
            if (directedEngine(trimmed, settings) != null) return true
        }
        val source = sourceKeyFor(head, settings) ?: return false
        if (!sourceEnabled(source, settings)) return false
        // 裸标识无数据会回落全源（parseDirective 同逻辑），此时历史照常参展，前后一致。
        if (!hasSpace || trimmed.substringAfter(' ').isBlank()) {
            return bareSource(source, launcherSettings, settings) != null
        }
        return true
    }

    /**
     * 标识指令解析（bb 新闻 / sms 138 / 裸 sms）：命中返回纯目标结果，
     * 无指令返回 null 走全源。大小写不敏感；标识未知、无内容、有名无实（裸）按规则办。
     *
     * 三种出口：引擎标识 → 只回引擎行；来源标识+内容 → 只在该源内搜；
     * 裸标识（无空格或内容空）→ 列出该类近项。开关关闭/无数据 → null 回落全源。
     */
private fun QuickSearchIndex.parseDirective(
        trimmed: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings,
        searchSettings: QuickSearchSettings,
    ): List<QuickSearchResult>? {
        if (trimmed.isEmpty()) return null
        val hasSpace = trimmed.contains(' ')
        val head = if (hasSpace) trimmed.substringBefore(' ') else trimmed
        val rest = if (hasSpace) trimmed.substringAfter(' ').trim() else ""
        val headLower = head.lowercase()
        // 引擎标识 + 内容：只回引擎行（纯目标结果，不跑全源）。
        if (hasSpace && rest.isNotEmpty()) {
            directedEngine(trimmed, searchSettings)?.let { return listOf(it) }
        }
        // 来源标识：开关关了、标识被清空（关闭）都回落全源。
        val source = sourceKeyFor(headLower, searchSettings) ?: return null
        if (!sourceEnabled(source, searchSettings)) return null
        if (rest.isEmpty()) return bareSource(source, launcherSettings, searchSettings)
        return searchInSource(source, rest, libraryEntries, launcherSettings, searchSettings)
    }

    /** 标识头 → 来源 key：自定义优先、默认补齐，大小写不敏感，空串=关闭跳过。 */
private fun QuickSearchIndex.sourceKeyFor(headLower: String, settings: QuickSearchSettings): String? {
        if (headLower.isEmpty()) return null
        for ((k, v) in DefaultSourceTriggers) {
            val t = settings.effectiveTrigger(k).trim().lowercase()
            if (t.isNotEmpty() && t == headLower) return k
        }
        return null
    }

    /** 来源总开关：应用/快捷方式/动作/设置页无开关，常开。 */
private fun QuickSearchIndex.sourceEnabled(source: String, settings: QuickSearchSettings): Boolean = when (source) {
        SRC_CONTACT -> settings.searchContacts
        SRC_MUSIC -> settings.searchMusic
        SRC_FILE -> settings.searchFiles
        SRC_CALENDAR -> settings.searchCalendar
        SRC_SMS -> settings.searchSms
        SRC_CALL -> settings.searchCalls
        SRC_TOGGLE -> settings.searchToggles
        SRC_CLIP -> settings.searchClipboard
        SRC_APP, SRC_SHORTCUT, SRC_ACTION, SRC_SETTINGS -> true
        else -> false
    }

    /** 定向搜：只在该源内匹配内容，排序与全源同一套。 */
private fun QuickSearchIndex.searchInSource(
        source: String,
        rest: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings,
        searchSettings: QuickSearchSettings,
    ): List<QuickSearchResult> {
        val tokens = tokenize(rest)
        if (tokens.isEmpty()) return bareSource(source, launcherSettings, searchSettings) ?: emptyList()
        val folded = tokenize(AppSearch.foldPinyin(rest))
        val now = System.currentTimeMillis()
        val triples = when (source) {
            SRC_APP -> matchApps(tokens, folded, launcherSettings, now)
            SRC_SHORTCUT -> matchShortcuts(tokens, folded, pkgScores(launcherSettings, now))
            SRC_ACTION -> matchActions(tokens, folded)
            SRC_SETTINGS -> matchSettingsPages(tokens, folded)
            SRC_CONTACT -> matchContacts(tokens, folded, searchSettings)
            SRC_MUSIC -> matchTracks(tokens, folded, searchSettings)
            SRC_FILE -> matchGallery(tokens, folded, searchSettings) +
                matchDocs(tokens, folded, searchSettings)
            SRC_CALENDAR -> matchCalendar(tokens, folded, searchSettings)
            SRC_SMS -> matchSms(tokens, folded, searchSettings)
            SRC_CALL -> matchCalls(tokens, folded, searchSettings)
            SRC_TOGGLE -> matchToggles(tokens, folded, searchSettings)
            SRC_CLIP -> matchClips(tokens, folded, searchSettings)
            else -> emptyList()
        }
        // 开关关了但标识还在（极少）：匹配函数内已 gate，空结果直接回（不回落全源，
        // 用户明确点了类，结果为空就是空）。
        return sortTriples(triples)
    }

    /** 裸标识：列出该类近项（cap 20，应用走常用 8）。无数据回 null → 全源。 */
private fun QuickSearchIndex.bareSource(
        source: String,
        launcherSettings: QuickAppLauncherSettings,
        searchSettings: QuickSearchSettings,
    ): List<QuickSearchResult>? {
        val out: List<QuickSearchResult> = when (source) {
            SRC_APP -> topUsed(launcherSettings)
            SRC_SHORTCUT -> shortcuts.map { QuickSearchResult.Shortcut(it) }.take(BARE_LIST_LIMIT)
            SRC_ACTION -> actions.take(BARE_LIST_LIMIT)
            SRC_SETTINGS -> settingsPages.take(BARE_LIST_LIMIT)
            SRC_CONTACT -> if (contactsReady()) contacts.take(BARE_LIST_LIMIT) else return null
            SRC_MUSIC -> if (hasMediaPermission(android.Manifest.permission.READ_MEDIA_AUDIO)) {
                tracks.take(BARE_LIST_LIMIT)
            } else {
                return null
            }
            SRC_FILE -> recentFiles(BARE_LIST_LIMIT).takeIf { it.isNotEmpty() } ?: return null
            SRC_CALENDAR -> {
                if (!calendarReady() || events.isEmpty()) return null
                val now = System.currentTimeMillis()
                val (upcoming, past) = events.partition { it.beginMillis >= now - 86400000L }
                (upcoming + past).take(BARE_LIST_LIMIT)
            }
            SRC_SMS -> if (smsReady() && smsList.isNotEmpty()) smsList.take(BARE_LIST_LIMIT) else return null
            SRC_CALL -> if (callsReady() && callList.isNotEmpty()) callList.take(BARE_LIST_LIMIT) else return null
            SRC_TOGGLE -> toggleRows()
            SRC_CLIP -> {
                // 标识页：固定全列打头，历史去重补到上限。
                val pinned = searchSettings.pinnedClips.map { QuickSearchResult.ClipEntry(it) }
                val rest = searchSettings.clipHistory.filter { it !in searchSettings.pinnedClips }
                    .take((BARE_LIST_LIMIT - pinned.size).coerceAtLeast(0))
                    .map { QuickSearchResult.ClipEntry(it) }
                pinned + rest
            }
            else -> return null
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** 快捷方式包分聚合：全源与定向共用，抽出来。 */
private fun QuickSearchIndex.pkgScores(
        launcherSettings: QuickAppLauncherSettings,
        now: Long,
    ): Map<String, Double> {
        val pkgScore = mutableMapOf<String, Double>()
        for ((key, count) in launcherSettings.launchCount) {
            val pkg = key.substringBefore('/')
            val recent = launcherSettings.recentLaunchTime[key] ?: 0L
            if (count <= 0L || recent <= 0L) continue
            val score = AppSearch.decayedUsage(count, (now - recent) / 86400000.0)
            if (score > (pkgScore[pkg] ?: 0.0)) pkgScore[pkg] = score
        }
        return pkgScore
    }

    /**
     * 搜索引擎 Triple：tier -1 保证在同一排序管线里垫底。
     * 标识直达优先选引擎，否则默认引擎（选择优先，位置不特殊）。
     */
private fun QuickSearchIndex.engineTriple(
        query: String,
        settings: QuickSearchSettings,
    ): RankedHit {
        val directed = directedEngine(query, settings)
        if (directed != null) return RankedHit(directed, -1, 0, 0, 0.0)
        val default = settings.defaultEngine()
        return RankedHit(
            QuickSearchResult.EngineSearch(
                query,
                context.getString(R.string.quick_search_engine_search, default.name),
                default.urlTemplate,
                default.name,
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
private fun QuickSearchIndex.directedEngine(query: String, settings: QuickSearchSettings): QuickSearchResult.EngineSearch? {
        val rest = query.substringAfter(' ', "")
        if (rest.isBlank()) return null
        val head = query.substringBefore(' ')
        val target = settings.effectiveEngines()
            .firstOrNull { it.trigger.isNotBlank() && it.trigger.equals(head, ignoreCase = true) }
            ?: return null
        return QuickSearchResult.EngineSearch(
            rest.trim(),
            context.getString(R.string.quick_search_engine_search, target.name),
            target.urlTemplate,
            target.name,
        )
    }

    /**
     * 计算器：纯算式（数字加减乘除取余括号空格）即算即显。要求至少一个数字和一个
     * 运算符，裸数字不触发；解析失败、除零无穷都返回 null（无行）。
     */

private val calcChars = Regex("^[0-9+\\-*/().%\\s]+$")

private fun QuickSearchIndex.calculatorResult(query: String): QuickSearchResult.Calc? {
        if (query.length < 3 || !calcChars.matches(query)) return null
        if (!query.any { it.isDigit() } || !query.any { it in "+-*/%" }) return null
        val value = runCatching { evalExpr(query) }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        val text = if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        return QuickSearchResult.Calc(query, text)
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

private fun QuickSearchIndex.urlResult(query: String): QuickSearchResult.Url? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.contains("://")
        val looksLikeDomain = trimmed.contains('.') && !trimmed.contains(' ')
            && trimmed.matches(Regex("^[^\\s]+\\.[^\\s]{2,}.*$"))
        return if (hasScheme || looksLikeDomain) QuickSearchResult.Url(trimmed) else null
    }
